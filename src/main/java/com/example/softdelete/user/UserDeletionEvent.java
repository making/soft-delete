package com.example.softdelete.user;

import java.time.OffsetDateTime;
import java.util.Map;

public record UserDeletionEvent(long userId, Map<String, Object> userInfoAtDeletion, OffsetDateTime deletedAt) {

}
