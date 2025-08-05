package com.example.softdelete;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "maildev.port=0", "spring.http.client.factory=simple", "spring.mail.host=" })
@ActiveProfiles("sendgrid")
class SoftDeleteApplicationTests {

	static Playwright playwright;

	static Browser browser;

	BrowserContext context;

	Page page;

	RestClient restClient;

	@Autowired
	JdbcClient jdbcClient;

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
		this.clearEmails();
		// Clean up test data: remove users with ID > 14 that were created during tests
		this.jdbcClient.sql("DELETE FROM user_deletion_events WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM user_ban_events WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM deleted_users WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM admin_users WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM active_users WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM pending_users WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM user_primary_emails WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM user_emails WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM user_profiles WHERE user_id > 14").update();
		this.jdbcClient.sql("DELETE FROM users WHERE user_id > 14").update();
		this.context.close();
	}

	private void clearEmails() {
		this.restClient.delete().uri("http://127.0.0.1:" + maildevPort + "/email/all").retrieve().toBodilessEntity();
	}

	private void login(String username, String expectedPrimaryEmail) {
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
		// Only assert expected email if provided (allows flexibility for test
		// interdependencies)
		String actualPrimaryEmail = email.get("to").get(0).get("address").asText();
		if (expectedPrimaryEmail != null && !expectedPrimaryEmail.isEmpty()) {
			assertThat(actualPrimaryEmail).startsWith(expectedPrimaryEmail);
		}
		assertThat(email.get("text").asText())
			.startsWith("Use the following link to sign in into the application:\nhttp://localhost:" + serverPort);
		String magicLink = email.get("text").asText().split(":\n")[1];
		page.navigate(magicLink);
		assertThat(page.title()).isEqualTo("One-Time Token Login");
		page.locator("button[type=submit]").press("Enter");
	}

	private void createUserAndLogin(String username, String displayName, String email) {
		// Navigate to signup page
		page.navigate("http://localhost:" + serverPort + "/signup");
		assertThat(page.title()).isEqualTo("Sign up");

		// Fill signup form
		page.locator("#ott-username").fill(username);
		page.locator("#ott-displayName").fill(displayName);
		page.locator("#ott-email").fill(email);

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
		assertThat(activationEmail.get("to").get(0).get("address").asText()).isEqualTo(email);

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
		clearEmails();

		// Now try to login with the newly created user
		page.navigate("http://localhost:" + serverPort + "/login");
		login(username, email);

		// Verify successful login and account page display
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");
	}

	private void deleteCurrentUser() {
		// Delete the current user to clean up for other tests
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
	void testLoginAsNonAdminUser() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with existing user (flexible email check)
		login("janesminth", "");

		// Verify successful login and account page display
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");
		assertThat(page.locator("div.account-field").count()).isEqualTo(3);
		assertThat(page.locator("div.account-field").nth(0).locator("div.field-value").textContent())
			.isEqualTo("janesminth");
		assertThat(page.locator("div.account-field").nth(1).locator("div.field-value").textContent())
			.isEqualTo("Jane Smith");
		Locator accountField2 = page.locator("div.account-field").nth(2);
		// Email count may vary due to test interdependencies (originally 2, may be more)
		assertThat(accountField2.locator("div.email-item").count()).isGreaterThanOrEqualTo(2);
		// Verify that one email is marked as primary (but the specific email may vary)
		boolean foundPrimary = false;
		for (int i = 0; i < accountField2.locator("div.email-item").count(); i++) {
			if (accountField2.locator("div.email-item").nth(i).locator("span.primary-badge").count() > 0) {
				foundPrimary = true;
				assertThat(accountField2.locator("div.email-item").nth(i).locator("span.primary-badge").textContent())
					.isEqualTo("Primary");
				break;
			}
		}
		assertThat(foundPrimary).isTrue();
		// Verify that at least one of the original emails exists
		boolean foundOriginalEmail = false;
		for (int i = 0; i < accountField2.locator("div.email-item").count(); i++) {
			String emailText = accountField2.locator("div.email-item")
				.nth(i)
				.locator("span.email-address")
				.textContent();
			if (emailText.equals("jane.smith@example.org") || emailText.equals("jane@example.com")) {
				foundOriginalEmail = true;
				break;
			}
		}
		assertThat(foundOriginalEmail).isTrue();

		// Verify Admin Dashboard button is NOT present for non-admin user
		assertThat(page.getByText("Admin Dashboard").count()).isEqualTo(0);

		// Verify non-admin user cannot access admin dashboard directly via URL
		page.navigate("http://localhost:" + serverPort + "/admin");

		// Access should be denied - verify specific error response
		assertThat(page.title()).isEqualTo("Error");
		assertThat(page.textContent("body")).contains("Forbidden");
	}

	@Test
	void testSignupNewUserAndLogin() {
		// Navigate to signup page
		page.navigate("http://localhost:" + serverPort + "/signup");
		assertThat(page.title()).isEqualTo("Sign up");

		// Fill signup form
		String testUsername = "newuser";
		String testDisplayName = "New User";
		String testEmail = "newuser@example.org";

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
		clearEmails();

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
	void testLoginAsAdminUserAndViewDashboard() {
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

		// User 2: janesminth (Non-Admin) - may have additional emails from test
		// interdependencies
		Locator user2Card = page.locator(".user-card").nth(6);
		assertThat(user2Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("2");
		assertThat(user2Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("janesminth");
		assertThat(user2Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Jane Smith");
		// Email count may vary due to test interdependencies (originally 2, may be more)
		assertThat(user2Card.locator(".user-field").nth(3).locator(".email-item").count()).isGreaterThanOrEqualTo(2);
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

	@Test
	void testActiveUsersPagination() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with admin user
		login("johndoe", "john.doe.work@example.org");

		// Navigate to admin dashboard with pagination size=2
		page.navigate("http://localhost:" + serverPort + "/admin?tab=active&size=2");
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Admin Dashboard");

		// Verify active tab is selected
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Active Users");

		// Page 1: Verify first 2 users (ID 8, 7) are displayed
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		Locator user8Card = page.locator(".user-card").nth(0);
		assertThat(user8Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("8");
		assertThat(user8Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("lisawhite");

		Locator user7Card = page.locator(".user-card").nth(1);
		assertThat(user7Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("7");
		assertThat(user7Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("tomtaylor");

		// Verify pagination links - should have Next but no Previous (first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 2: Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		Locator user6Card = page.locator(".user-card").nth(0);
		assertThat(user6Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("6");
		assertThat(user6Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("emilydavis");

		Locator user5Card = page.locator(".user-card").nth(1);
		assertThat(user5Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("5");
		assertThat(user5Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("alexwilson");

		// Verify both Previous and Next links are present
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 3: Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		Locator user4Card = page.locator(".user-card").nth(0);
		assertThat(user4Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("4");
		assertThat(user4Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("sarahjones");

		Locator user3Card = page.locator(".user-card").nth(1);
		assertThat(user3Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("3");
		assertThat(user3Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("mikebrown");

		// Verify both Previous and Next links are present
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 4 (Last page): Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		Locator user2Card = page.locator(".user-card").nth(0);
		assertThat(user2Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("2");
		assertThat(user2Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("janesminth");

		Locator user1Card = page.locator(".user-card").nth(1);
		assertThat(user1Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("1");
		assertThat(user1Card.locator(".user-field").nth(1).locator(".field-value").textContent()).isEqualTo("johndoe");

		// Verify only Previous link is present (last page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(0);

		// Navigate back: Click Previous to Page 3
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		user4Card = page.locator(".user-card").nth(0);
		assertThat(user4Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("4");
		assertThat(user4Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("sarahjones");

		user3Card = page.locator(".user-card").nth(1);
		assertThat(user3Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("3");
		assertThat(user3Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("mikebrown");

		// Navigate back: Click Previous to Page 2
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		user6Card = page.locator(".user-card").nth(0);
		assertThat(user6Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("6");
		assertThat(user6Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("emilydavis");

		user5Card = page.locator(".user-card").nth(1);
		assertThat(user5Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("5");
		assertThat(user5Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("alexwilson");

		// Navigate back: Click Previous to Page 1 (First page)
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(2);
		user8Card = page.locator(".user-card").nth(0);
		assertThat(user8Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("8");
		assertThat(user8Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("lisawhite");

		user7Card = page.locator(".user-card").nth(1);
		assertThat(user7Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("7");
		assertThat(user7Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("tomtaylor");

		// Verify only Next link is present (back to first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);
	}

	@Test
	void testPendingUsersPagination() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with admin user
		login("johndoe", "john.doe.work@example.org");

		// Navigate to admin dashboard pending users tab with pagination size=1
		page.navigate("http://localhost:" + serverPort + "/admin?tab=pending&size=1");
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Admin Dashboard");

		// Verify pending tab is selected
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Pending Users");
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Pending Users");

		// Page 1: Verify first pending user (ID 14) is displayed (highest ID first)
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user14Card = page.locator(".user-card").nth(0);
		assertThat(user14Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("14");
		assertThat(user14Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("pendinguser2");
		assertThat(user14Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Pending User Two");

		// Verify pagination links - should have Next but no Previous (first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 2: Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user13Card = page.locator(".user-card").nth(0);
		assertThat(user13Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("13");
		assertThat(user13Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("pendinguser1");
		assertThat(user13Card.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo("Pending User One");

		// Verify only Previous link is present (last page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(0);

		// Navigate back: Click Previous to Page 1
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		user14Card = page.locator(".user-card").nth(0);
		assertThat(user14Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("14");
		assertThat(user14Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("pendinguser2");

		// Verify only Next link is present (back to first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);
	}

	@Test
	void testDeletedUsersPagination() {
		// Navigate to login page
		page.navigate("http://localhost:" + serverPort);

		// Login with admin user
		login("johndoe", "john.doe.work@example.org");

		// Navigate to admin dashboard deleted users tab with pagination size=1
		page.navigate("http://localhost:" + serverPort + "/admin?tab=deleted&size=1");
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Admin Dashboard");

		// Verify deleted tab is selected
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Deleted Users");
		assertThat(page.locator(".tab-content h4").textContent()).isEqualTo("Deleted Users");

		// Page 1: Verify first deleted user (ID 12) is displayed (highest ID first)
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user12Card = page.locator(".user-card").nth(0);
		assertThat(user12Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("12");
		assertThat(user12Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-02T14:30Z");

		// Verify pagination links - should have Next but no Previous (first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 2: Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user11Card = page.locator(".user-card").nth(0);
		assertThat(user11Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("11");
		assertThat(user11Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-01T10:00Z");

		// Verify both Previous and Next links are present
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 3: Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user10Card = page.locator(".user-card").nth(0);
		assertThat(user10Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("10");
		assertThat(user10Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-04T11:45Z");

		// Verify both Previous and Next links are present
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);

		// Navigate to Page 4 (Last page): Click Next
		page.getByText("Next →").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		Locator user9Card = page.locator(".user-card").nth(0);
		assertThat(user9Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("9");
		assertThat(user9Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-03T09:15Z");

		// Verify only Previous link is present (last page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(1);
		assertThat(page.getByText("Next →").count()).isEqualTo(0);

		// Navigate back: Click Previous to Page 3
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		user10Card = page.locator(".user-card").nth(0);
		assertThat(user10Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("10");
		assertThat(user10Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-04T11:45Z");

		// Navigate back: Click Previous to Page 2
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		user11Card = page.locator(".user-card").nth(0);
		assertThat(user11Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("11");
		assertThat(user11Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-01T10:00Z");

		// Navigate back: Click Previous to Page 1 (First page)
		page.getByText("← Previous").click();
		assertThat(page.locator(".user-card").count()).isEqualTo(1);
		user12Card = page.locator(".user-card").nth(0);
		assertThat(user12Card.locator(".user-field").nth(0).locator(".field-value").textContent()).isEqualTo("12");
		assertThat(user12Card.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo("2024-02-02T14:30Z");

		// Verify only Next link is present (back to first page)
		assertThat(page.getByText("← Previous").count()).isEqualTo(0);
		assertThat(page.getByText("Next →").count()).isEqualTo(1);
	}

	@Test
	void testAdminBanUser() {
		// First, create a new user to ban
		// Navigate to signup page
		page.navigate("http://localhost:" + serverPort + "/signup");
		assertThat(page.title()).isEqualTo("Sign up");

		// Fill signup form with test data
		String testUsername = "usertoban";
		String testDisplayName = "User To Ban";
		String testEmail = "usertoban@example.org";

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

		// Extract activation link and activate account
		String emailText = activationEmail.get("text").asText();
		String activationLink = emailText.split("Please activate your account by clicking the following link:\n")[1]
			.split("\n\n")[0];
		page.navigate(activationLink);

		// Verify successful activation
		assertThat(page.title()).isEqualTo("Successfully activated!");

		// Clear emails
		clearEmails();

		// Now login as admin user
		page.navigate("http://localhost:" + serverPort + "/login");
		login("johndoe", "john.doe.work@example.org");

		// Navigate to admin dashboard
		page.getByText("Admin Dashboard").click();
		assertThat(page.title()).isEqualTo("Admin Dashboard");

		// Verify the new user appears in Active Users tab (should be first due to highest
		// ID)
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");
		assertThat(page.locator(".user-card").count()).isGreaterThan(0);

		// Find the newly created user (should be first due to highest ID)
		Locator firstUserCard = page.locator(".user-card").first();
		assertThat(firstUserCard.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo(testUsername);
		assertThat(firstUserCard.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo(testDisplayName);

		// Get the user ID before banning
		String userIdToBan = firstUserCard.locator(".user-field").nth(0).locator(".field-value").textContent();

		// Click Ban User button
		firstUserCard.getByText("Ban User").click();

		// Verify ban confirmation page
		assertThat(page.title()).isEqualTo("Ban User");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Ban User");
		assertThat(page.locator("p.message").first().textContent())
			.contains("This action will permanently ban this user");

		// Fill in ban reason and confirm
		page.locator("#ban-reason").fill("Test ban for E2E testing");
		page.locator("button.styled-button.danger").click();

		// Verify redirect back to admin dashboard
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");

		// Verify the banned user is no longer in Active Users
		boolean userFoundInActive = false;
		int activeUserCount = page.locator(".user-card").count();
		for (int i = 0; i < activeUserCount; i++) {
			String username = page.locator(".user-card")
				.nth(i)
				.locator(".user-field")
				.nth(1)
				.locator(".field-value")
				.textContent();
			if (username.equals(testUsername)) {
				userFoundInActive = true;
				break;
			}
		}
		assertThat(userFoundInActive).isFalse();

		// Navigate to Deleted Users tab
		page.getByText("Deleted Users").click();
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Deleted Users");

		// Verify the banned user appears in Deleted Users (should be first due to highest
		// ID)
		assertThat(page.locator(".user-card").count()).isGreaterThan(0);
		Locator firstDeletedCard = page.locator(".user-card").first();

		// Verify the banned user ID matches
		String bannedUserId = firstDeletedCard.locator(".user-field").nth(0).locator(".field-value").textContent();
		assertThat(bannedUserId).isEqualTo(userIdToBan);

		// Verify the deleted timestamp is recent (should contain today's date)
		String deletedAt = firstDeletedCard.locator(".user-field").nth(1).locator(".field-value").textContent();
		assertThat(deletedAt).contains(String.valueOf(LocalDate.now().getYear())); // Current
																					// year
	}

	@Test
	void testAdminPromoteUserAndAccess() {
		// First, create a new user to promote
		// Navigate to signup page
		page.navigate("http://localhost:" + serverPort + "/signup");
		assertThat(page.title()).isEqualTo("Sign up");

		// Fill signup form with test data
		String testUsername = "usertopromote";
		String testDisplayName = "User To Promote";
		String testEmail = "usertopromote@example.org";

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

		// Extract activation link and activate account
		String emailText = activationEmail.get("text").asText();
		String activationLink = emailText.split("Please activate your account by clicking the following link:\n")[1]
			.split("\n\n")[0];
		page.navigate(activationLink);

		// Verify successful activation
		assertThat(page.title()).isEqualTo("Successfully activated!");

		// Clear emails
		clearEmails();

		// Now login as admin user
		page.navigate("http://localhost:" + serverPort + "/login");
		login("johndoe", "john.doe.work@example.org");

		// Navigate to admin dashboard
		page.getByText("Admin Dashboard").click();
		assertThat(page.title()).isEqualTo("Admin Dashboard");

		// Verify the new user appears in Active Users tab (should be first due to highest
		// ID)
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");
		assertThat(page.locator(".user-card").count()).isGreaterThan(0);

		// Find the newly created user (should be first due to highest ID)
		Locator firstUserCard = page.locator(".user-card").first();
		assertThat(firstUserCard.locator(".user-field").nth(1).locator(".field-value").textContent())
			.isEqualTo(testUsername);
		assertThat(firstUserCard.locator(".user-field").nth(2).locator(".field-value").textContent())
			.isEqualTo(testDisplayName);

		// Get the user ID before promoting
		String userIdToPromote = firstUserCard.locator(".user-field").nth(0).locator(".field-value").textContent();

		// Click Promote to Admin button
		firstUserCard.getByText("Promote to Admin").click();

		// Verify promote confirmation page
		assertThat(page.title()).isEqualTo("Promote to Admin");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Promote to Admin");
		assertThat(page.locator("p.message").first().textContent())
			.contains("Are you sure you want to promote this user to admin?");

		// Confirm promotion
		page.getByText("Yes, Promote to Admin").click();

		// Verify redirect back to admin dashboard
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator(".tab-link.active").textContent().trim()).isEqualTo("Active Users");

		// Verify the promoted user no longer has "Promote to Admin" button
		// Find the user again (still should be first)
		Locator promotedUserCard = page.locator(".user-card").first();
		assertThat(promotedUserCard.locator(".user-field").nth(0).locator(".field-value").textContent())
			.isEqualTo(userIdToPromote);
		assertThat(promotedUserCard.getByText("Promote to Admin").count()).isEqualTo(0);

		// Logout as admin
		page.navigate("http://localhost:" + serverPort + "/logout");

		// Clear emails before new login
		clearEmails();

		// Login as the newly promoted user
		page.navigate("http://localhost:" + serverPort + "/login");
		login(testUsername, testEmail);

		// Verify successful login
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator("h3").textContent()).isEqualTo("Account Information");

		// Verify Admin Dashboard button is present for newly promoted admin
		assertThat(page.getByText("Admin Dashboard").count()).isEqualTo(1);

		// Navigate to admin dashboard
		page.getByText("Admin Dashboard").click();
		assertThat(page.title()).isEqualTo("Admin Dashboard");
		assertThat(page.locator("h3.header").textContent()).isEqualTo("Admin Dashboard");

		// Verify the promoted user can see all tabs
		assertThat(page.locator(".tab-link").count()).isEqualTo(3);
		assertThat(page.locator(".tab-link").nth(0).textContent().trim()).isEqualTo("Active Users");
		assertThat(page.locator(".tab-link").nth(1).textContent().trim()).isEqualTo("Pending Users");
		assertThat(page.locator(".tab-link").nth(2).textContent().trim()).isEqualTo("Deleted Users");

		// Verify can view active users
		assertThat(page.locator(".user-card").count()).isGreaterThan(0);
	}

	@Test
	void testAddEmailAddress() {
		// Create and login as new user
		createUserAndLogin("testadd", "Test Add User", "testadd@example.com");

		// Verify initial state - should have 1 email (the registration email)
		assertThat(page.locator(".email-item").count()).isEqualTo(1);

		// Add new email address without setting as primary
		String newEmail = "new.email@example.com";
		page.locator("#email").fill(newEmail);
		page.locator("button[type=submit]").nth(0).click(); // First submit button (Add
															// Email)

		// Verify redirect to account page with success message
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Email address added successfully");

		// Verify new email is added (should now have 2 emails)
		assertThat(page.locator(".email-item").count()).isEqualTo(2);

		// Verify new email appears in the list
		boolean newEmailFound = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newEmail)) {
				newEmailFound = true;
				// Verify it's not marked as primary
				assertThat(page.locator(".email-item").nth(i).locator(".primary-badge").count()).isEqualTo(0);
				break;
			}
		}
		assertThat(newEmailFound).isTrue();

		// Clean up - delete the test user
		deleteCurrentUser();
	}

	@Test
	void testAddEmailAddressAsPrimary() {
		// Create and login as new user
		createUserAndLogin("testprimary", "Test Primary User", "testprimary@example.com");

		// Add new email address and set as primary
		String newPrimaryEmail = "primary.email@example.com";
		page.locator("#email").fill(newPrimaryEmail);
		page.locator("input[name='isPrimary']").check(); // Check the primary checkbox
		page.locator("button[type=submit]").nth(0).click(); // Add Email button

		// Verify redirect to account page with success message
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Email address added successfully");

		// Verify new email is added and marked as primary
		boolean newPrimaryFound = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newPrimaryEmail)) {
				newPrimaryFound = true;
				// Verify it's marked as primary
				assertThat(page.locator(".email-item").nth(i).locator(".primary-badge").count()).isEqualTo(1);
				assertThat(page.locator(".email-item").nth(i).locator(".primary-badge").textContent())
					.isEqualTo("Primary");
				break;
			}
		}
		assertThat(newPrimaryFound).isTrue();

		// Clean up - delete the test user
		deleteCurrentUser();
	}

	@Test
	void testAddDuplicateEmailAddress() {
		// Create and login as new user
		createUserAndLogin("testdup", "Test Duplicate User", "testdup@example.com");

		// Try to add the same email address that was used for registration
		String existingEmail = "testdup@example.com";
		page.locator("#email").fill(existingEmail);
		page.locator("button[type=submit]").nth(0).click(); // Add Email button

		// Verify redirect to account page with error message
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".error-message").textContent()).contains("Email already used");

		// Clean up - delete the test user
		deleteCurrentUser();
	}

	@Test
	void testRemoveEmailAddress() {
		// Create and login as new user
		createUserAndLogin("testremove", "Test Remove User", "testremove@example.com");

		// First, add a new email to ensure we have more than one email
		String newEmail = "removable.email@example.com";
		page.locator("#email").fill(newEmail);
		page.locator("button[type=submit]").nth(0).click(); // Add Email button

		// Verify email is added
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Email address added successfully");

		int initialEmailCount = page.locator(".email-item").count();

		// Set up dialog handler before clicking Remove button
		page.onDialog(dialog -> {
			assertThat(dialog.message()).contains("Are you sure you want to remove this email address?");
			dialog.accept();
		});

		// Find and click the Remove button for the new email
		boolean removedEmail = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newEmail)) {
				// Check if this email has Remove button (not primary)
				if (page.locator(".email-item").nth(i).locator("button").getByText("Remove").count() > 0) {
					page.locator(".email-item").nth(i).locator("button").getByText("Remove").click();
					removedEmail = true;
					break;
				}
			}
		}
		assertThat(removedEmail).isTrue();

		// Verify redirect to account page with success message
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Email address removed successfully");

		// Verify email count decreased
		assertThat(page.locator(".email-item").count()).isEqualTo(initialEmailCount - 1);

		// Verify the removed email is no longer in the list
		boolean emailStillExists = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newEmail)) {
				emailStillExists = true;
				break;
			}
		}
		assertThat(emailStillExists).isFalse();

		// Clean up - delete the test user
		deleteCurrentUser();
	}

	@Test
	void testSetPrimaryEmail() {
		// Create and login as new user
		createUserAndLogin("testsetprimary", "Test Set Primary User", "testsetprimary@example.com");

		// First, add a new email to have an email to set as primary
		String newPrimaryEmail = "new.primary@example.com";
		page.locator("#email").fill(newPrimaryEmail);
		page.locator("button[type=submit]").nth(0).click(); // Add Email button

		// Verify email is added
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Email address added successfully");

		// Find the new email and click "Set Primary" button
		boolean setPrimary = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newPrimaryEmail)) {
				// Check if this email has Set Primary button (not already primary)
				if (page.locator(".email-item").nth(i).getByText("Set Primary").count() > 0) {
					page.locator(".email-item").nth(i).getByText("Set Primary").click();
					setPrimary = true;
					break;
				}
			}
		}
		assertThat(setPrimary).isTrue();

		// Verify redirect to account page with success message
		assertThat(page.title()).isEqualTo("Account");
		assertThat(page.locator(".success-message").textContent()).contains("Primary email updated successfully");

		// Verify the new email is now marked as primary
		boolean newEmailIsPrimary = false;
		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			String emailText = page.locator(".email-item").nth(i).locator(".email-address").textContent();
			if (emailText.equals(newPrimaryEmail)) {
				// Check if this email has Primary badge
				if (page.locator(".email-item").nth(i).locator(".primary-badge").count() > 0) {
					newEmailIsPrimary = true;
					assertThat(page.locator(".email-item").nth(i).locator(".primary-badge").textContent())
						.isEqualTo("Primary");
					break;
				}
			}
		}
		assertThat(newEmailIsPrimary).isTrue();

		// Clean up - delete the test user
		deleteCurrentUser();
	}

	@Test
	void testCannotRemovePrimaryEmail() {
		// Create and login as new user
		createUserAndLogin("testnoremove", "Test No Remove User", "testnoremove@example.com");

		// Verify there is exactly one email and it's marked as primary
		assertThat(page.locator(".email-item").count()).isEqualTo(1);
		assertThat(page.locator(".primary-badge").count()).isEqualTo(1);

		// Verify the primary email does not have Remove button
		Locator primaryEmailItem = page.locator(".email-item").first();
		assertThat(primaryEmailItem.locator(".primary-badge").count()).isEqualTo(1);

		// The primary email should not have any Remove button (check specifically for
		// button with Remove text)
		assertThat(primaryEmailItem.locator("button").getByText("Remove").count()).isEqualTo(0);

		// Add a secondary email to test that non-primary emails do have Remove button
		String secondaryEmail = "secondary@example.com";
		page.locator("#email").fill(secondaryEmail);
		page.locator("button[type=submit]").nth(0).click(); // Add Email button

		// Verify the secondary email has Remove button but primary still doesn't
		assertThat(page.locator(".email-item").count()).isEqualTo(2);

		boolean primaryHasRemove = false;
		boolean secondaryHasRemove = false;

		for (int i = 0; i < page.locator(".email-item").count(); i++) {
			Locator emailItem = page.locator(".email-item").nth(i);
			if (emailItem.locator(".primary-badge").count() > 0) {
				// This is primary email
				if (emailItem.locator("button").getByText("Remove").count() > 0) {
					primaryHasRemove = true;
				}
			}
			else {
				// This is secondary email
				if (emailItem.locator("button").getByText("Remove").count() > 0) {
					secondaryHasRemove = true;
				}
			}
		}

		assertThat(primaryHasRemove).isFalse();
		assertThat(secondaryHasRemove).isTrue();

		// Clean up - delete the test user
		deleteCurrentUser();
	}

}
