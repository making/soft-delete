package com.example.softdelete;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
	}

	@SuppressWarnings("resource")
	@Profile({ "sendgrid", "mail" })
	@Bean
	FixedHostPortGenericContainer<?> sendgridMaildev(@Value("${maildev.port:31080}") int maildevPort) {
		var container = new FixedHostPortGenericContainer<>("ykanazawa/sendgrid-maildev")
			.withEnv("SENDGRID_DEV_API_SERVER", ":3030")
			.withEnv("SENDGRID_DEV_API_KEY", "SG.test")
			.withEnv("SENDGRID_DEV_SMTP_SERVER", "127.0.0.1:1025")
			.withExposedPorts(3030, 1080, 1025)
			.waitingFor(new LogMessageWaitStrategy().withRegEx(".*sendgrid-dev entered RUNNING state.*")
				.withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)))
			.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("sendgrid-maildev")));
		return maildevPort > 0 ? container.withFixedExposedPort(maildevPort, 1080) : container;
	}

	@Profile("sendgrid")
	@Bean
	DynamicPropertyRegistrar dynamicSendGridPropertyRegistrar(GenericContainer<?> sendgridMaildev) {
		return registry -> {
			registry.add("sendgrid.base-url", () -> "http://127.0.0.1:" + sendgridMaildev.getMappedPort(3030));
			registry.add("sendgrid.api-key", () -> "SG.test");
			registry.add("maildev.port", () -> sendgridMaildev.getMappedPort(1080));
		};
	}

	@Profile("mail")
	@Bean
	DynamicPropertyRegistrar dynamicMailPropertyRegistrar(GenericContainer<?> sendgridMaildev) {
		return registry -> {
			registry.add("spring.mail.host", () -> "localhost");
			registry.add("spring.mail.port", () -> sendgridMaildev.getMappedPort(1025));
			registry.add("maildev.port", () -> sendgridMaildev.getMappedPort(1080));
		};
	}

}
