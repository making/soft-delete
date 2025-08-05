package com.example.softdelete.security;

import com.example.softdelete.user.ActiveUser;
import com.example.softdelete.user.DeletedUser;
import com.example.softdelete.user.PendingUser;
import com.example.softdelete.user.User;
import com.example.softdelete.user.UserMapper;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UserMapper userMapper;

	public UserDetailsServiceImpl(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Optional<Long> userIdOptional = username.contains("@") ? userMapper.findUserIdByEmail(username)
				: userMapper.findUserIdByUsername(username);
		long userId = userIdOptional.orElseThrow(() -> usernameNotFoundException(username));
		User user = this.userMapper.findUser(userId).orElseThrow(() -> usernameNotFoundException(username));
		return switch (user) {
			case ActiveUser activeUser -> new ActiveUserDetails(activeUser);
			case PendingUser pendingUser -> new PendingUserDetails(pendingUser);
			case DeletedUser ignored -> throw usernameNotFoundException(username);
		};
	}

	UsernameNotFoundException usernameNotFoundException(String username) {
		return new UsernameNotFoundException(username + " is not found");
	}

}
