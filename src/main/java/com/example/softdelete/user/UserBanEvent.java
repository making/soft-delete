package com.example.softdelete.user;

import java.time.OffsetDateTime;
import java.util.Map;

public record UserBanEvent(long userId, long adminUserId, Map<String, Object> userInfoAtBan, String banReason,
		OffsetDateTime bannedAt) {

}
