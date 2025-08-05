package com.example.softdelete.user.web;

import com.example.softdelete.security.ActiveUserDetails;
import com.example.softdelete.user.ActiveUser;
import com.example.softdelete.user.UserService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping(path = "/")
	public String index() {
		return "redirect:/account";
	}

	@GetMapping(path = "/account")
	public String showUserAccount(@AuthenticationPrincipal ActiveUserDetails userDetails, Model model) {
		model.addAttribute("user", userDetails.getActiveUser());
		return "user-account";
	}

	@GetMapping(path = "/signup")
	public String showSignupForm() {
		return "user-signup";
	}

	@PostMapping(path = "/signup")
	public String processSignup(UserService.UserRegistration userRegistration, UriComponentsBuilder uriBuilder) {
		this.userService.registerUser(userRegistration,
				uriBuilder.replacePath("").replaceQuery(null).fragment(null).build().toUri());
		return "redirect:/signup?success";
	}

	@GetMapping(path = "/signup", params = "success")
	public String showSignupSuccess() {
		return "user-signup-success";
	}

	@GetMapping(path = "/activation")
	public String processActivation(@RequestParam UUID token, Model model) {
		UserService.ActivationResult result = this.userService.activateUser(token);
		if (result == UserService.ActivationResult.SUCCESS) {
			return "user-activation-success";
		}
		else {
			model.addAttribute("error", result.getDefaultMessage());
			return "user-activation-failure";
		}

	}

	@GetMapping(path = "/delete", params = "confirm")
	public String showDeleteConfirmation() {
		return "user-delete-confirm";
	}

	@PostMapping(path = "/delete", params = "cancel")
	public String cancelDeleteUser() {
		return "redirect:/account";
	}

	@PostMapping(path = "/delete")
	public String processDeleteUser(@AuthenticationPrincipal ActiveUserDetails userDetails) {
		ActiveUser activeUser = userDetails.getActiveUser();
		this.userService.deleteUser(activeUser.userId());
		return "redirect:/goodbye";
	}

	@GetMapping(path = "/goodbye")
	public String showGoodbye() {
		SecurityContextHolder.getContext().setAuthentication(null);
		return "goodbye";
	}

}
