package com.example.softdelete.user;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.OffsetDateTime;

@JsonTypeName("deleted")
public record DeletedUser(long userId, OffsetDateTime deletedAt) implements User {
}
