package com.example.softdelete.notification.sendgrid;

import com.example.softdelete.notification.Notification;
import com.example.softdelete.notification.NotificationSender;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Profile("sendgrid")
@Component
public class SendGridNotificationSender implements NotificationSender {

	private final RestClient restClient;

	private final SendGridProps props;

	public SendGridNotificationSender(RestClient.Builder restClientBuilder, SendGridProps props) {
		this.restClient = restClientBuilder.baseUrl(props.baseUrl())
			.defaultHeaders(headers -> headers.setBearerAuth(props.apiKey()))
			.defaultStatusHandler(__ -> true, (req, res) -> {
			})
			.build();
		this.props = props;
	}

	@Override
	public void sendNotification(Notification notification) {
		ResponseEntity<String> response = this.restClient.post()
			.uri("/v3/mail/send")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("personalizations",
					List.of(Map.of("to", List.of(Map.of("email", notification.to())), "subject",
							notification.subject())),
					"from", Map.of("email", this.props.from()), "content",
					List.of(Map.of("type", "text/plain", "value", notification.content()))))
			.retrieve()
			.toEntity(String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Failed to send a mail: " + response.getBody());
		}
	}

}
