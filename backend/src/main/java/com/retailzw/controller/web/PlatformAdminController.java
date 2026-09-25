package com.retailzw.controller.web;

import com.retailzw.dto.request.TenantSignUpRequest;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.service.PackageModuleAccessService;
import com.retailzw.service.PlatformCommunicationService;
import com.retailzw.service.EmailService;
import com.retailzw.service.PasswordResetService;
import com.retailzw.service.SmilePayCheckoutService;
import com.retailzw.service.BillingAutomationService;
import com.retailzw.service.TenantProvisioningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PlatformAdminController {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final TenantSubscriptionRepository tenantSubscriptions;
    private final PackageModuleAccessService packageModuleAccess;
    private final BranchRepository branches;
    private final ProductRepository products;
    private final UserRepository users;
    private final SaleRepository sales;
    private final NotificationRepository notifications;
    private final TenantAnnouncementRepository announcements;
    private final TenantChatMessageRepository chatMessages;
    private final PlatformCommunicationService communicationService;
    private final TenantProvisioningService provisioning;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final SmilePayCheckoutService smilePayCheckoutService;
    private final BillingAutomationService billingAutomationService;
    private final com.retailzw.service.TenantDeletionService tenantDeletion;
    private final com.retailzw.service.SupportMessageService chatSending;

    @PostMapping("/admin/tenants/{id}/delete")
    public String deleteTenant(@PathVariable Long id, @RequestParam String confirmation,
                               @RequestParam(defaultValue = "false") boolean acknowledged,
                               java.security.Principal principal, RedirectAttributes redirect) {
        tenantDeletion.delete(id, confirmation, acknowledged, principal.getName());
        redirect.addFlashAttribute("message", "Shop and its database records permanently deleted. External backups and device copies are not erased.");
        return "redirect:/admin/tenants";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/auth/shop/login";
    }

    @GetMapping("/auth/admin/login")
    public String saasLogin() {
        return "auth/admin-login";
    }

    @GetMapping("/auth/admin/forgot")
    public String adminForgotPassword() {
        return "auth/admin-forgot-password";
    }

    @PostMapping("/auth/admin/forgot")
    public String adminForgotPassword(@RequestParam String email, RedirectAttributes redirect) {
        passwordResetService.requestAdminReset(email);
        redirect.addFlashAttribute("message", "An email has been sent to your email.");
        return "redirect:/auth/admin/forgot";
    }

    @GetMapping("/auth/admin/reset")
    public String adminResetPassword(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("resetAction", "/auth/admin/reset");
        return "auth/reset-password";
    }

    @PostMapping("/auth/admin/reset")
    public String adminResetPassword(@RequestParam String token,
                                     @RequestParam String password,
                                     RedirectAttributes redirect) {
        try {
            passwordResetService.resetPassword(token, password);
            redirect.addFlashAttribute("message", "Password reset complete. Sign in with your new password.");
            return "redirect:/auth/admin/login";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/auth/admin/reset?token=" + url(token);
        }
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        addNavigationModel(model);
        model.addAttribute("totalTenants", tenants.count());
        model.addAttribute("activeTenants", tenants.countByStatus(Tenant.TenantStatus.ACTIVE));
        model.addAttribute("pendingTenants", tenants.countByStatus(Tenant.TenantStatus.PENDING));
        List<SaasPlan> dashboardPlans = plans.findAll();
        model.addAttribute("plans", dashboardPlans);
        model.addAttribute("activePlanCount", dashboardPlans.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count());
        model.addAttribute("registrationChart", registrationChart(LocalDate.now()));
        model.addAttribute("tenants", tenants.findAll(PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"))).getContent());
        return "admin/dashboard";
    }

    @GetMapping("/admin/tenants")
    public String tenants(@RequestParam(required = false) String search,
                          @RequestParam(required = false) Tenant.TenantStatus status,
                          @RequestParam(required = false) Long planId,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "25") int size,
                          Model model) {
        addNavigationModel(model);
        List<SaasPlan> planList = plans.findAll();
        Map<Long, SaasPlan> planById = planList.stream().collect(Collectors.toMap(SaasPlan::getId, Function.identity()));
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<Tenant> tenantPage = tenants.findTenants(normalizedSearch, status, planId, pageRequest(page, size));
        Map<Long, String> tenantPlanNames = new HashMap<>();
        List<Long> tenantIds = tenantPage.getContent().stream().map(Tenant::getId).toList();
        Map<Long, Long> tenantUserCounts = countMap(tenantIds.isEmpty() ? List.of() : users.countByTenantIds(tenantIds));
        Map<Long, Long> tenantBranchCounts = countMap(tenantIds.isEmpty() ? List.of() : branches.countByTenantIds(tenantIds));
        Map<Long, Long> tenantProductCounts = countMap(tenantIds.isEmpty() ? List.of() : products.countActiveByTenantIds(tenantIds));
        tenantPage.getContent().forEach(t -> {
            SaasPlan plan = tenantPlan(planById, t);
            tenantPlanNames.put(t.getId(), plan == null ? "No package" : plan.getName());
            tenantUserCounts.putIfAbsent(t.getId(), 0L);
            tenantBranchCounts.putIfAbsent(t.getId(), 0L);
            tenantProductCounts.putIfAbsent(t.getId(), 0L);
        });
        model.addAttribute("tenants", tenantPage.getContent());
        model.addAttribute("tenantPage", tenantPage);
        addPaginationModel(model, "tenant", tenantPage, "/admin/tenants", params("search", search, "status", status, "planId", planId));
        model.addAttribute("plans", planList);
        model.addAttribute("statuses", Tenant.TenantStatus.values());
        model.addAttribute("search", search);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedPlanId", planId);
        model.addAttribute("tenantPlanNames", tenantPlanNames);
        model.addAttribute("tenantUserCounts", tenantUserCounts);
        model.addAttribute("tenantBranchCounts", tenantBranchCounts);
        model.addAttribute("tenantProductCounts", tenantProductCounts);
        model.addAttribute("announcements", announcements.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)));
        model.addAttribute("totalTenants", tenants.count());
        model.addAttribute("activeTenants", tenants.countByStatus(Tenant.TenantStatus.ACTIVE));
        model.addAttribute("pendingTenants", tenants.countByStatus(Tenant.TenantStatus.PENDING));
        model.addAttribute("recentTenantActivity", tenantPage.getContent().stream().limit(5).toList());
        model.addAttribute("signup", new TenantSignUpRequest());
        return "admin/tenants";
    }

    @GetMapping("/admin/tenants/{id}")
    public String tenantDetail(@PathVariable Long id, Model model) {
        addNavigationModel(model);
        Tenant tenant = tenants.findById(id).orElseThrow();
        List<SaasPlan> planList = plans.findAll();
        Map<Long, SaasPlan> planById = planList.stream().collect(Collectors.toMap(SaasPlan::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime yearStart = now.withDayOfYear(1).toLocalDate().atStartOfDay();
        BigDecimal monthRevenueUsd = sales.sumTenantGrandTotalByCurrency(id, monthStart, now, CurrencyCode.USD);
        BigDecimal monthRevenueZwg = sales.sumTenantGrandTotalByCurrency(id, monthStart, now, CurrencyCode.ZWG);
        BigDecimal monthProfit = sales.sumTenantGrossProfit(id, monthStart, now);
        long todaySales = sales.countByTenantIdAndCreatedAtBetween(id, todayStart, now);
        long monthSales = sales.countByTenantIdAndCreatedAtBetween(id, monthStart, now);
        long yearSales = sales.countByTenantIdAndCreatedAtBetween(id, yearStart, now);
        long voidedMonth = sales.countByTenantIdAndStatusAndCreatedAtBetween(id, Sale.SaleStatus.VOIDED, monthStart, now);
        List<TenantChatMessage> chat = chatMessages.findByTenantIdOrderByCreatedAtDesc(id, PageRequest.of(0, 30)).stream()
                .sorted(Comparator.comparing(TenantChatMessage::getCreatedAt))
                .toList();

        model.addAttribute("tenant", tenant);
        model.addAttribute("plans", planList);
        model.addAttribute("statuses", Tenant.TenantStatus.values());
        model.addAttribute("planName", tenantPlan(planById, tenant) == null ? "No package" : tenantPlan(planById, tenant).getName());
        model.addAttribute("branches", branches.findByTenantId(id));
        model.addAttribute("users", users.findByTenantId(id));
        model.addAttribute("subscriptions", tenantSubscriptions.findByTenantId(id));
        model.addAttribute("recentSales", sales.findTop10ByTenantIdOrderByCreatedAtDesc(id));
        model.addAttribute("recentNotifications", notifications.findAll().stream()
                .filter(n -> n.getTenantId().equals(id))
                .sorted(Comparator.comparing(Notification::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList());
        model.addAttribute("chatMessages", chat);
        model.addAttribute("todaySales", todaySales);
        model.addAttribute("monthSales", monthSales);
        model.addAttribute("yearSales", yearSales);
        model.addAttribute("voidedMonth", voidedMonth);
        model.addAttribute("monthRevenueUsd", safeMoney(monthRevenueUsd));
        model.addAttribute("monthRevenueZwg", safeMoney(monthRevenueZwg));
        model.addAttribute("monthProfit", safeMoney(monthProfit));
        model.addAttribute("activeUsers", users.findByTenantIdAndIsActive(id, true).size());
        model.addAttribute("activeProducts", products.countByTenantIdAndIsActiveTrue(id));
        return "admin/tenant-detail";
    }

    @GetMapping("/admin/support")
    public String supportCenter(@RequestParam(required = false) Long tenantId,
                                @RequestParam(defaultValue = "") String search,
                                @RequestParam(defaultValue = "0") int page, Model model) {
        addNavigationModel(model);
        Page<Tenant> inboxPage = tenants.searchTenants(search.strip(), PageRequest.of(Math.max(0, page), 30,
                org.springframework.data.domain.Sort.by("companyName", "id")));
        List<Tenant> tenantList = inboxPage.getContent();
        model.addAttribute("inboxPage", inboxPage);
        model.addAttribute("search", search);
        Long selectedTenantId = tenantId != null
                ? tenantId
                : tenantList.stream().findFirst().map(Tenant::getId).orElse(null);
        Tenant selectedTenant = selectedTenantId == null ? null : tenants.findById(selectedTenantId).orElse(null);

        List<Long> inboxIds = tenantList.stream().map(Tenant::getId).toList();
        Map<Long, TenantChatMessage> lastMessageByTenant = inboxIds.isEmpty() ? Map.of() : chatMessages.latestForTenants(inboxIds).stream()
                .collect(Collectors.toMap(TenantChatMessage::getTenantId, Function.identity()));
        Map<Long, Long> unreadByTenant = new HashMap<>();
        if (!inboxIds.isEmpty()) chatMessages.unreadForTenants(inboxIds)
                .forEach(row -> unreadByTenant.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));

        List<TenantChatMessage> activeChat = selectedTenantId == null
                ? List.of()
                : chatMessages.findByTenantIdOrderByCreatedAtDesc(selectedTenantId, PageRequest.of(0, 60)).stream()
                .sorted(Comparator.comparing(TenantChatMessage::getCreatedAt))
                .toList();

        model.addAttribute("tenants", tenantList);
        model.addAttribute("selectedTenant", selectedTenant);
        model.addAttribute("selectedTenantId", selectedTenantId);
        model.addAttribute("chatMessages", activeChat);
        model.addAttribute("lastMessageByTenant", lastMessageByTenant);
        model.addAttribute("unreadByTenant", unreadByTenant);
        model.addAttribute("totalUnreadSupport", chatMessages.countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType.SHOP));
        return "admin/support";
    }

    @PostMapping("/admin/tenants/{id}/edit")
    public String editTenant(@PathVariable Long id,
                             @ModelAttribute Tenant request,
                             RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(id).orElseThrow();
        tenant.setCompanyName(request.getCompanyName());
        tenant.setEmail(request.getEmail());
        tenant.setPhone(request.getPhone());
        tenant.setCity(request.getCity());
        tenant.setCountry(request.getCountry());
        tenant.setAddress(request.getAddress());
        tenant.setWebsite(request.getWebsite());
        tenant.setRegistrationNumber(request.getRegistrationNumber());
        tenant.setVatNumber(request.getVatNumber());
        tenant.setStatus(request.getStatus() == null ? tenant.getStatus() : request.getStatus());
        tenant.setPlanId(request.getPlanId());
        tenants.save(tenant);
        alignCurrentSubscriptionPlan(tenant.getId(), request.getPlanId());
        packageModuleAccess.syncAndGetEnabledModules(tenant.getId());
        redirect.addFlashAttribute("message", "Tenant profile updated.");
        return "redirect:/admin/tenants/" + id;
    }

    @PostMapping("/admin/tenants/{id}/billing-period")
    public String updateTenantBillingPeriod(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
            RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Shop not found."));
        if (endsOn.isBefore(startsOn)) {
            redirect.addFlashAttribute("message", "Billing end date must be on or after the start date.");
            return "redirect:/admin/tenants#modal-billing-" + id;
        }

        LocalDateTime startsAt = startsOn.atStartOfDay();
        LocalDateTime endsAt = endsOn.plusDays(1).atStartOfDay().minusNanos(1);
        tenant.setSubscriptionStart(startsAt);
        tenant.setSubscriptionEnd(endsAt);
        tenants.save(tenant);

        tenantSubscriptions.findByTenantIdAndStatus(id, TenantSubscription.SubscriptionStatus.ACTIVE)
                .or(() -> tenantSubscriptions.findByTenantIdAndStatus(
                        id, TenantSubscription.SubscriptionStatus.TRIAL))
                .ifPresent(subscription -> {
                    subscription.setStartsAt(startsAt);
                    subscription.setEndsAt(endsAt);
                    tenantSubscriptions.save(subscription);
                });

        redirect.addFlashAttribute("message", "Billing period updated for " + tenant.getCompanyName() + ".");
        return "redirect:/admin/tenants";
    }

    @PostMapping("/admin/tenants/announce")
    public String sendAnnouncement(@RequestParam TenantAnnouncement.AudienceMode audienceMode,
                                   @RequestParam(required = false) List<Long> tenantIds,
                                   @RequestParam(required = false) List<Long> planIds,
                                   @RequestParam(required = false) List<Tenant.TenantStatus> statuses,
                                   @RequestParam String subject,
                                   @RequestParam String message,
                                   @RequestParam(defaultValue = "false") boolean emailEnabled,
                                   RedirectAttributes redirect) {
        TenantAnnouncement sent = communicationService.sendAnnouncement(audienceMode, tenantIds, planIds, statuses,
                subject, message, emailEnabled, "platform");
        redirect.addFlashAttribute("message", "Announcement sent to " + sent.getRecipientCount()
                + " shop(s), " + sent.getNotificationSentCount() + " in-app notification(s), "
                + sent.getEmailSentCount() + " email(s).");
        return "redirect:/admin/tenants";
    }

    @PostMapping("/admin/tenants/{id}/announce")
    public String sendTenantAnnouncement(@PathVariable Long id,
                                         @RequestParam String subject,
                                         @RequestParam String message,
                                         @RequestParam(defaultValue = "false") boolean emailEnabled,
                                         RedirectAttributes redirect) {
        TenantAnnouncement sent = communicationService.sendAnnouncement(TenantAnnouncement.AudienceMode.SELECTED,
                List.of(id), null, null, subject, message, emailEnabled, "platform");
        redirect.addFlashAttribute("message", "Announcement sent: " + sent.getNotificationSentCount()
                + " notification(s), " + sent.getEmailSentCount() + " email(s).");
        return "redirect:/admin/tenants/" + id;
    }

    @PostMapping("/admin/tenants/{id}/chat")
    public String sendTenantChat(@PathVariable Long id,
                                 @RequestParam String message,
                                 RedirectAttributes redirect) {
        validateChat(id, message);
        chatSending.send(id, TenantChatMessage.SenderType.PLATFORM, "Platform Admin", message, java.util.UUID.randomUUID().toString());
        redirect.addFlashAttribute("message", "Chat message sent.");
        return "redirect:/admin/tenants/" + id + "#live-chat";
    }

    @PostMapping("/admin/support/{id}/chat")
    public String sendSupportChat(@PathVariable Long id,
                                  @RequestParam String message,
                                  RedirectAttributes redirect) {
        validateChat(id, message);
        chatSending.send(id, TenantChatMessage.SenderType.PLATFORM, "Platform Support", message, java.util.UUID.randomUUID().toString());
        redirect.addFlashAttribute("message", "Support reply sent.");
        return "redirect:/admin/support?tenantId=" + id;
    }

    @GetMapping("/admin/tenants/{id}/chat/feed")
    @ResponseBody
    public List<Map<String, Object>> tenantChatFeed(@PathVariable Long id) {
        return chatMessages.findByTenantIdOrderByCreatedAtDesc(id, PageRequest.of(0, 30)).stream()
                .sorted(Comparator.comparing(TenantChatMessage::getCreatedAt))
                .map(this::chatPayload)
                .toList();
    }

    @GetMapping("/admin/support/{id}/chat/feed")
    @ResponseBody
    public List<Map<String, Object>> supportChatFeed(@PathVariable Long id) {
        return chatMessages.findByTenantIdOrderByCreatedAtDesc(id, PageRequest.of(0, 60)).stream()
                .sorted(Comparator.comparing(TenantChatMessage::getCreatedAt))
                .map(this::chatPayload)
                .toList();
    }

    @GetMapping("/auth/signup")
    public String signup(Model model) {
        addNavigationModel(model);
        model.addAttribute("plans", plans.findByIsActiveTrue());
        model.addAttribute("businessModules", List.of(BusinessModule.SHOP_MODULE, BusinessModule.GAS_MODULE, BusinessModule.FUEL_MODULE));
        model.addAttribute("signup", new TenantSignUpRequest());
        return "auth/signup";
    }

    @PostMapping("/auth/signup")
    public String signup(@Valid @ModelAttribute("signup") TenantSignUpRequest request, RedirectAttributes redirect) {
        try {
            Tenant tenant = provisioning.signUp(request);
            var checkout = smilePayCheckoutService.createSignupCheckout(tenant.getId());
            return "redirect:/checkout/smilepay/" + checkout.getAccessToken();
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("message", "Shop created, but checkout could not start: " + ex.getMessage());
        }
        return "redirect:/auth/signup";
    }

    @PostMapping("/admin/subscriptions/tenants/{id}/remind")
    public String sendBillingReminder(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            boolean sent = billingAutomationService.sendManualReminder(id);
            redirect.addFlashAttribute("message", sent
                    ? "Payment reminder sent."
                    : "Payment reminder could not be delivered. Check the tenant email and SMTP settings.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/admin/subscriptions#billing-queue";
    }

    @PostMapping("/admin/subscriptions/tenants/{id}/checkout")
    public String openTenantCheckout(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            var checkout = smilePayCheckoutService.createSignupCheckout(id);
            return "redirect:/checkout/smilepay/" + checkout.getAccessToken();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/admin/subscriptions#billing-queue";
        }
    }

    @PostMapping("/admin/tenants/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        Tenant tenant = provisioning.activate(id);
        emailService.sendSupport(tenant.getEmail(), "RetailZW account activated",
                "<p>Your RetailZW account is now active.</p><p>You can sign in at https://retailzw.co.zw/auth/shop/login</p>");
        redirect.addFlashAttribute("message", "Tenant activated.");
        return "redirect:/admin/tenants";
    }

    @PostMapping("/admin/tenants/{id}/suspend")
    public String suspend(@PathVariable Long id, RedirectAttributes redirect) {
        provisioning.suspend(id);
        redirect.addFlashAttribute("message", "Tenant suspended.");
        return "redirect:/admin/tenants";
    }

    @GetMapping("/admin/subscriptions")
    public String subscriptions(@RequestParam(required = false) String search,
                                Model model) {
        addNavigationModel(model);
        List<SaasPlan> allPlans = plans.findAll();
        Map<Long, SaasPlan> planById = allPlans.stream()
                .collect(Collectors.toMap(SaasPlan::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime renewalWindow = now.plusDays(30);
        long expiringSoonCount = tenants.countBySubscriptionEndGreaterThanEqualAndSubscriptionEndLessThan(now, renewalWindow);
        BigDecimal monthlyRevenueUsd = BigDecimal.ZERO;
        BigDecimal monthlyRevenueZwg = BigDecimal.ZERO;
        for (Object[] row : tenants.activeCountsByPlan()) {
            SaasPlan plan = row[0] == null ? null : planById.get(((Number) row[0]).longValue());
            if (plan == null) continue;
            BigDecimal count = BigDecimal.valueOf(((Number) row[1]).longValue());
            monthlyRevenueUsd = monthlyRevenueUsd.add(safeMoney(plan.getPriceUsd()).multiply(count));
            monthlyRevenueZwg = monthlyRevenueZwg.add(safeMoney(plan.getPriceZwg()).multiply(count));
        }
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<SaasPlan> planList = normalizedSearch.isBlank()
                ? allPlans
                : allPlans.stream()
                        .filter(plan -> containsIgnoreCase(plan.getName(), normalizedSearch)
                                || containsIgnoreCase(plan.getCode(), normalizedSearch)
                                || containsIgnoreCase(plan.getDescription(), normalizedSearch))
                        .toList();
        List<SaasPlan> retailPlans = planList.stream()
                .filter(plan -> isExclusiveModulePlan(plan, BusinessModule.SHOP_MODULE))
                .toList();
        List<SaasPlan> gasPlans = planList.stream()
                .filter(plan -> isExclusiveModulePlan(plan, BusinessModule.GAS_MODULE))
                .toList();
        List<SaasPlan> fuelPlans = planList.stream()
                .filter(plan -> isExclusiveModulePlan(plan, BusinessModule.FUEL_MODULE))
                .toList();
        List<SaasPlan> restaurantPlans = planList.stream()
                .filter(plan -> isExclusiveModulePlan(plan, BusinessModule.RESTAURANT_MODULE))
                .toList();
        List<SaasPlan> legacyMixedPlans = planList.stream()
                .filter(plan -> plan.allowedModuleList().size() != 1)
                .toList();
        Map<Long, BusinessModule> planPrimaryModules = allPlans.stream()
                .filter(plan -> plan.getId() != null)
                .collect(Collectors.toMap(
                        SaasPlan::getId,
                        plan -> plan.allowedModuleList().isEmpty()
                                ? BusinessModule.SHOP_MODULE
                                : plan.allowedModuleList().get(0)));

        model.addAttribute("plans", planList);
        model.addAttribute("search", search == null ? "" : search.trim());
        model.addAttribute("retailPlans", retailPlans);
        model.addAttribute("gasPlans", gasPlans);
        model.addAttribute("fuelPlans", fuelPlans);
        model.addAttribute("restaurantPlans", restaurantPlans);
        model.addAttribute("legacyMixedPlans", legacyMixedPlans);
        model.addAttribute("planPrimaryModules", planPrimaryModules);
        model.addAttribute("activePackageCount", allPlans.stream()
                .filter(plan -> Boolean.TRUE.equals(plan.getIsActive()))
                .count());
        model.addAttribute("businessModules", List.of(
                BusinessModule.SHOP_MODULE,
                BusinessModule.GAS_MODULE,
                BusinessModule.FUEL_MODULE,
                BusinessModule.RESTAURANT_MODULE));
        model.addAttribute("expiringSoonCount", expiringSoonCount);
        model.addAttribute("monthlyRevenueUsd", monthlyRevenueUsd);
        model.addAttribute("monthlyRevenueZwg", monthlyRevenueZwg);
        return "admin/subscriptions";
    }

    @PostMapping("/admin/subscriptions")
    public String createSubscription(@RequestParam Long tenantId,
                                     @RequestParam Long planId,
                                     @RequestParam(defaultValue = "ACTIVE") TenantSubscription.SubscriptionStatus status,
                                     @RequestParam(defaultValue = "USD") CurrencyCode currency,
                                     @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startsAt,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endsAt,
                                     @RequestParam(required = false) BigDecimal amountPaid,
                                     @RequestParam(required = false) String paymentReference,
                                     @RequestParam(required = false) String notes,
                                     RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found."));
        SaasPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Package not found."));

        if (TenantSubscription.SubscriptionStatus.ACTIVE.equals(status)) {
            tenantSubscriptions.findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                    .ifPresent(existing -> {
                        existing.setStatus(TenantSubscription.SubscriptionStatus.EXPIRED);
                        existing.setEndsAt(startsAt.minusSeconds(1));
                        tenantSubscriptions.save(existing);
                    });
        }

        TenantSubscription subscription = tenantSubscriptions.save(TenantSubscription.builder()
                .tenantId(tenantId)
                .planId(planId)
                .status(status)
                .currency(currency)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .amountPaid(amountPaid == null ? planPriceForCurrency(plan, currency) : amountPaid)
                .paymentReference(blankToNull(paymentReference))
                .notes(blankToNull(notes))
                .createdBy(0L)
                .build());
        applySubscriptionToTenant(tenant, planId, status, startsAt, endsAt);
        emailService.sendSubscriptionBilling(tenant, plan, subscription);
        redirect.addFlashAttribute("message", "Subscription created for " + tenant.getCompanyName() + ".");
        return "redirect:/admin/subscriptions#billing-queue";
    }

    @PostMapping("/admin/subscriptions/tenants/{tenantId}/edit")
    public String editTenantSubscription(@PathVariable Long tenantId,
                                         @RequestParam Long planId,
                                         @RequestParam(defaultValue = "ACTIVE") TenantSubscription.SubscriptionStatus status,
                                         @RequestParam(defaultValue = "USD") CurrencyCode currency,
                                         @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startsAt,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endsAt,
                                         @RequestParam(required = false) BigDecimal amountPaid,
                                         @RequestParam(required = false) String paymentReference,
                                         @RequestParam(required = false) String notes,
                                         RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new IllegalArgumentException("Tenant not found."));
        SaasPlan plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("Package not found."));
        TenantSubscription subscription = tenantSubscriptions.findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElseGet(() -> TenantSubscription.builder()
                        .tenantId(tenantId)
                        .createdBy(0L)
                        .build());

        subscription.setPlanId(planId);
        subscription.setStatus(status);
        subscription.setCurrency(currency);
        subscription.setStartsAt(startsAt);
        subscription.setEndsAt(endsAt);
        subscription.setAmountPaid(amountPaid == null ? planPriceForCurrency(plan, currency) : amountPaid);
        subscription.setPaymentReference(blankToNull(paymentReference));
        subscription.setNotes(blankToNull(notes));
        tenantSubscriptions.save(subscription);
        applySubscriptionToTenant(tenant, planId, status, startsAt, endsAt);
        emailService.sendSubscriptionBilling(tenant, plan, subscription);
        redirect.addFlashAttribute("message", "Subscription updated for " + tenant.getCompanyName() + ".");
        return "redirect:/admin/subscriptions#billing-queue";
    }

    @PostMapping("/admin/packages")
    public String createPackage(@RequestParam String name,
                                @RequestParam String code,
                                @RequestParam(required = false) String description,
                                @RequestParam BigDecimal priceUsd,
                                @RequestParam BigDecimal priceZwg,
                                @RequestParam Integer maxBranches,
                                @RequestParam Integer maxUsers,
                                @RequestParam Integer maxProducts,
                                @RequestParam(required = false) List<BusinessModule> allowedModules,
                                @RequestParam(defaultValue = "0") Integer maxGasTanks,
                                @RequestParam(defaultValue = "false") boolean allowMixedModules,
                                @RequestParam(defaultValue = "false") boolean gasReconciliationEnabled,
                                RedirectAttributes redirect) {
        BusinessModule packageModule = exclusivePackageModule(allowedModules, redirect);
        if (packageModule == null) {
            return "redirect:/admin/subscriptions";
        }
        if (plans.existsByCode(code)) {
            redirect.addFlashAttribute("message", "A package with code " + code + " already exists.");
            return "redirect:/admin/subscriptions";
        }
        plans.save(com.retailzw.model.SaasPlan.builder()
                .name(name)
                .code(code.toUpperCase())
                .description(description)
                .priceUsd(priceUsd)
                .priceZwg(priceZwg)
                .maxBranches(maxBranches)
                .maxUsers(maxUsers)
                .maxProducts(maxProducts)
                .maxGasTanks(maxGasTanks)
                .allowedModules(packageModule.name())
                .allowMixedModules(false)
                .gasReconciliationEnabled(gasReconciliationEnabled)
                .isActive(true)
                .features("[]")
                .build());
        redirect.addFlashAttribute("message", "Package created.");
        return "redirect:/admin/subscriptions";
    }

    @PostMapping("/admin/packages/{id}/edit")
    public String editPackage(@PathVariable Long id,
                              @RequestParam String name,
                              @RequestParam String code,
                              @RequestParam(required = false) String description,
                              @RequestParam BigDecimal priceUsd,
                              @RequestParam BigDecimal priceZwg,
                              @RequestParam Integer maxBranches,
                              @RequestParam Integer maxUsers,
                              @RequestParam Integer maxProducts,
                              @RequestParam(required = false) List<BusinessModule> allowedModules,
                              @RequestParam(defaultValue = "0") Integer maxGasTanks,
                              @RequestParam(defaultValue = "false") boolean allowMixedModules,
                              @RequestParam(defaultValue = "false") boolean gasReconciliationEnabled,
                              @RequestParam(defaultValue = "false") boolean isActive,
                              RedirectAttributes redirect) {
        BusinessModule packageModule = exclusivePackageModule(allowedModules, redirect);
        if (packageModule == null) {
            return "redirect:/admin/subscriptions";
        }
        SaasPlan plan = plans.findById(id).orElseThrow(() -> new IllegalArgumentException("Package not found."));
        boolean duplicateCode = plans.findByCode(code.toUpperCase())
                .filter(existing -> !existing.getId().equals(id))
                .isPresent();
        if (duplicateCode) {
            redirect.addFlashAttribute("message", "A package with code " + code + " already exists.");
            return "redirect:/admin/subscriptions";
        }
        plan.setName(name);
        plan.setCode(code.toUpperCase());
        plan.setDescription(description);
        plan.setPriceUsd(priceUsd);
        plan.setPriceZwg(priceZwg);
        plan.setMaxBranches(maxBranches);
        plan.setMaxUsers(maxUsers);
        plan.setMaxProducts(maxProducts);
        plan.setMaxGasTanks(maxGasTanks);
        plan.setAllowedModules(packageModule.name());
        plan.setAllowMixedModules(false);
        plan.setGasReconciliationEnabled(gasReconciliationEnabled);
        plan.setIsActive(isActive);
        plans.save(plan);
        packageModuleAccess.reconcileTenantsOnPlan(plan.getId());
        redirect.addFlashAttribute("message", "Package updated.");
        return "redirect:/admin/subscriptions";
    }

    private boolean isExclusiveModulePlan(SaasPlan plan, BusinessModule module) {
        List<BusinessModule> modules = plan.allowedModuleList();
        return modules.size() == 1 && modules.contains(module);
    }

    private BusinessModule exclusivePackageModule(List<BusinessModule> modules,
                                                  RedirectAttributes redirect) {
        List<BusinessModule> selected = modules == null
                ? List.of()
                : modules.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (selected.size() != 1) {
            redirect.addFlashAttribute("message",
                    "Choose exactly one business module. Retail, Gas, and Restaurant packages must remain separate.");
            return null;
        }
        return selected.get(0);
    }

    private void addNavigationModel(Model model) {
        model.addAttribute("supportUnreadCount",
                chatMessages.countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType.SHOP));
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && search != null && value.toLowerCase().contains(search.toLowerCase());
    }

    private Map<String, Object> chatPayload(TenantChatMessage message) {
        return Map.of(
                "id", message.getId(),
                "senderType", message.getSenderType().name(),
                "senderName", message.getSenderName(),
                "message", message.getMessage(),
                "createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString(),
                "readByPlatform", Boolean.TRUE.equals(message.getReadByPlatform()),
                "readByShop", Boolean.TRUE.equals(message.getReadByShop())
        );
    }

    private void validateChat(Long tenantId, String message) {
        if (!tenants.existsById(tenantId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (message == null || message.isBlank() || message.length() > 4000)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Message must contain 1–4000 characters");
    }

    private void applySubscriptionToTenant(Tenant tenant,
                                           Long planId,
                                           TenantSubscription.SubscriptionStatus status,
                                           LocalDateTime startsAt,
                                           LocalDateTime endsAt) {
        tenant.setPlanId(planId);
        tenant.setSubscriptionStart(startsAt);
        tenant.setSubscriptionEnd(endsAt);
        if (TenantSubscription.SubscriptionStatus.ACTIVE.equals(status)
                || TenantSubscription.SubscriptionStatus.TRIAL.equals(status)) {
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        } else if (TenantSubscription.SubscriptionStatus.CANCELLED.equals(status)) {
            tenant.setStatus(Tenant.TenantStatus.CANCELLED);
        }
        tenants.save(tenant);
        packageModuleAccess.syncAndGetEnabledModules(tenant.getId());
    }

    private void alignCurrentSubscriptionPlan(Long tenantId, Long planId) {
        if (planId == null) {
            return;
        }
        TenantSubscription subscription = tenantSubscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .or(() -> tenantSubscriptions.findByTenantIdAndStatus(
                        tenantId, TenantSubscription.SubscriptionStatus.TRIAL))
                .orElse(null);
        if (subscription != null && !planId.equals(subscription.getPlanId())) {
            subscription.setPlanId(planId);
            tenantSubscriptions.save(subscription);
        }
    }

    private String modulesCsv(List<BusinessModule> modules) {
        List<BusinessModule> selected = modules == null || modules.isEmpty()
                ? List.of(BusinessModule.SHOP_MODULE)
                : modules.stream()
                    .filter(module -> !BusinessModule.RESTAURANT_MODULE.equals(module))
                    .distinct()
                    .toList();
        if (selected.isEmpty()) {
            selected = List.of(BusinessModule.SHOP_MODULE);
        }
        return selected.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private BigDecimal planPriceForCurrency(SaasPlan plan, CurrencyCode currency) {
        return CurrencyCode.ZWG.equals(currency) ? safeMoney(plan.getPriceZwg()) : safeMoney(plan.getPriceUsd());
    }

    private Map<String, Object> registrationChart(LocalDate today) {
        LocalDate first = today.withDayOfMonth(1).minusMonths(5);
        Map<Integer, Long> counts = new HashMap<>();
        for (Object[] row : tenants.registrationCounts(first.atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            counts.put(((Number) row[0]).intValue() * 12 + ((Number) row[1]).intValue(), ((Number) row[2]).longValue());
        }
        long max = Math.max(1L, counts.values().stream().mapToLong(Long::longValue).max().orElse(0));
        List<Map<String, Object>> points = new ArrayList<>();
        List<String> coordinates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate month = first.plusMonths(i);
            long count = counts.getOrDefault(month.getYear() * 12 + month.getMonthValue(), 0L);
            int x = 54 + i * 108;
            int y = 170 - (int) (count * 140.0 / max);
            points.add(Map.of("x", x, "y", y, "count", count, "label", month.format(java.time.format.DateTimeFormatter.ofPattern("MMM"))));
            coordinates.add(x + "," + y);
        }
        String line = String.join(" ", coordinates);
        return Map.of("points", points, "line", line, "area", "M " + String.join(" L ", coordinates) + " L 594,180 L 54,180 Z", "max", max);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SaasPlan tenantPlan(Map<Long, SaasPlan> planById, Tenant tenant) {
        return tenant.getPlanId() == null ? null : planById.get(tenant.getPlanId());
    }

    private BigDecimal planPrice(Map<Long, SaasPlan> planById, Tenant tenant, Function<SaasPlan, BigDecimal> priceGetter) {
        SaasPlan plan = tenantPlan(planById, tenant);
        return plan == null ? BigDecimal.ZERO : safeMoney(priceGetter.apply(plan));
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<Long, Long> countMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }

    private String packageInsight(SaasPlan plan, long tenantCount, long activeTenantCount) {
        if (Boolean.FALSE.equals(plan.getIsActive())) {
            return "Hidden from signup";
        }
        if (tenantCount == 0) {
            return "Ready for positioning";
        }
        int share = activeTenantCount == 0 ? 0 : (int) Math.round((tenantCount * 100.0) / activeTenantCount);
        if (share >= 50) {
            return "Core revenue engine";
        }
        if (share >= 20) {
            return "Strong adoption";
        }
        return "Niche growth lane";
    }

    private String billingSignal(Tenant tenant, SaasPlan plan, LocalDateTime now, LocalDateTime renewalWindow) {
        if (plan == null) {
            return "No package";
        }
        if (Tenant.TenantStatus.SUSPENDED.equals(tenant.getStatus())) {
            return "Suspended";
        }
        if (Tenant.TenantStatus.CANCELLED.equals(tenant.getStatus())) {
            return "Cancelled";
        }
        if (Tenant.TenantStatus.PENDING.equals(tenant.getStatus())) {
            return "Activation pending";
        }
        if (tenant.getSubscriptionEnd() != null && tenant.getSubscriptionEnd().isBefore(now)) {
            return "Expired";
        }
        if (tenant.getSubscriptionEnd() != null && tenant.getSubscriptionEnd().isBefore(renewalWindow)) {
            return "Renewal due";
        }
        return "Healthy";
    }

    private String billingSignalClass(Tenant tenant, SaasPlan plan, LocalDateTime now, LocalDateTime renewalWindow) {
        String signal = billingSignal(tenant, plan, now, renewalWindow);
        return switch (signal) {
            case "Healthy" -> "status-active";
            case "Renewal due", "Activation pending" -> "status-pending";
            case "Expired", "Suspended", "Cancelled" -> "status-suspended";
            default -> "status-draft";
        };
    }

    private String billingRecommendation(Tenant tenant, SaasPlan plan, LocalDateTime now, LocalDateTime renewalWindow) {
        if (plan == null) {
            return "Assign a package before billing.";
        }
        if (Tenant.TenantStatus.PENDING.equals(tenant.getStatus())) {
            return "Activate after tenant verification.";
        }
        if (Tenant.TenantStatus.SUSPENDED.equals(tenant.getStatus())) {
            return "Resolve account hold.";
        }
        if (tenant.getSubscriptionEnd() != null && tenant.getSubscriptionEnd().isBefore(now)) {
            return "Renew now to restore billing health.";
        }
        if (tenant.getSubscriptionEnd() != null && tenant.getSubscriptionEnd().isBefore(renewalWindow)) {
            return "Prepare renewal conversation.";
        }
        String code = plan.getCode() == null ? "" : plan.getCode().toUpperCase();
        if ("STARTER".equals(code)) {
            return "Monitor for Growth upgrade.";
        }
        if ("GROWTH".equals(code)) {
            return "Watch for Enterprise conversion.";
        }
        return "Enterprise account stable.";
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), safeSize(size));
    }

    private int safeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private <T> Page<T> pageList(List<T> rows, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = safeSize(size);
        int fromIndex = Math.min(safePage * safeSize, rows.size());
        int toIndex = Math.min(fromIndex + safeSize, rows.size());
        return new PageImpl<>(rows.subList(fromIndex, toIndex), PageRequest.of(safePage, safeSize), rows.size());
    }

    private Map<String, Object> params(Object... keysAndValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            Object value = keysAndValues[i + 1];
            if (value != null && !value.toString().isBlank()) {
                params.put(keysAndValues[i].toString(), value);
            }
        }
        return params;
    }

    private void addPaginationModel(Model model, String prefix, Page<?> page, String path, Map<String, ?> params) {
        model.addAttribute(prefix + "PageLinks", pageLinks(page, path, params));
        model.addAttribute(prefix + "PrevUrl", page.hasPrevious() ? pageUrl(path, params, page.getNumber() - 1, page.getSize()) : null);
        model.addAttribute(prefix + "NextUrl", page.hasNext() ? pageUrl(path, params, page.getNumber() + 1, page.getSize()) : null);
    }

    private List<PageLink> pageLinks(Page<?> page, String path, Map<String, ?> params) {
        int totalPages = page.getTotalPages();
        if (totalPages <= 1) {
            return List.of();
        }
        int current = page.getNumber();
        int start = Math.max(0, current - 3);
        int end = Math.min(totalPages - 1, start + 6);
        start = Math.max(0, end - 6);
        List<PageLink> links = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            links.add(new PageLink(i, String.valueOf(i + 1), pageUrl(path, params, i, page.getSize()), i == current));
        }
        return links;
    }

    private String pageUrl(String path, Map<String, ?> params, int page, int size) {
        StringBuilder url = new StringBuilder(path).append("?page=").append(page).append("&size=").append(size);
        params.forEach((key, value) -> {
            if (value != null && !value.toString().isBlank()) {
                url.append("&").append(key).append("=")
                        .append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
            }
        });
        return url.toString();
    }

    private String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record PageLink(int number, String label, String url, boolean active) {
    }
}
