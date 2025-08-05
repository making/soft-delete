package com.example.softdelete.user;

import am.ik.pagination.CursorPageRequest;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PendingUserHouseKeeper {

	private final UserMapper userMapper;

	private final Clock clock;

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	public PendingUserHouseKeeper(UserMapper userMapper, Clock clock) {
		this.userMapper = userMapper;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 * * * *")
	@Transactional
	public void cleanUpPendingUsers() {
		logger.info("Cleaning up pending users");
		CursorPageRequest<Long> pageRequest = new CursorPageRequest<>(Long.MAX_VALUE, 200,
				CursorPageRequest.Navigation.NEXT);
		this.userMapper.findPendingUsers(pageRequest).content().forEach(pendingUser -> {
			if (pendingUser.isExpired(this.clock)) {
				long userId = pendingUser.userId();
				this.userMapper.deletePendingUser(userId);
				this.userMapper.deleteUserProfile(userId);
				this.userMapper.deleteUserEmail(userId);
				this.userMapper.deleteUser(userId);
			}
		});
	}

}
