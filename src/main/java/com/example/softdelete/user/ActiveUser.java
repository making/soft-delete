package com.example.softdelete.user;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeName("active")
public record ActiveUser(long userId, UserProfile userProfile, List<Email> emails, boolean isAdmin) implements User {

	public String primaryEmail() {
		return emails.stream()
			.filter(Email::isPrimary)
			.map(Email::email)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No primary email found for user " + userId));
	}
}
