package com.example.softdelete.config;

import java.time.Clock;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.IdGenerator;
import org.springframework.util.JdkIdGenerator;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

@Configuration(proxyBeanMethods = false)
class AppConfig {

	@Bean
	IdGenerator idGenerator() {
		return new JdkIdGenerator();
	}

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

	@Bean
	RestClientCustomizer restClientCustomizer(Logbook logbook) {
		return builder -> builder.requestInterceptor(new LogbookClientHttpRequestInterceptor(logbook));
	}

}
