package com.example.softdelete.user;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public sealed interface User permits ActiveUser, DeletedUser, PendingUser {

	long userId();

}
