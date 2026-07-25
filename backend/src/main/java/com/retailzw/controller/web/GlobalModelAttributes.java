package com.retailzw.controller.web;

import com.retailzw.model.SaasAdmin;
import com.retailzw.model.User;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.NotificationRepository;
import com.retailzw.repository.SaasAdminRepository;
import com.retailzw.repository.UserRepository;
import com.retailzw.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.retailzw.controller.web")
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final UserRepository users;
    private final BranchRepository branches;
    private final SaasAdminRepository admins;
    private final NotificationRepository notifications;

    @ModelAttribute
    public void addCurrentUser(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails shopUser) {
            users.findById(shopUser.getUserId())
                    .map(user -> UserMenuDetails.fromShopUser(user, branchLabel(user)))
                    .ifPresent(user -> model.addAttribute("currentUser", user));
            model.addAttribute("unreadNotificationCount", notifications.countByUserIdAndIsReadFalse(shopUser.getUserId()));
            return;
        }

        String username = authentication.getName();
        if (username == null || "anonymousUser".equals(username)) {
            return;
        }
        admins.findByUsername(username)
                .map(UserMenuDetails::fromSaasAdmin)
                .ifPresent(user -> model.addAttribute("currentUser", user));
        model.addAttribute("unreadNotificationCount", 0L);
    }

    private String branchLabel(User user) {
        if (user.getBranchId() == null) {
            return "Head Office";
        }
        return branches.findById(user.getBranchId())
                .filter(branch -> branch.getTenantId().equals(user.getTenantId()))
                .map(branch -> Boolean.TRUE.equals(branch.getIsActive()) ? branch.getName() : branch.getName() + " inactive")
                .orElse("Branch #" + user.getBranchId());
    }

    public static class UserMenuDetails {
        private final String displayName;
        private final String email;
        private final String initials;
        private final String roleLabel;
        private final String branchLabel;

        private UserMenuDetails(String displayName, String email, String initials, String roleLabel, String branchLabel) {
            this.displayName = displayName;
            this.email = email;
            this.initials = initials;
            this.roleLabel = roleLabel;
            this.branchLabel = branchLabel;
        }

        static UserMenuDetails fromShopUser(User user, String branchLabel) {
            String displayName = joinName(user.getFirstName(), user.getLastName(), user.getUsername());
            String role = user.getRole() == null ? "Shop User" : user.getRole().getName().name().replace('_', ' ');
            return new UserMenuDetails(displayName, user.getEmail(), initials(user.getFirstName(), user.getLastName(), user.getUsername()), role, branchLabel);
        }

        static UserMenuDetails fromSaasAdmin(SaasAdmin admin) {
            String displayName = joinName(admin.getFirstName(), admin.getLastName(), admin.getUsername());
            return new UserMenuDetails(displayName, admin.getEmail(), initials(admin.getFirstName(), admin.getLastName(), admin.getUsername()), "System Admin", "Platform");
        }

        private static String joinName(String firstName, String lastName, String fallback) {
            String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
            return name.isBlank() ? fallback : name;
        }

        private static String initials(String firstName, String lastName, String fallback) {
            String first = firstName == null || firstName.isBlank() ? fallback : firstName;
            String second = lastName == null || lastName.isBlank() ? "" : lastName;
            String value = (takeFirst(first) + takeFirst(second)).trim();
            return value.isBlank() ? "U" : value.toUpperCase();
        }

        private static String takeFirst(String value) {
            return value == null || value.isBlank() ? "" : value.substring(0, 1);
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }

        public String getInitials() {
            return initials;
        }

        public String getRoleLabel() {
            return roleLabel;
        }

        public String getBranchLabel() {
            return branchLabel;
        }
    }
}
