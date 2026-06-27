package com.retailzw.service;

import com.retailzw.enums.UserRole;
import com.retailzw.model.PasswordResetToken;
import com.retailzw.model.SaasAdmin;
import com.retailzw.model.User;
import com.retailzw.repository.PasswordResetTokenRepository;
import com.retailzw.repository.SaasAdminRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final SaasAdminRepository admins;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:https://retailzw.co.zw}")
    private String baseUrl;

    @Transactional
    public void requestShopReset(String email) {
        final String cleanEmail = email == null ? "" : email.trim();
        final List<User> matches = new ArrayList<>(users.findByEmailIgnoreCase(cleanEmail));
        if (matches.isEmpty()) {
            matches.addAll(users.findAllByUsernameForMobileLogin(cleanEmail));
        }
        if (matches.isEmpty()) {
            tenants.findByEmailIgnoreCase(cleanEmail)
                    .ifPresent(tenant -> matches.addAll(users.findByTenantId(tenant.getId())));
        }
        if (matches.isEmpty()) {
            log.info("Password reset requested for unknown shop identifier={}", cleanEmail);
            return;
        }
        User user = matches.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> u.getRole() != null && UserRole.SUPER_ADMIN.equals(u.getRole().getName()))
                .findFirst()
                .orElseGet(() -> matches.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).findFirst().orElse(matches.get(0)));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("Password reset requested for shop user without email userId={} username={}", user.getId(), user.getUsername());
            return;
        }
        String token = createToken(PasswordResetToken.AccountType.SHOP_USER, user.getId(), user.getEmail());
        boolean sent = emailService.sendPasswordReset(user.getEmail(), user.getFirstName(), baseUrl + "/auth/shop/reset?token=" + token);
        log.info("Shop password reset email {} for userId={} email={}", sent ? "sent" : "failed", user.getId(), user.getEmail());
    }

    @Transactional
    public void requestAdminReset(String email) {
        final String cleanEmail = email == null ? "" : email.trim();
        admins.findByEmailIgnoreCase(cleanEmail)
                .or(() -> admins.findByUsernameIgnoreCase(cleanEmail))
                .ifPresentOrElse(admin -> {
            if (!Boolean.TRUE.equals(admin.getIsActive())) return;
            if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                log.info("Password reset requested for admin without email adminId={} username={}", admin.getId(), admin.getUsername());
                return;
            }
            String token = createToken(PasswordResetToken.AccountType.SAAS_ADMIN, admin.getId(), admin.getEmail());
            boolean sent = emailService.sendPasswordReset(admin.getEmail(), admin.getFirstName(), baseUrl + "/auth/admin/reset?token=" + token);
            log.info("Admin password reset email {} for adminId={} email={}", sent ? "sent" : "failed", admin.getId(), admin.getEmail());
        }, () -> log.info("Password reset requested for unknown admin identifier={}", cleanEmail));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Enter a valid reset link and a password with at least 8 characters.");
        }
        PasswordResetToken reset = tokens.findByTokenHash(hash(token))
                .filter(PasswordResetToken::isUsable)
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or expired."));
        if (PasswordResetToken.AccountType.SHOP_USER.equals(reset.getAccountType())) {
            User user = users.findById(reset.getAccountId()).orElseThrow();
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            user.setForcePasswordChange(false);
            users.save(user);
        } else {
            SaasAdmin admin = admins.findById(reset.getAccountId()).orElseThrow();
            admin.setPasswordHash(passwordEncoder.encode(newPassword));
            admins.save(admin);
        }
        reset.setUsedAt(LocalDateTime.now());
        tokens.save(reset);
    }

    private String createToken(PasswordResetToken.AccountType type, Long accountId, String email) {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(PasswordResetToken.builder()
                .tokenHash(hash(raw))
                .accountType(type)
                .accountId(accountId)
                .email(email)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());
        return raw;
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash reset token.", ex);
        }
    }
}
