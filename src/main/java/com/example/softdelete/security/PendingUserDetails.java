package com.example.softdelete.security;

import com.example.softdelete.user.PendingUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class PendingUserDetails implements UserDetails {

	private final PendingUser pendingUser;

	public PendingUserDetails(PendingUser pendingUser) {
		this.pendingUser = pendingUser;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return "";
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

}
