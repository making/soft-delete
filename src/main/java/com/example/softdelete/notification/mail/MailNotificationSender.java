package com.example.softdelete.notification.mail;

import com.example.softdelete.notification.Notification;
import com.example.softdelete.notification.NotificationSender;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Profile("mail")
@Component
public class MailNotificationSender implements NotificationSender {

	private final JavaMailSender mailSender;

	public MailNotificationSender(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public void sendNotification(Notification notification) {
		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setSubject(notification.subject());
		mailMessage.setTo(notification.to());
		mailMessage.setText(notification.content());
		// TODO setFrom
		this.mailSender.send(mailMessage);
	}

}
