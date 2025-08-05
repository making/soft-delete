package com.example.softdelete;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "maildev.port=0", "spring.http.client.factory=simple" })
@ActiveProfiles("sendgrid")
class SoftDeleteApplicationTests {

	static Playwright playwright;

	static Browser browser;

	BrowserContext context;

	Page page;

	RestClient restClient;

	@LocalServerPort
	int serverPort;

	int maildevPort;

	@BeforeAll
	static void before() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch();
	}

	@AfterAll
	static void after() {
		playwright.close();
	}

	@BeforeEach
	void setUp(@Autowired RestClient.Builder restClientBuilder, @Value("${maildev.port}") int maildevPort) {
		this.restClient = restClientBuilder.defaultStatusHandler(__ -> true, (req, res) -> {
		}).build();
		this.maildevPort = maildevPort;
		this.context = browser.newContext();
		this.context.setDefaultTimeout(3000);
		this.page = context.newPage();
	}

	@AfterEach
	void tearDown() {
		this.restClient.delete().uri("http://127.0.0.1:" + maildevPort + "/email/all").retrieve().toBodilessEntity();
		this.context.close();
	}

	private void login(String username, String primaryEmail) {
		assertThat(page.title()).isEqualTo("Login");
		page.locator("input[name=username]").fill(username);
		page.locator("button[type=submit]").press("Enter");
		ResponseEntity<JsonNode> emailsResponse = restClient.get()
			.uri("http://127.0.0.1:" + maildevPort + "/email")
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(emailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode emails = emailsResponse.getBody();
		assertThat(emails).isNotNull();
		assertThat(emails.size()).isEqualTo(1);
		JsonNode email = emails.get(0);
		assertThat(email.get("subject").asText()).startsWith("Your One Time Token");
		assertThat(email.get("to").get(0).get("address").asText()).startsWith(primaryEmail);
		assertThat(email.get("from").get(0).get("address").asText()).startsWith("noreply@example.com");
		assertThat(email.get("text").asText())
			.startsWith("Use the following link to sign in into the application:\nhttp://localhost:" + serverPort);
		String magicLink = email.get("text").asText().split(":\n")[1];
		page.navigate(magicLink);
		assertThat(page.title()).isEqualTo("One-Time Token Login");
		page.locator("button[type=submit]").press("Enter");
	}

	@Test
	void loginAsNonAdminUser() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with existing user
		login("janesminth", "jane.smith@example.org");

		// Verify successful login and account page display
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");
		assertThat(page.locator("div.account-field").count()).isEqualTo(3);
		assertThat(page.locator("div.account-field").nth(0).locator("div.field-value").textContent())
			.isEqualTo("janesminth");
		assertThat(page.locator("div.account-field").nth(1).locator("div.field-value").textContent())
			.isEqualTo("Jane Smith");
		Locator accountField2 = page.locator("div.account-field").nth(2);
		assertThat(accountField2.locator("div.email-item").count()).isEqualTo(2);
		assertThat(accountField2.locator("div.email-item").nth(0).locator("span.email-address").textContent())
			.isEqualTo("jane.smith@example.org");
		assertThat(accountField2.locator("div.email-item").nth(0).locator("span.primary-badge").textContent())
			.isEqualTo("Primary");
		assertThat(accountField2.locator("div.email-item").nth(1).locator("span.email-address").textContent())
			.isEqualTo("jane@example.com");
		assertThat(accountField2.locator("div.email-item").nth(1).locator("span.primary-badge").count()).isEqualTo(0);

		// Verify Admin Dashboard button is NOT present for non-admin user
		assertThat(page.getByText("Admin Dashboard").count()).isEqualTo(0);
	}

	@Test
	void signupNewUserAndLogin() {
		// Navigate to signup page
		page.navigate("http://localhost:" + serverPort + "/signup");
		assertThat(page.title()).isEqualTo("Sign up");

		// Fill signup form
		String testUsername = "newuser" + System.currentTimeMillis();
		String testDisplayName = "New User";
		String testEmail = "newuser" + System.currentTimeMillis() + "@example.org";

		page.locator("#ott-username").fill(testUsername);
		page.locator("#ott-displayName").fill(testDisplayName);
		page.locator("#ott-email").fill(testEmail);

		// Submit signup form
		page.locator("button[type=submit]").press("Enter");

		// Verify redirect to success page
		assertThat(page.title()).isEqualTo("Account Registration");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Check your email to activate the account.");

		// Check activation email was sent
		ResponseEntity<JsonNode> emailsResponse = restClient.get()
			.uri("http://127.0.0.1:" + maildevPort + "/email")
			.retrieve()
			.toEntity(JsonNode.class);
		assertThat(emailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode emails = emailsResponse.getBody();
		assertThat(emails).isNotNull();
		assertThat(emails.size()).isEqualTo(1);

		JsonNode activationEmail = emails.get(0);
		assertThat(activationEmail.get("subject").asText()).isEqualTo("Activate your account");
		assertThat(activationEmail.get("to").get(0).get("address").asText()).isEqualTo(testEmail);
		assertThat(activationEmail.get("from").get(0).get("address").asText()).isEqualTo("noreply@example.com");

		// Extract activation link and activate account
		String emailText = activationEmail.get("text").asText();
		assertThat(emailText).contains("http://localhost:" + serverPort + "/activation");
		String activationLink = emailText.split("Please activate your account by clicking the following link:\n")[1]
			.split("\n\n")[0];
		page.navigate(activationLink);

		// Verify successful activation
		assertThat(page.title()).isEqualTo("Successfully activated!");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Successfully activated!");
		// Clear emails for login test
		restClient.delete().uri("http://127.0.0.1:" + maildevPort + "/email/all").retrieve().toBodilessEntity();

		// Now try to login with the newly created user
		page.navigate("http://localhost:" + serverPort + "/login");
		login(testUsername, testEmail);

		// Verify successful login and account page display
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");
		assertThat(page.locator("div.account-field").count()).isEqualTo(3);
		assertThat(page.locator("div.account-field").nth(0).locator("div.field-value").textContent())
			.isEqualTo(testUsername);
		assertThat(page.locator("div.account-field").nth(1).locator("div.field-value").textContent())
			.isEqualTo(testDisplayName);
		Locator emailField = page.locator("div.account-field").nth(2);
		assertThat(emailField.locator("div.email-item").count()).isEqualTo(1);
		assertThat(emailField.locator("div.email-item").nth(0).locator("span.email-address").textContent())
			.isEqualTo(testEmail);
		assertThat(emailField.locator("div.email-item").nth(0).locator("span.primary-badge").textContent())
			.isEqualTo("Primary");

		// Delete the newly created account to clean up for other tests
		page.getByText("Delete Account").click();
		assertThat(page.title()).isEqualTo("Delete Account");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Delete Account");
		assertThat(page.locator("p.message").first().textContent())
			.isEqualTo("Are you sure you want to delete your account?");
		page.getByText("Yes, delete my account").click();
		assertThat(page.title()).isEqualTo("Account Deleted");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Account Deleted");
		assertThat(page.locator("p.message").first().textContent())
			.isEqualTo("Your account has been successfully deleted.");
	}

	@Test
	void loginAsAdminUserAndViewDashboard() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with admin user
		login("johndoe", "john.doe.work@example.org");

		// Verify successful login and account page display
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");
		assertThat(page.locator("div.account-field").count()).isEqualTo(3);
		assertThat(page.locator("div.account-field").nth(0).locator("div.field-value").textContent())
			.isEqualTo("johndoe");
		assertThat(page.locator("div.account-field").nth(1).locator("div.field-value").textContent())
			.isEqualTo("John Doe");
		Locator accountField2 = page.locator("div.account-field").nth(2);
		assertThat(accountField2.locator("div.email-item").count()).isEqualTo(3);
		assertThat(accountField2.locator("div.email-item").nth(0).locator("span.email-address").textContent())
			.isEqualTo("john.doe.work@example.org");
		assertThat(accountField2.locator("div.email-item").nth(0).locator("span.primary-badge").textContent())
			.isEqualTo("Primary");
		assertThat(accountField2.locator("div.email-item").nth(1).locator("span.email-address").textContent())
			.isEqualTo("john.doe@example.com");
		assertThat(accountField2.locator("div.email-item").nth(1).locator("span.primary-badge").count()).isEqualTo(0);
		assertThat(accountField2.locator("div.email-item").nth(2).locator("span.email-address").textContent())
			.isEqualTo("j.doe@example.net");
		assertThat(accountField2.locator("div.email-item").nth(2).locator("span.primary-badge").count()).isEqualTo(0);

		// Verify Admin Dashboard button is present for admin user
		assertThat(page.getByText("Admin Dashboard").count()).isEqualTo(1);

		// Navigate to admin dashboard by clicking the button
		page.getByText("Admin Dashboard").click();
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Admin Dashboard");

		// Verify tab navigation is present
		assertThat(page.locator(".tab-navigation").count()).isEqualTo(1);
		assertThat(page.locator(".tab-link").count()).isEqualTo(3);
		assertThat(page.locator(".tab-link").nth(0).textContent().trim()).isEqualTo("Active Users");
		assertThat(page.locator(".tab-link").nth(1).textContent().trim()).isEqualTo("Pending Users");
		assertThat(page.locator(".tab-link").nth(2).textContent().trim()).isEqualTo("Deleted Users");

		// Verify active tab is selected by default
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");

		// Verify active users tab content
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Active Users");
		assertThat(page.locator(".user-card").count()).isEqualTo(8); // Exactly 8 active
																		// users (1-8)

		// Verify detailed information for all active users (displayed in descending ID
		// order)
		// User 8: lisawhite (Non-Admin) - highest ID displayed first
		Locator user8Card = page.locator(".user-card").nth(0);
		assertThat(user8Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("8");
		assertThat(user8Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("lisawhite");
		assertThat(user8Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Lisa White");
		assertThat(user8Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(1);
		// Should have Promote to Admin button (not admin)
		assertThat(user8Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user8Card.getByText("Ban User").count()).isEqualTo(1);

		// User 7: tomtaylor (Non-Admin)
		Locator user7Card = page.locator(".user-card").nth(1);
		assertThat(user7Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("7");
		assertThat(user7Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("tomtaylor");
		assertThat(user7Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Tom Taylor");
		assertThat(user7Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(1);
		// Should have Promote to Admin button (not admin)
		assertThat(user7Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user7Card.getByText("Ban User").count()).isEqualTo(1);

		// User 6: emilydavis (Non-Admin)
		Locator user6Card = page.locator(".user-card").nth(2);
		assertThat(user6Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("6");
		assertThat(user6Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("emilydavis");
		assertThat(user6Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Emily Davis");
		assertThat(user6Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(1);
		// Should have Promote to Admin button (not admin)
		assertThat(user6Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user6Card.getByText("Ban User").count()).isEqualTo(1);

		// User 5: alexwilson (Non-Admin)
		Locator user5Card = page.locator(".user-card").nth(3);
		assertThat(user5Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("5");
		assertThat(user5Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("alexwilson");
		assertThat(user5Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Alex Wilson");
		assertThat(user5Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(2);
		// Should have Promote to Admin button (not admin)
		assertThat(user5Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user5Card.getByText("Ban User").count()).isEqualTo(1);

		// User 4: sarahjones (Non-Admin)
		Locator user4Card = page.locator(".user-card").nth(4);
		assertThat(user4Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("4");
		assertThat(user4Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("sarahjones");
		assertThat(user4Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Sarah Jones");
		assertThat(user4Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(1);
		// Should have Promote to Admin button (not admin)
		assertThat(user4Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user4Card.getByText("Ban User").count()).isEqualTo(1);

		// User 3: mikebrown (Admin)
		Locator user3Card = page.locator(".user-card").nth(5);
		assertThat(user3Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("3");
		assertThat(user3Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("mikebrown");
		assertThat(user3Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Mike Brown");
		assertThat(user3Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(2);
		// Should not have Promote to Admin button (already admin)
		assertThat(user3Card.getByText("Promote to Admin").count()).isEqualTo(0);
		assertThat(user3Card.getByText("Ban User").count()).isEqualTo(1);

		// User 2: janesminth (Non-Admin)
		Locator user2Card = page.locator(".user-card").nth(6);
		assertThat(user2Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("2");
		assertThat(user2Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("janesminth");
		assertThat(user2Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Jane Smith");
		assertThat(user2Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(2);
		// Should have Promote to Admin button (not admin)
		assertThat(user2Card.getByText("Promote to Admin").count()).isEqualTo(1);
		assertThat(user2Card.getByText("Ban User").count()).isEqualTo(1);

		// User 1: johndoe (Admin) - lowest ID displayed last
		Locator user1Card = page.locator(".user-card").nth(7);
		assertThat(user1Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("1");
		assertThat(user1Card.locator(".user-field").nth(1).locator(".field-value").textContent()).isEqualTo("johndoe");
		assertThat(user1Card.locator(".user-field").nth(2).locator(".field-value").textContent()).isEqualTo("John Doe");
		assertThat(user1Card.locator(".user-field").nth(3).locator(".email-item").count()).isEqualTo(3);
		// Should not have Promote to Admin button (already admin)
		assertThat(user1Card.getByText("Promote to Admin").count()).isEqualTo(0);
		assertThat(user1Card.getByText("Ban User").count()).isEqualTo(1);

		// Test navigation to pending users tab
		page.locator(".tab-link").nth(1).click();
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Pending Users");

		// Test navigation to deleted users tab
		page.locator(".tab-link").nth(2).click();
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Deleted Users");

		// Verify back to account button is present
		assertThat(page.getByText("Back to Account").count()).isEqualTo(1);
	}

}
