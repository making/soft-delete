package com.example.softdelete.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(authz -> authz
				.requestMatchers("/login", "/login/ott", "/logout", "/ott/sent", "/signup", "/activation", "/error",
						"/style.css", "/goodbye")
				.permitAll()
				.requestMatchers("/admin/**")
				.hasRole("ADMIN")
				.anyRequest()
				.authenticated())
			.formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/"))
			.oneTimeTokenLogin(ott -> ott.loginPage("/login").showDefaultSubmitPage(false))
			.build();
	}

	@Bean
	JdbcOneTimeTokenService jdbcOneTimeTokenService(JdbcTemplate jdbcTemplate) {
		return new JdbcOneTimeTokenService(jdbcTemplate);
	}

}
