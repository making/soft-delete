package com.example.softdelete.admin.web;

import am.ik.pagination.CursorPage;
import am.ik.pagination.CursorPageRequest;
import com.example.softdelete.security.ActiveUserDetails;
import com.example.softdelete.user.ActiveUser;
import com.example.softdelete.user.DeletedUser;
import com.example.softdelete.user.PendingUser;
import com.example.softdelete.user.User;
import com.example.softdelete.user.UserMapper;
import com.example.softdelete.user.UserService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private final UserMapper userMapper;

	private final UserService userService;

	public AdminController(UserMapper userMapper, UserService userService) {
		this.userMapper = userMapper;
		this.userService = userService;
	}

	@GetMapping
	public String showDashboardActiveUser(CursorPageRequest<Long> pageRequest, Model model) {
		CursorPage<ActiveUser, Long> cursorPage = this.userMapper.findActiveUsers(pageRequest);
		List<ActiveUser> users = cursorPage.content();
		AdminDashboard dashboard = new AdminDashboard(users, List.of(), List.of());
		model.addAttribute("dashboard", dashboard);
		model.addAttribute("isActiveTab", true);
		model.addAttribute("isPendingTab", false);
		model.addAttribute("isDeletedTab", false);
		model.addAttribute("size", cursorPage.size());
		if (!users.isEmpty()) {
			if (cursorPage.hasPrevious()) {
				model.addAttribute("firstUserId", users.getFirst().userId());
			}
			if (cursorPage.hasNext()) {
				model.addAttribute("lastUserId", users.getLast().userId());
			}
		}
		return "admin-dashboard";
	}

	@GetMapping(params = "tab=pending")
	public String showDashboardPendingUser(CursorPageRequest<Long> pageRequest, Model model) {
		CursorPage<PendingUser, Long> cursorPage = this.userMapper.findPendingUsers(pageRequest);
		List<PendingUser> users = cursorPage.content();
		AdminDashboard dashboard = new AdminDashboard(List.of(), users, List.of());
		model.addAttribute("dashboard", dashboard);
		model.addAttribute("isActiveTab", false);
		model.addAttribute("isPendingTab", true);
		model.addAttribute("isDeletedTab", false);
		model.addAttribute("size", cursorPage.size());
		if (!users.isEmpty()) {
			if (cursorPage.hasPrevious()) {
				model.addAttribute("firstUserId", users.getFirst().userId());
			}
			if (cursorPage.hasNext()) {
				model.addAttribute("lastUserId", users.getLast().userId());
			}
		}
		return "admin-dashboard";
	}

	@GetMapping(params = "tab=deleted")
	public String showDashboardDeletedUser(CursorPageRequest<Long> pageRequest, Model model) {
		CursorPage<DeletedUser, Long> cursorPage = this.userMapper.findDeletedUsers(pageRequest);
		List<DeletedUser> users = cursorPage.content();
		AdminDashboard dashboard = new AdminDashboard(List.of(), List.of(), users);
		model.addAttribute("dashboard", dashboard);
		model.addAttribute("isActiveTab", false);
		model.addAttribute("isPendingTab", false);
		model.addAttribute("isDeletedTab", true);
		model.addAttribute("size", cursorPage.size());
		if (!users.isEmpty()) {
			if (cursorPage.hasPrevious()) {
				model.addAttribute("firstUserId", users.getFirst().userId());
			}
			if (cursorPage.hasNext()) {
				model.addAttribute("lastUserId", users.getLast().userId());
			}
		}
		return "admin-dashboard";
	}

	@GetMapping("/users/{userId}/ban")
	public String showBanForm(@PathVariable long userId, Model model) {
		User user = this.userMapper.findUser(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		if (!(user instanceof ActiveUser activeUser)) {
			throw new IllegalArgumentException("User is not active: " + userId);
		}

		model.addAttribute("user", activeUser);
		return "admin-ban-form";
	}

	@PostMapping("/users/{userId}/ban")
	public String banUser(@PathVariable long userId, BanRequest banRequest,
			@AuthenticationPrincipal ActiveUserDetails adminDetails, RedirectAttributes redirectAttributes) {
		try {
			this.userService.banUser(userId, adminDetails.getActiveUser().userId(), banRequest.reason());
			redirectAttributes.addFlashAttribute("message", "User has been banned successfully.");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", "Failed to ban user: " + e.getMessage());
		}
		return "redirect:/admin?tab=active";
	}

	@GetMapping("/users/{userId}/promote")
	public String showPromoteForm(@PathVariable long userId, @RequestParam(required = false) String confirm,
			Model model) {
		User user = this.userMapper.findUser(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

		if (!(user instanceof ActiveUser activeUser)) {
			throw new IllegalArgumentException("User is not active: " + userId);
		}

		if ("true".equals(confirm)) {
			model.addAttribute("user", activeUser);
			return "admin-promote-confirm";
		}

		return "redirect:/admin?tab=active";
	}

	@PostMapping(path = "/users/{userId}/promote", params = "cancel")
	public String cancelPromoteToAdmin(@PathVariable long userId) {
		return "redirect:/admin?tab=active";
	}

	@PostMapping("/users/{userId}/promote")
	public String promoteToAdmin(@PathVariable long userId, RedirectAttributes redirectAttributes) {
		try {
			this.userService.promoteToAdmin(userId);
			redirectAttributes.addFlashAttribute("message", "User has been promoted to admin successfully.");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", "Failed to promote user: " + e.getMessage());
		}
		return "redirect:/admin?tab=active";
	}

	@PostMapping(path = "/users/{userId}/ban", params = "cancel")
	public String cancelBanUser(@PathVariable long userId) {
		return "redirect:/admin?tab=active";
	}

	record AdminDashboard(List<ActiveUser> activeUsers, List<PendingUser> pendingUsers,
			List<DeletedUser> deletedUsers) {
	}

	record BanRequest(String reason) {
	}

}