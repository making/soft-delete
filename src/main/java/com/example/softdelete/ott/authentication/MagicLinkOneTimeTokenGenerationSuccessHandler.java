package com.example.softdelete.ott.authentication;

import com.example.softdelete.notification.Notification;
import com.example.softdelete.notification.NotificationSender;
import com.example.softdelete.security.ActiveUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MagicLinkOneTimeTokenGenerationSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

	private final Logger logger = LoggerFactory.getLogger(MagicLinkOneTimeTokenGenerationSuccessHandler.class);

	private final OneTimeTokenGenerationSuccessHandler redirectHandler = new RedirectOneTimeTokenGenerationSuccessHandler(
			"/ott/sent");

	private final UserDetailsService userDetailsService;

	private final NotificationSender notificationSender;

	public MagicLinkOneTimeTokenGenerationSuccessHandler(UserDetailsService userDetailsService,
			NotificationSender notificationSender) {
		this.userDetailsService = userDetailsService;
		this.notificationSender = notificationSender;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
			throws IOException, ServletException {
		try {
			UserDetails userDetails = this.userDetailsService.loadUserByUsername(oneTimeToken.getUsername());
			if (userDetails instanceof ActiveUserDetails activeUserDetails) {
				URI magicLink = UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
					.replacePath(request.getContextPath())
					.replaceQuery(null)
					.fragment(null)
					.path("/login/ott")
					.queryParam("token", oneTimeToken.getTokenValue())
					.build()
					.toUri();
				Notification notification = new Notification(activeUserDetails.getActiveUser().primaryEmail(),
						"Your One Time Token", "Use the following link to sign in into the application: " + magicLink);
				this.notificationSender.sendNotification(notification);
				this.redirectHandler.handle(request, response, oneTimeToken);
			}
			else if (!userDetails.isEnabled()) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN,
						"Your account is not activated yet. Please check your email for the activation link.");
			}
		}
		catch (UsernameNotFoundException e) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
		}
		catch (ResponseStatusException e) {
			logger.error("Failed to send a magic link", e);
			response.sendError(e.getStatusCode().value(), e.getReason());
		}
	}

}