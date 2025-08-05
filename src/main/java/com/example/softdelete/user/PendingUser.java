package com.example.softdelete.user;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonTypeName("pending")
public record PendingUser(long userId, UserProfile userProfile, List<Email> emails, UUID activationToken,
		OffsetDateTime expiresAt) implements User {

	public boolean isExpired(Clock clock) {
		return OffsetDateTime.now(clock).isAfter(this.expiresAt);
	}

	public boolean isValidToken(UUID activationToken) {
		return Objects.equals(activationToken, this.activationToken);
	}
}
