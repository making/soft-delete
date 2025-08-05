package com.example.softdelete.notification;

import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

@Fallback
@Component
public class ConsoleNotificationSender implements NotificationSender {

	@Override
	public void sendNotification(Notification notification) {
		System.out.printf("""
				Subject: %s
				To: %s
				---
				%s
				%n""", notification.subject(), notification.to(), notification.content());
	}

}
