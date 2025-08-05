package com.example.softdelete.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserBanEventMapper {

	private final JdbcClient jdbcClient;

	private final ObjectMapper objectMapper;

	public UserBanEventMapper(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public int insertUserBanEvent(UserBanEvent event) {
		try {
			String userInfoAtBan = this.objectMapper.writeValueAsString(event.userInfoAtBan());
			return this.jdbcClient
				.sql("""
						INSERT INTO user_ban_events (user_id, admin_user_id, user_info_at_ban, ban_reason, banned_at) VALUES (:userId, :adminUserId, :userInfoAtBan::jsonb, :banReason, :bannedAt)
						""")
				.param("userId", event.userId())
				.param("adminUserId", event.adminUserId())
				.param("userInfoAtBan", userInfoAtBan)
				.param("banReason", event.banReason())
				.param("bannedAt", event.bannedAt())
				.update();
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

}
