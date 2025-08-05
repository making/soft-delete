package com.example.softdelete.user.web;

import com.example.softdelete.security.ActiveUserDetails;
import com.example.softdelete.user.ActiveUser;
import com.example.softdelete.user.Email;
import com.example.softdelete.user.UserService;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
	public String showUserAccount(@AuthenticationPrincipal ActiveUserDetails userDetails, Model model,
			@RequestParam(required = false) String emailError, @RequestParam(required = false) String emailSuccess) {
		model.addAttribute("user", userDetails.getActiveUser());
		if (emailError != null) {
			model.addAttribute("emailError", emailError);
		}
		if (emailSuccess != null) {
			model.addAttribute("emailSuccess", emailSuccess);
		}
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

	@PostMapping(path = "/account/add-email")
	public String addEmail(@AuthenticationPrincipal ActiveUserDetails userDetails, @RequestParam String email,
			@RequestParam(required = false) boolean isPrimary) {
		try {
			ActiveUser activeUser = userDetails.getActiveUser();
			Email emailToAdd = new Email(email, isPrimary);
			ActiveUser updatedUser = this.userService.addEmail(activeUser.userId(), emailToAdd);

			// Update security context with updated user information
			ActiveUserDetails updatedUserDetails = new ActiveUserDetails(updatedUser);
			UsernamePasswordAuthenticationToken updatedAuth = new UsernamePasswordAuthenticationToken(
					updatedUserDetails, null, updatedUserDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updatedAuth);

			return "redirect:/account?emailSuccess=Email+address+added+successfully";
		}
		catch (UserService.UserException e) {
			return "redirect:/account?emailError=" + e.getMessage().replace(" ", "+");
		}
	}

	@PostMapping(path = "/account/remove-email")
	public String removeEmail(@AuthenticationPrincipal ActiveUserDetails userDetails, @RequestParam String email) {
		try {
			ActiveUser activeUser = userDetails.getActiveUser();
			ActiveUser updatedUser = this.userService.removeEmail(activeUser.userId(), email);

			// Update security context with updated user information
			ActiveUserDetails updatedUserDetails = new ActiveUserDetails(updatedUser);
			UsernamePasswordAuthenticationToken updatedAuth = new UsernamePasswordAuthenticationToken(
					updatedUserDetails, null, updatedUserDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updatedAuth);

			return "redirect:/account?emailSuccess=Email+address+removed+successfully";
		}
		catch (UserService.UserException e) {
			return "redirect:/account?emailError=" + e.getMessage().replace(" ", "+");
		}
	}

	@PostMapping(path = "/account/set-primary-email")
	public String setPrimaryEmail(@AuthenticationPrincipal ActiveUserDetails userDetails, @RequestParam String email) {
		try {
			ActiveUser activeUser = userDetails.getActiveUser();
			ActiveUser updatedUser = this.userService.setPrimaryEmail(activeUser.userId(), email);

			// Update security context with updated user information
			ActiveUserDetails updatedUserDetails = new ActiveUserDetails(updatedUser);
			UsernamePasswordAuthenticationToken updatedAuth = new UsernamePasswordAuthenticationToken(
					updatedUserDetails, null, updatedUserDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(updatedAuth);

			return "redirect:/account?emailSuccess=Primary+email+updated+successfully";
		}
		catch (UserService.UserException e) {
			return "redirect:/account?emailError=" + e.getMessage().replace(" ", "+");
		}
	}

	record AddEmailRequest(String email, boolean isPrimary) {
	}

}
