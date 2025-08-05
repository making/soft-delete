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
		page.navigate("http://localhost:" + serverPort);
		login("janesminth", "jane.smith@example.org");
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
	}

}
