package com.example.softdelete.sendgrid;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sendgrid")
public record SendGridProps(String apiKey, @DefaultValue("https://api.sendgrid.com") URI baseUrl,
		@DefaultValue("noreply@example.com") String from) {

}
