package com.example.softdelete.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserDeletionEventMapper {

	private final JdbcClient jdbcClient;

	private final ObjectMapper objectMapper;

	public UserDeletionEventMapper(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public int insertUserDeletionEvent(UserDeletionEvent event) {
		try {
			String userInfoAtDeletion = this.objectMapper.writeValueAsString(event.userInfoAtDeletion());
			return this.jdbcClient
				.sql("""
						INSERT INTO user_deletion_events (user_id, user_info_at_deletion, deleted_at) VALUES (:userId, :userInfoAtDeletion::json, :deletedAt)
						""")
				.param("userId", event.userId())
				.param("userInfoAtDeletion", userInfoAtDeletion)
				.param("deletedAt", event.deletedAt())
				.update();
		}
		catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

}
