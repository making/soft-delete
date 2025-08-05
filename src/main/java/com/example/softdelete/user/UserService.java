package com.example.softdelete.user;

import com.example.softdelete.notification.Notification;
import com.example.softdelete.notification.NotificationSender;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

@Service
public class UserService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final UserMapper userMapper;

	private final UserDeletionEventMapper userDeletionEventMapper;

	private final UserBanEventMapper userBanEventMapper;

	private final NotificationSender notificationSender;

	private final IdGenerator idGenerator;

	private final Clock clock;

	public UserService(UserMapper userMapper, UserDeletionEventMapper userDeletionEventMapper,
			UserBanEventMapper userBanEventMapper, NotificationSender notificationSender, IdGenerator idGenerator,
			Clock clock) {
		this.userMapper = userMapper;
		this.userDeletionEventMapper = userDeletionEventMapper;
		this.userBanEventMapper = userBanEventMapper;
		this.notificationSender = notificationSender;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	@Transactional
	public PendingUser registerUser(UserRegistration userRegistration, URI baseUrl) {
		long userId = this.userMapper.insertUser();
		UserProfile userProfile = new UserProfile(userRegistration.username(), userRegistration.displayName());
		Email email = new Email(userRegistration.email(), true);
		this.userMapper.insertUserProfile(userId, userProfile);
		this.userMapper.insertUserEmail(userId, email);
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		OffsetDateTime expiredAt = now.plusHours(3);
		UUID activationToken = this.idGenerator.generateId();
		PendingUser pendingUser = new PendingUser(userId, userProfile, List.of(email), activationToken, expiredAt);
		this.userMapper.insertPendingUser(pendingUser);
		String content = """
				Hello %s,

				Please activate your account by clicking the following link:
				%s/activation?token=%s

				This link will expire at %s.

				Thank you!""".formatted(userRegistration.displayName(), baseUrl, activationToken, expiredAt);
		Notification notification = new Notification(email.email(), "Activate your account", content);
		this.notificationSender.sendNotification(notification);
		return pendingUser;
	}

	@Transactional
	public ActivationResult activateUser(UUID activationToken) {
		Optional<PendingUser> userOptional = this.userMapper.getPendingUserByToken(activationToken);
		if (userOptional.isEmpty()) {
			return ActivationResult.USER_NOT_FOUND;
		}
		PendingUser pendingUser = userOptional.get();
		ActivationResult result;
		if (pendingUser.isExpired(this.clock)) {
			result = ActivationResult.TOKEN_EXPIRED;
		}
		else if (!pendingUser.isValidToken(activationToken)) {
			result = ActivationResult.INVALID_TOKEN;
		}
		else {
			result = ActivationResult.SUCCESS;
		}
		long userId = pendingUser.userId();
		this.userMapper.deletePendingUser(userId);
		if (result == ActivationResult.SUCCESS) {
			this.userMapper.insertActiveUser(userId);
		}
		else {
			this.userMapper.deleteUserProfile(userId);
			this.userMapper.deleteUserEmail(userId);
			this.userMapper.deleteUser(userId);
		}
		return result;
	}

	@Transactional
	public ActiveUser promoteToAdmin(long userId) {
		User user = this.userMapper.findUser(userId).orElseThrow(() -> new UserException("User not found: " + userId));
		if (user instanceof ActiveUser activeUser) {
			if (activeUser.isAdmin()) {
				throw new UserException("User is already an admin: " + userId);
			}
		}
		else {
			throw new UserException("User is not active: " + userId);
		}
		this.userMapper.insertAdminUser(activeUser.userId());
		return new ActiveUser(activeUser.userId(), activeUser.userProfile(), activeUser.emails(), true);
	}

	private ActiveUser deleteActiveUserInternal(long userId) {
		User user = this.userMapper.findUser(userId).orElseThrow(() -> new UserException("User not found: " + userId));
		if (user instanceof DeletedUser) {
			throw new UserException("User is already deleted: " + userId);
		}
		if (!(user instanceof ActiveUser activeUser)) {
			throw new UserException("User is not active: " + userId);
		}
		this.userMapper.deleteAdminUser(userId);
		this.userMapper.deleteActiveUser(userId);
		this.userMapper.deleteUserProfile(userId);
		this.userMapper.deleteUserEmail(userId);
		this.userMapper.insertDeletedUser(userId);
		return activeUser;
	}

	@Transactional
	public DeletedUser deleteUser(long userId) {
		ActiveUser activeUser = this.deleteActiveUserInternal(userId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		Map<String, Object> userInfo = Map.of("username", activeUser.userProfile().username(), "displayName",
				activeUser.userProfile().displayName(), "emails", activeUser.emails());
		UserDeletionEvent event = new UserDeletionEvent(activeUser.userId(), userInfo, now);
		this.userDeletionEventMapper.insertUserDeletionEvent(event);
		return new DeletedUser(activeUser.userId(), now);
	}

	@Transactional
	public DeletedUser banUser(long userId, long adminUserId, String banReason) {
		ActiveUser activeUser = this.deleteActiveUserInternal(userId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		Map<String, Object> userInfo = Map.of("username", activeUser.userProfile().username(), "displayName",
				activeUser.userProfile().displayName(), "emails", activeUser.emails());
		UserBanEvent event = new UserBanEvent(activeUser.userId(), adminUserId, userInfo, banReason, now);
		this.userBanEventMapper.insertUserBanEvent(event);
		return new DeletedUser(activeUser.userId(), now);
	}

	@Transactional
	public ActiveUser addEmail(long userId, Email email) {
		User user = this.userMapper.findUser(userId).orElseThrow(() -> new UserException("User not found: " + userId));
		if (!(user instanceof ActiveUser)) {
			throw new UserException("User is not active: " + userId);
		}

		if (this.userMapper.existsUserEmail(email.email())) {
			throw new UserException("Email already used: " + email.email());
		}

		// Insert email as non-primary initially
		Email newEmail = new Email(email.email(), false);
		this.userMapper.insertUserEmail(userId, newEmail);

		if (email.isPrimary()) {
			this.userMapper.updatePrimaryEmail(userId, email.email());
		}

		// Return updated user
		return (ActiveUser) this.userMapper.findUser(userId)
			.orElseThrow(() -> new UserException("User not found after update: " + userId));
	}

	@Transactional
	public ActiveUser removeEmail(long userId, String email) {
		User user = this.userMapper.findUser(userId).orElseThrow(() -> new UserException("User not found: " + userId));
		if (!(user instanceof ActiveUser activeUser)) {
			throw new UserException("User is not active: " + userId);
		}

		// Check if email exists for this user
		boolean emailExists = activeUser.emails().stream().anyMatch(e -> e.email().equals(email));
		if (!emailExists) {
			throw new UserException("Email not found: " + email);
		}

		// Check if this is the primary email
		if (email.equals(activeUser.primaryEmail())) {
			throw new UserException("Cannot delete primary email. Please set another email as primary first.");
		}

		// Check if user has more than one email
		if (activeUser.emails().size() <= 1) {
			throw new UserException("Cannot delete the only email address.");
		}

		this.userMapper.deleteUserEmail(userId, email);

		// Return updated user
		return (ActiveUser) this.userMapper.findUser(userId)
			.orElseThrow(() -> new UserException("User not found after update: " + userId));
	}

	@Transactional
	public ActiveUser setPrimaryEmail(long userId, String email) {
		User user = this.userMapper.findUser(userId).orElseThrow(() -> new UserException("User not found: " + userId));
		if (!(user instanceof ActiveUser)) {
			throw new UserException("User is not active: " + userId);
		}

		// Check if email exists for this user
		boolean emailExists = ((ActiveUser) user).emails().stream().anyMatch(e -> e.email().equals(email));
		if (!emailExists) {
			throw new UserException("Email not found: " + email);
		}

		this.userMapper.updatePrimaryEmail(userId, email);

		// Return updated user
		return (ActiveUser) this.userMapper.findUser(userId)
			.orElseThrow(() -> new UserException("User not found after update: " + userId));
	}

	public record UserRegistration(String email, String username, String displayName) {
	}

	public enum ActivationResult {

		SUCCESS("Successfully activated!"), USER_NOT_FOUND("The requested user not found"),
		TOKEN_EXPIRED("The token expired"), INVALID_TOKEN("Invalid token");

		private final String defaultMessage;

		ActivationResult(String defaultMessage) {
			this.defaultMessage = defaultMessage;
		}

		public String getDefaultMessage() {
			return defaultMessage;
		}

	}

	public static class UserException extends RuntimeException {

		public UserException(String message) {
			super(message);
		}

		public UserException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
