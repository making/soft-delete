package com.example.softdelete.user;

import am.ik.pagination.CursorPage;
import am.ik.pagination.CursorPageRequest;
import am.ik.pagination.CursorPageRequest.Navigation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserMapper {

	private final JdbcClient jdbcClient;

	private final RowMapper<User> userRowMapper;

	public UserMapper(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.userRowMapper = (rs, rowNum) -> {
			String type = rs.getString("type");
			long userId = rs.getLong("user_id");
			try {
				return switch (type) {
					case "active" ->
						new ActiveUser(userId, new UserProfile(rs.getString("username"), rs.getString("display_name")),
								objectMapper.readValue(rs.getString("emails"), new TypeReference<>() {
								}), rs.getBoolean("is_admin"));
					case "pending" ->
						new PendingUser(userId, new UserProfile(rs.getString("username"), rs.getString("display_name")),
								objectMapper.readValue(rs.getString("emails"), new TypeReference<>() {
								}), rs.getObject("activation_token", UUID.class),
								rs.getObject("expires_at", OffsetDateTime.class));
					case "deleted" -> new DeletedUser(userId, rs.getObject("deleted_at", OffsetDateTime.class));
					default -> null;
				};
			}
			catch (JsonProcessingException e) {
				throw new UncheckedIOException(e);
			}
		};
		;
	}

	@Transactional
	public long insertUser() {
		return this.jdbcClient.sql("""
				INSERT INTO users DEFAULT VALUES RETURNING user_id
				""").query(Long.class).single();
	}

	public Optional<Long> findUserIdByEmail(String email) {
		return this.jdbcClient.sql("""
				SELECT user_id FROM user_emails WHERE email = :email
				""").param("email", email).query(Long.class).optional();
	}

	public Optional<Long> findUserIdByUsername(String username) {
		return this.jdbcClient.sql("""
				SELECT user_id FROM user_profiles WHERE username = :username
				""").param("username", username).query(Long.class).optional();
	}

	@Transactional
	public long deleteUser(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM users WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public long insertUserProfile(long userId, UserProfile userProfile) {
		return this.jdbcClient.sql("""
				INSERT INTO user_profiles (user_id, username, display_name) VALUES (:userId, :username, :displayName)
				""")
			.param("userId", userId)
			.param("username", userProfile.username())
			.param("displayName", userProfile.displayName())
			.update();
	}

	@Transactional
	public long deleteUserProfile(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM user_profiles WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public void insertUserEmail(long userId, Email email) {
		this.jdbcClient.sql("""
				INSERT INTO user_emails (user_id, email) VALUES (:userId, :email)
				""").param("userId", userId).param("email", email.email()).update();
		if (email.isPrimary()) {
			this.jdbcClient.sql("""
					INSERT INTO user_primary_emails (user_id, email) VALUES (:userId, :email)
					ON CONFLICT (user_id) DO UPDATE SET email = :email
					""").param("userId", userId).param("email", email.email()).update();
		}
	}

	@Transactional
	public void deleteUserEmail(long userId) {
		this.jdbcClient.sql("""
				DELETE FROM user_primary_emails WHERE user_id = :userId
				""").param("userId", userId).update();
		this.jdbcClient.sql("""
				DELETE FROM user_emails WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public int insertPendingUser(PendingUser pendingUser) {
		return this.jdbcClient
			.sql("""
					INSERT INTO pending_users (user_id, activation_token, expires_at) VALUES (:userId, :activationToken, :expiresAt)
					""")
			.param("userId", pendingUser.userId())
			.param("activationToken", pendingUser.activationToken())
			.param("expiresAt", pendingUser.expiresAt())
			.update();
	}

	public Optional<PendingUser> getPendingUserByToken(UUID activationToken) {
		return this.jdbcClient.sql("""
				SELECT
				    'pending' as type,
				    u.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    LEFT JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE pu.activation_token = :activationToken
				GROUP BY
				    u.user_id,
				    pu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at
				""")
			.param("activationToken", activationToken)
			.query(userRowMapper)
			.optional()
			.map(PendingUser.class::cast);
	}

	@Transactional
	public int deletePendingUser(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM pending_users WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public int insertActiveUser(long userId) {
		return this.jdbcClient.sql("""
				INSERT INTO active_users (user_id) VALUES (:userId)
				""").param("userId", userId).update();
	}

	@Transactional
	public int deleteActiveUser(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM active_users WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public int insertAdminUser(long userId) {
		return this.jdbcClient.sql("""
				INSERT INTO admin_users (user_id) VALUES (:userId)
				""").param("userId", userId).update();
	}

	@Transactional
	public int deleteAdminUser(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM admin_users WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	@Transactional
	public int insertDeletedUser(long userId) {
		return this.jdbcClient.sql("""
				INSERT INTO deleted_users (user_id) VALUES (:userId)
				""").param("userId", userId).update();
	}

	@Transactional
	public int deleteDeletedUser(long userId) {
		return this.jdbcClient.sql("""
				DELETE FROM deleted_users WHERE user_id = :userId
				""").param("userId", userId).update();
	}

	public Optional<User> findUser(long userId) {
		return this.jdbcClient.sql("""
				SELECT
				    u.user_id,
				    CASE
				        WHEN au.user_id IS NOT NULL THEN 'active'
				        WHEN pu.user_id IS NOT NULL THEN 'pending'
				        WHEN du.user_id IS NOT NULL THEN 'deleted'
				        ELSE 'unknown'
				    END AS type,
				    CASE
				        WHEN adu.user_id IS NOT NULL THEN TRUE
				        ELSE FALSE
				    END AS is_admin,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    LEFT JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN active_users au ON u.user_id = au.user_id
				    LEFT JOIN admin_users adu ON au.user_id = adu.user_id
				    LEFT JOIN deleted_users du ON u.user_id = du.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id = :userId
				GROUP BY
				    u.user_id,
				    au.user_id,
				    pu.user_id,
				    du.user_id,
				    adu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at
				""").param("userId", userId).query(userRowMapper).optional();
	}

	private <T extends User> CursorPage<T, Long> findUsersByType(CursorPageRequest<Long> pageRequest, String nextQuery,
			String previousQuery, Class<T> userType) {
		Optional<Long> cursor = pageRequest.cursorOptional();
		int pageSizePlus1 = pageRequest.pageSize() + 1;
		Navigation navigation = pageRequest.navigation();

		List<T> contentPlus1 = this.jdbcClient.sql(navigation.isNext() ? nextQuery : previousQuery)
			.param("cursor", cursor.orElse(navigation.isNext() ? Long.MAX_VALUE : Long.MIN_VALUE))
			.param("limit", pageSizePlus1)
			.query(userRowMapper)
			.list()
			.stream()
			.map(userType::cast)
			.toList();

		boolean hasPrevious;
		boolean hasNext;
		List<T> content;
		if (navigation.isNext()) {
			hasPrevious = cursor.isPresent();
			hasNext = contentPlus1.size() == pageSizePlus1;
			content = hasNext ? contentPlus1.subList(0, pageRequest.pageSize()) : contentPlus1;
		}
		else {
			hasPrevious = contentPlus1.size() == pageSizePlus1;
			hasNext = cursor.isPresent();
			content = hasPrevious ? contentPlus1.subList(1, pageSizePlus1) : contentPlus1;
		}
		return new CursorPage<>(content, pageRequest.pageSize(), User::userId, hasPrevious, hasNext);
	}

	public CursorPage<ActiveUser, Long> findActiveUsers(CursorPageRequest<Long> pageRequest) {
		String nextQuery = """
				SELECT
				    u.user_id,
				    'active' AS type,
				    CASE
				        WHEN adu.user_id IS NOT NULL THEN TRUE
				        ELSE FALSE
				    END AS is_admin,
				    up.username,
				    up.display_name,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    INNER JOIN active_users au ON u.user_id = au.user_id
				    LEFT JOIN admin_users adu ON au.user_id = adu.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id < :cursor
				GROUP BY
				    u.user_id,
				    au.user_id,
				    adu.user_id,
				    up.username,
				    up.display_name
				ORDER BY u.user_id DESC
				LIMIT :limit
				""";

		String previousQuery = """
				WITH page AS (SELECT
				    u.user_id,
				    'active' AS type,
				    CASE
				        WHEN adu.user_id IS NOT NULL THEN TRUE
				        ELSE FALSE
				    END AS is_admin,
				    up.username,
				    up.display_name,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    INNER JOIN active_users au ON u.user_id = au.user_id
				    LEFT JOIN admin_users adu ON au.user_id = adu.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id > :cursor
				GROUP BY
				    u.user_id,
				    au.user_id,
				    adu.user_id,
				    up.username,
				    up.display_name
				ORDER BY u.user_id ASC
				LIMIT :limit)
				SELECT * FROM page ORDER BY user_id DESC
				""";

		return findUsersByType(pageRequest, nextQuery, previousQuery, ActiveUser.class);
	}

	public CursorPage<PendingUser, Long> findPendingUsers(CursorPageRequest<Long> pageRequest) {
		String nextQuery = """
				SELECT
				    u.user_id,
				    'pending' AS type,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    NULL AS deleted_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    INNER JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id < :cursor
				GROUP BY
				    u.user_id,
				    pu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at
				ORDER BY u.user_id DESC
				LIMIT :limit
				""";

		String previousQuery = """
				WITH page AS (SELECT
				    u.user_id,
				    'pending' AS type,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    NULL AS deleted_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    INNER JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id > :cursor
				GROUP BY
				    u.user_id,
				    pu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at
				ORDER BY u.user_id ASC
				LIMIT :limit)
				SELECT * FROM page ORDER BY user_id DESC
				""";

		return findUsersByType(pageRequest, nextQuery, previousQuery, PendingUser.class);
	}

	public CursorPage<DeletedUser, Long> findDeletedUsers(CursorPageRequest<Long> pageRequest) {
		String nextQuery = """
				SELECT
				    u.user_id,
				    'deleted' AS type,
				    du.deleted_at
				FROM
				    users u
				    INNER JOIN deleted_users du ON u.user_id = du.user_id
				WHERE u.user_id < :cursor
				ORDER BY u.user_id DESC
				LIMIT :limit
				""";

		String previousQuery = """
				WITH page AS (SELECT
				    u.user_id,
				    'deleted' AS type,
				    du.deleted_at
				FROM
				    users u
				    INNER JOIN deleted_users du ON u.user_id = du.user_id
				WHERE u.user_id > :cursor
				ORDER BY u.user_id ASC
				LIMIT :limit)
				SELECT * FROM page ORDER BY user_id DESC
				""";

		return findUsersByType(pageRequest, nextQuery, previousQuery, DeletedUser.class);
	}

	public CursorPage<User, Long> findUsers(CursorPageRequest<Long> pageRequest) {
		String nextQuery = """
				SELECT
				    u.user_id,
				    CASE
				        WHEN au.user_id IS NOT NULL THEN 'active'
				        WHEN pu.user_id IS NOT NULL THEN 'pending'
				        WHEN du.user_id IS NOT NULL THEN 'deleted'
				        ELSE 'unknown'
				    END AS type,
				    CASE
				        WHEN adu.user_id IS NOT NULL THEN TRUE
				        ELSE FALSE
				    END AS is_admin,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    LEFT JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN active_users au ON u.user_id = au.user_id
				    LEFT JOIN admin_users adu ON au.user_id = adu.user_id
				    LEFT JOIN deleted_users du ON u.user_id = du.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id < :cursor
				GROUP BY
				    u.user_id,
				    au.user_id,
				    pu.user_id,
				    du.user_id,
				    adu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at
				ORDER BY u.user_id DESC
				LIMIT :limit
				""";

		String previousQuery = """
				WITH page AS (SELECT
				    u.user_id,
				    CASE
				        WHEN au.user_id IS NOT NULL THEN 'active'
				        WHEN pu.user_id IS NOT NULL THEN 'pending'
				        WHEN du.user_id IS NOT NULL THEN 'deleted'
				        ELSE 'unknown'
				    END AS type,
				    CASE
				        WHEN adu.user_id IS NOT NULL THEN TRUE
				        ELSE FALSE
				    END AS is_admin,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at,
				    COALESCE(
				        jsonb_agg(
				            jsonb_build_object(
				                'email', ue.email,
				                'isPrimary', CASE WHEN upe.email = ue.email THEN TRUE ELSE FALSE END
				            ) ORDER BY
				                CASE WHEN upe.email = ue.email THEN 0 ELSE 1 END,  -- Primary first
				                ue.created_at
				        ) FILTER (WHERE ue.email IS NOT NULL),
				        '[]'::jsonb
				    ) AS emails
				FROM
				    users u
				    LEFT JOIN pending_users pu ON u.user_id = pu.user_id
				    LEFT JOIN active_users au ON u.user_id = au.user_id
				    LEFT JOIN admin_users adu ON au.user_id = adu.user_id
				    LEFT JOIN deleted_users du ON u.user_id = du.user_id
				    LEFT JOIN user_profiles up ON u.user_id = up.user_id
				    LEFT JOIN user_emails ue ON u.user_id = ue.user_id
				    LEFT JOIN user_primary_emails upe ON u.user_id = upe.user_id
				WHERE u.user_id > :cursor
				GROUP BY
				    u.user_id,
				    au.user_id,
				    pu.user_id,
				    du.user_id,
				    adu.user_id,
				    up.username,
				    up.display_name,
				    pu.activation_token,
				    pu.expires_at,
				    du.deleted_at
				ORDER BY u.user_id ASC
				LIMIT :limit)
				SELECT * FROM page ORDER BY user_id DESC
				""";

		return findUsersByType(pageRequest, nextQuery, previousQuery, User.class);
	}

}
