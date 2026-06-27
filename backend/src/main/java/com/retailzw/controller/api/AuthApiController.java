package com.retailzw.controller.api;

import com.retailzw.dto.request.LoginRequest;
import com.retailzw.dto.request.MobileLoginRequest;
import com.retailzw.dto.response.ApiResponse;
import com.retailzw.enums.UserRole;
import com.retailzw.model.Branch;
import com.retailzw.model.Tenant;
import com.retailzw.model.User;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.UserRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.security.CustomUserDetails;
import com.retailzw.security.UserDetailsServiceImpl;
import com.retailzw.security.jwt.JwtUtils;
import com.retailzw.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final BranchRepository branches;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Tenant tenant = tenants.findByTenantCode(request.getTenantCode()).orElseThrow();
        CustomUserDetails user = (CustomUserDetails) userDetailsService.loadUserByUsernameAndTenant(request.getUsername(), tenant.getId());
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        String accessToken = jwtUtils.generateAccessToken(user, user.getTenantId(), user.getBranchId(), user.getRoleName());
        return ApiResponse.success("Signed in", Map.of(
                "accessToken", accessToken,
                "refreshToken", jwtUtils.generateRefreshToken(),
                "tenantId", user.getTenantId(),
                "branchId", user.getBranchId(),
                "username", user.getUsername(),
                "role", user.getRoleName(),
                "tenantCode", tenant.getTenantCode()
        ));
    }

    @PostMapping("/mobile-login")
    public ApiResponse<Map<String, Object>> mobileLogin(@Valid @RequestBody MobileLoginRequest request) {
        List<User> matches = users.findAllByUsernameForMobileLogin(request.getUsername().trim());
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("This username exists in more than one shop. Please make usernames unique before mobile sign in.");
        }

        User user = matches.get(0);
        Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
        if (!Tenant.TenantStatus.ACTIVE.equals(tenant.getStatus())) {
            throw new IllegalArgumentException("This shop is not active.");
        }
        if (!Boolean.TRUE.equals(user.getIsActive()) || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        if (user.getRole() == null || !UserRole.CASHIER.equals(user.getRole().getName())) {
            throw new IllegalArgumentException("Only cashier accounts can sign in on the mobile app.");
        }
        if (user.getBranchId() == null) {
            throw new IllegalArgumentException("This cashier is not assigned to a branch. Please update the user profile first.");
        }
        Branch branch = branches.findById(user.getBranchId())
                .filter(b -> b.getTenantId().equals(user.getTenantId()))
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("This cashier branch is inactive or no longer exists."));

        CustomUserDetails details = new CustomUserDetails(user);
        String accessToken = jwtUtils.generateAccessToken(details, user.getTenantId(), user.getBranchId(), details.getRoleName());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", jwtUtils.generateRefreshToken());
        response.put("tenantId", user.getTenantId());
        response.put("branchId", user.getBranchId());
        response.put("branchCode", branch.getBranchCode());
        response.put("branchName", branch.getName());
        response.put("branchModule", branch.getModuleType());
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("firstName", user.getFirstName());
        response.put("lastName", user.getLastName());
        response.put("role", details.getRoleName());
        response.put("tenantCode", tenant.getTenantCode());
        response.put("tenantBusinessMode", tenant.getBusinessMode());
        response.put("companyName", tenant.getCompanyName());
        response.put("companyEmail", tenant.getEmail());
        response.put("companyPhone", tenant.getPhone());
        response.put("companyAddress", tenant.getAddress());
        response.put("companyCity", tenant.getCity());
        response.put("companyCountry", tenant.getCountry());
        response.put("companyLogoUrl", tenant.getLogoUrl());
        response.put("companyWebsite", tenant.getWebsite());
        response.put("companyRegistrationNumber", tenant.getRegistrationNumber());
        response.put("companyVatNumber", tenant.getVatNumber());
        response.put("receiptFooter", tenant.getReceiptFooter());
        response.put("defaultCurrency", tenant.getDefaultCurrency());
        response.put("secondaryCurrency", tenant.getSecondaryCurrency());
        response.put("defaultTaxRate", tenant.getDefaultTaxRate());
        return ApiResponse.success("Signed in", response);
    }

    /**
     * POST /api/auth/test-email?to=you@example.com
     *
     * Sends a test email from each of the three configured mailboxes
     * (support, billing, notifications) so you can verify SMTP connectivity.
     * This endpoint is public (no JWT required) — restrict it after testing.
     */
    @PostMapping("/test-email")
    public ApiResponse<Map<String, Object>> testEmail(
            @RequestParam(defaultValue = "talentchigwagwa@gmail.com") String to) {

        Map<String, Object> results = new java.util.LinkedHashMap<>();

        boolean supportOk = emailService.sendSupport(to,
                "RetailZW SMTP test — support@retailzw.co.zw",
                "<h2>✅ SMTP is working!</h2>"
                + "<p>This message was sent from <strong>support@retailzw.co.zw</strong> "
                + "via <strong>mail.retailzw.co.zw:587</strong>.</p>"
                + "<p>Sent to: " + to + "</p>");
        results.put("support@retailzw.co.zw", supportOk ? "SENT" : "FAILED");

        boolean billingOk = emailService.sendBilling(to,
                "RetailZW SMTP test — sales@retailzw.co.zw",
                "<h2>✅ SMTP is working!</h2>"
                + "<p>This message was sent from <strong>sales@retailzw.co.zw</strong> "
                + "via <strong>mail.retailzw.co.zw:587</strong>.</p>"
                + "<p>Sent to: " + to + "</p>");
        results.put("sales@retailzw.co.zw", billingOk ? "SENT" : "FAILED");

        boolean notifOk = emailService.sendNotification(to,
                "RetailZW SMTP test — info@retailzw.co.zw",
                "<h2>✅ SMTP is working!</h2>"
                + "<p>This message was sent from <strong>info@retailzw.co.zw</strong> "
                + "via <strong>mail.retailzw.co.zw:587</strong>.</p>"
                + "<p>Sent to: " + to + "</p>");
        results.put("info@retailzw.co.zw", notifOk ? "SENT" : "FAILED");

        boolean allOk = supportOk && billingOk && notifOk;
        return ApiResponse.success(
                allOk ? "All 3 mailboxes sent successfully" : "Some mailboxes failed — check server logs",
                results);
    }
}
