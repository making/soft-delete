package com.example.softdelete.security;

import com.example.softdelete.user.ActiveUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

public class ActiveUserDetails implements UserDetails {

	private final ActiveUser activeUser;

	private final List<GrantedAuthority> authorities;

	public ActiveUserDetails(ActiveUser activeUser) {
		this.activeUser = activeUser;
		this.authorities = new ArrayList<>(AuthorityUtils.createAuthorityList("ROLE_USER"));
		if (activeUser.isAdmin()) {
			this.authorities.addAll(AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
		}
	}

	public ActiveUser getActiveUser() {
		return this.activeUser;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.authorities;
	}

	@Override
	public String getPassword() {
		throw new UnsupportedOperationException("ActiveUserDetails does not have a password");
	}

	@Override
	public String getUsername() {
		return activeUser.userProfile().username();
	}

	@Override
	public String toString() {
		return "ActiveUserDetails{activeUser=%s}".formatted(activeUser);
	}

}
