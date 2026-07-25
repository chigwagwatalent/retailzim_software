package com.retailzw.controller.web;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.GasTankStatus;
import com.retailzw.enums.UserRole;
import com.retailzw.dto.request.*;
import com.retailzw.dto.response.ProductImportResult;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.CreditAndChangeService;
import com.retailzw.service.FinanceReportCalculator;
import com.retailzw.service.BillingAccessService;
import com.retailzw.service.GasOperationsService;
import com.retailzw.service.InventoryIntelligenceService;
import com.retailzw.service.NotificationService;
import com.retailzw.service.PasswordResetService;
import com.retailzw.service.PackageModuleAccessService;
import com.retailzw.service.ProductImportService;
import com.retailzw.service.PurchaseOrderService;
import com.retailzw.service.RetailOperationsService;
import com.retailzw.service.ReturnService;
import com.retailzw.service.SmilePayCheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ShopWebController {

    private static final List<String> SALES_MODULES = List.of("sales", "cash", "change", "returns");
    private static final List<String> STOCK_MODULES = List.of("products", "categories", "inventory", "inventory-intelligence", "gas");
    private static final List<String> PEOPLE_MODULES = List.of("customers", "borrowers", "users");
    private static final List<String> FINANCE_MODULES = List.of("suppliers", "purchasing", "reports");
    private static final List<String> SYSTEM_MODULES = List.of("branches", "company", "audit");
    private static final List<String> GAS_MODULES = List.of("gas", "gas-sales", "gas-restocking", "gas-expenses", "gas-tanks", "gas-accounting");
    private static final List<UserRole> USER_MANAGEMENT_ROLES = List.of(UserRole.SUPER_ADMIN, UserRole.ACCOUNTANT, UserRole.CASHIER);
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String> WORKSPACE_MODULES = List.of("reports", "notifications", "audit");

    private static final List<String> MODAL_PLACEHOLDER_MODULES = List.of("notifications", "audit");

    private static final Map<String, String> MODULE_LABELS = Map.ofEntries(
            Map.entry("sales", "Sales History"),
            Map.entry("cash", "Cash Collection"),
            Map.entry("returns", "Returns"),
            Map.entry("products", "Products"),
            Map.entry("categories", "Categories"),
            Map.entry("inventory", "Inventory"),
            Map.entry("inventory-intelligence", "Inventory Intelligence"),
            Map.entry("gas", "Gas Control"),
            Map.entry("gas-sales", "Gas Sales"),
            Map.entry("gas-restocking", "Tank Restocking"),
            Map.entry("gas-expenses", "Gas Expenses"),
            Map.entry("gas-tanks", "Tanks & Prices"),
            Map.entry("gas-accounting", "Gas Accounting"),
            Map.entry("customers", "Customers"),
            Map.entry("borrowers", "Borrower Accounts"),
            Map.entry("change", "Held Change"),
            Map.entry("users", "Users & HR"),
            Map.entry("suppliers", "Suppliers"),
            Map.entry("purchasing", "Purchasing"),
            Map.entry("reports", "Reports"),
            Map.entry("branches", "Branches"),
            Map.entry("company", "Company"),
            Map.entry("notifications", "Notifications"),
            Map.entry("audit", "Audit Trail")
    );

    private static final Map<String, String> MODULE_ICONS = Map.ofEntries(
            Map.entry("sales", "fa-solid fa-receipt"),
            Map.entry("cash", "fa-solid fa-money-bill-wave"),
            Map.entry("returns", "fa-solid fa-rotate-left"),
            Map.entry("products", "fa-solid fa-box"),
            Map.entry("categories", "fa-solid fa-layer-group"),
            Map.entry("inventory", "fa-solid fa-warehouse"),
            Map.entry("inventory-intelligence", "fa-solid fa-brain"),
            Map.entry("gas", "fa-solid fa-gas-pump"),
            Map.entry("gas-sales", "fa-solid fa-scale-balanced"),
            Map.entry("gas-restocking", "fa-solid fa-truck-droplet"),
            Map.entry("gas-expenses", "fa-solid fa-receipt"),
            Map.entry("gas-tanks", "fa-solid fa-fire-flame-simple"),
            Map.entry("gas-accounting", "fa-solid fa-chart-line"),
            Map.entry("customers", "fa-solid fa-user-tag"),
            Map.entry("borrowers", "fa-solid fa-hand-holding-dollar"),
            Map.entry("change", "fa-solid fa-coins"),
            Map.entry("users", "fa-solid fa-user-gear"),
            Map.entry("suppliers", "fa-solid fa-truck-field"),
            Map.entry("purchasing", "fa-solid fa-file-invoice-dollar"),
            Map.entry("reports", "fa-solid fa-chart-pie"),
            Map.entry("branches", "fa-solid fa-store"),
            Map.entry("company", "fa-solid fa-building"),
            Map.entry("notifications", "fa-solid fa-bell"),
            Map.entry("audit", "fa-solid fa-shield-halved")
    );

    private static final Map<String, String> MODULE_URLS = Map.ofEntries(
            Map.entry("gas", "/shop/gas"),
            Map.entry("gas-sales", "/shop/gas/sales"),
            Map.entry("gas-restocking", "/shop/gas/restocking"),
            Map.entry("gas-expenses", "/shop/gas/expenses"),
            Map.entry("gas-tanks", "/shop/gas/tanks"),
            Map.entry("gas-accounting", "/shop/gas/accounting")
    );

    private final CurrentUserService current;
    private final RetailOperationsService operations;
    private final FinanceReportCalculator financeReportCalculator;
    private final ReturnService returnService;
    private final PurchaseOrderService purchaseOrderService;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final UnitOfMeasureRepository uoms;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final BranchRepository branches;
    private final UserRepository users;
    private final RoleRepository roles;
    private final InventoryRepository inventory;
    private final InventoryAdjustmentRepository adjustments;
    private final InventoryTransactionRepository inventoryTransactions;
    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository purchaseOrderItems;
    private final GoodsReceivedNoteRepository goodsReceivedNotes;
    private final SaleRepository sales;
    private final CashSessionRepository cashSessions;
    private final CashDrawerRepository drawers;
    private final CashMovementRepository cashMovements;
    private final SalePaymentRepository salePayments;
    private final ReturnRepository returns;
    private final NotificationRepository notifications;
    private final TenantChatMessageRepository chatMessages;
    private final NotificationService notificationService;
    private final PasswordResetService passwordResetService;
    private final TenantSubscriptionRepository tenantSubscriptions;
    private final SubscriptionPaymentRepository subscriptionPayments;
    private final SmilePayCheckoutService smilePayCheckoutService;
    private final BillingAccessService billingAccessService;
    private final PackageModuleAccessService packageModuleAccessService;
    private final CreditAndChangeService creditAndChangeService;
    private final InventoryIntelligenceService inventoryIntelligenceService;
    private final ProductImportService productImportService;
    private final BorrowerRepository borrowerRepository;
    private final HeldChangeRepository heldChangeRepository;
    private final InventoryLotRepository inventoryLots;
    private final StockVarianceInvestigationRepository varianceInvestigations;
    private final GasOperationsService gasOperations;

    @GetMapping("/auth/shop/login")
    public String login() {
        return "auth/shop-login";
    }

    @GetMapping("/auth/shop/forgot")
    public String forgotPassword() {
        return "auth/forgot-password";
    }

    @PostMapping("/auth/shop/forgot")
    public String forgotPassword(@RequestParam String email, RedirectAttributes redirect) {
        passwordResetService.requestShopReset(email);
        redirect.addFlashAttribute("message", "If that email exists, a reset link has been sent.");
        return "redirect:/auth/shop/forgot";
    }

    @GetMapping("/auth/shop/reset")
    public String resetPassword(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("resetAction", "/auth/shop/reset");
        return "auth/reset-password";
    }

    @PostMapping("/auth/shop/reset")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String password,
                                RedirectAttributes redirect) {
        try {
            passwordResetService.resetPassword(token, password);
            redirect.addFlashAttribute("message", "Password reset complete. Sign in with your new password.");
            return "redirect:/auth/shop/login";
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/auth/shop/reset?token=" + url(token);
        }
    }

    @GetMapping("/shop/dashboard")
    public String dashboard(Model model) {
        Long tenantId = current.tenantId();
        Long branchId = activeBranch();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDateTime yesterdayStart = start.minusDays(1);
        LocalDateTime weekStart = start.minusDays(6);
        List<Branch> activeBranches = branches.findByTenantIdAndIsActiveTrue(tenantId);
        List<Inventory> branchStock = inventory.findByTenantIdAndBranchId(tenantId, branchId);
        List<Inventory> lowStockRows = inventory.findLowStockItems(tenantId, branchId);
        List<User> tenantUsers = users.findByTenantId(tenantId);
        Map<Long, Product> productById = products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        DashboardMoney todayTotals = moneyTotals(tenantId, branchId, start, end);
        DashboardMoney yesterdayTotals = moneyTotals(tenantId, branchId, yesterdayStart, start);
        long todayTransactions = sales.countByTenantIdAndBranchIdAndStatusAndCreatedAtBetween(
                tenantId, branchId, Sale.SaleStatus.COMPLETED, start, end);
        long openCashSessions = cashSessions.findAllByTenantIdAndBranchIdAndStatus(
                tenantId, branchId, CashSession.SessionStatus.OPEN).size();
        BigDecimal openCashUsd = salePayments.sumCashCollected(tenantId, branchId, CurrencyCode.USD, start, end);
        BigDecimal openCashZwg = salePayments.sumCashCollected(tenantId, branchId, CurrencyCode.ZWG, start, end);
        TenantSubscription subscription = tenantSubscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);
        BigDecimal billingOutstanding = subscription == null
                ? BigDecimal.ZERO
                : nvl(subscription.getAmount()).subtract(nvl(subscription.getAmountPaid())).max(BigDecimal.ZERO);
        BigDecimal availableGasKg = dashboardAvailableGasKg(tenantId, activeBranches);

        model.addAttribute("module", "dashboard");
        addNavigationModel(model);
        model.addAttribute("productCount", products.countByTenantIdAndIsActiveTrue(tenantId));
        model.addAttribute("customerCount", customers.countByTenantId(tenantId));
        model.addAttribute("branchCount", activeBranches.size());
        model.addAttribute("activeCashierCount", tenantUsers.stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .filter(user -> hasRole(user, UserRole.CASHIER))
                .count());
        model.addAttribute("todaySales", todayTotals);
        model.addAttribute("yesterdaySales", yesterdayTotals);
        model.addAttribute("salesVsYesterday", percentChange(todayTotals.usdRaw(), yesterdayTotals.usdRaw()));
        model.addAttribute("todayTransactions", todayTransactions);
        model.addAttribute("inventoryHealth", inventoryHealth(branchStock.size(), lowStockRows.size()));
        model.addAttribute("lowStockCount", lowStockRows.size());
        model.addAttribute("lowStockCategoryCount", lowStockCategoryCount(lowStockRows, productById));
        model.addAttribute("cashSessionCount", cashSessions.findByTenantIdAndBranchId(tenantId, branchId, PageRequest.of(0, 100)).getTotalElements());
        model.addAttribute("openCashSessions", openCashSessions);
        model.addAttribute("openCashUsd", money(openCashUsd));
        model.addAttribute("openCashZwg", money(openCashZwg));
        model.addAttribute("billingOutstanding", money(billingOutstanding));
        model.addAttribute("dashboardGasKg", quantity(availableGasKg) + " kg");
        model.addAttribute("recentSales", dashboardRecentSales(tenantId, branchId));
        model.addAttribute("lowStock", dashboardLowStock(lowStockRows, productById));
        model.addAttribute("branchPerformance", dashboardBranchPerformance(tenantId, activeBranches, start, end));
        model.addAttribute("salesChart", dashboardSalesChart(tenantId, branchId, weekStart, today));
        return "shop/dashboard";
    }

    @GetMapping("/shop/billing")
    public String billing(
            @RequestParam(defaultValue = "0") int billingPage,
            Model model) {
        Long tenantId = current.tenantId();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SaasPlan plan = tenant.getPlanId() == null
                ? null
                : plans.findById(tenant.getPlanId()).orElse(null);
        model.addAttribute("module", "billing");
        model.addAttribute("tenant", tenant);
        model.addAttribute("plan", plan);
        model.addAttribute("availablePlans", plans.findByIsActiveTrue());
        BillingAccessService.BillingAccess billingAccess = billingAccessService.evaluateAndUpdate(tenantId);
        model.addAttribute("billingAccessLocked", billingAccess.locked());
        model.addAttribute("billingLockMessage", billingAccess.message());
        model.addAttribute("billingOverdueDays", billingAccess.overdueDays());
        model.addAttribute("subscription", tenantSubscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null));
        List<TenantSubscription> subscriptionHistory = tenantSubscriptions.findByTenantId(tenantId).stream()
                .sorted(java.util.Comparator.comparing(TenantSubscription::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
        Page<SubscriptionPayment> paymentHistory = subscriptionPayments
                .findByTenantIdOrderByConfirmedAtDesc(
                        tenantId,
                        PageRequest.of(Math.max(0, billingPage), DEFAULT_PAGE_SIZE));
        model.addAttribute("subscriptionHistory", subscriptionHistory);
        model.addAttribute("subscriptionPlanNames", java.util.stream.Stream.concat(
                        subscriptionHistory.stream().map(TenantSubscription::getPlanId),
                        paymentHistory.stream().map(SubscriptionPayment::getPlanId))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toMap(
                        java.util.function.Function.identity(),
                        planId -> plans.findById(planId).map(SaasPlan::getName).orElse("Package " + planId))));
        model.addAttribute("paymentHistory", paymentHistory);
        addNavigationModel(model);
        return "shop/billing";
    }

    @PostMapping("/shop/billing/pay")
    public String paySubscription(@RequestParam(required = false) Long planId,
                                   RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(current.tenantId()).orElseThrow();
        Long selectedPlanId = planId == null ? tenant.getPlanId() : planId;
        if (selectedPlanId == null) {
            redirect.addFlashAttribute("message", "Choose a package before renewal.");
            return "redirect:/shop/billing";
        }
        return "redirect:/shop/billing/renew?planId=" + selectedPlanId;
    }

    @GetMapping("/shop/billing/renew")
    public String renewalBuilder(
            @RequestParam(required = false) Long planId,
            Model model) {
        Long tenantId = current.tenantId();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        Long selectedPlanId = planId == null ? tenant.getPlanId() : planId;
        SaasPlan selectedPlan = selectedPlanId == null
                ? plans.findByIsActiveTrue().stream().findFirst().orElse(null)
                : plans.findById(selectedPlanId)
                        .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                        .orElse(null);
        if (selectedPlan == null) {
            throw new IllegalArgumentException("No active subscription package is available.");
        }
        List<Integer> allowedMonths = smilePayCheckoutService.allowedRenewalMonths(selectedPlan);
        Map<Integer, BigDecimal> renewalTotals = allowedMonths.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        months -> smilePayCheckoutService.renewalAmount(selectedPlan, months),
                        (left, right) -> left,
                        LinkedHashMap::new));
        model.addAttribute("module", "billing");
        model.addAttribute("tenant", tenant);
        model.addAttribute("availablePlans", plans.findByIsActiveTrue());
        model.addAttribute("selectedPlan", selectedPlan);
        model.addAttribute("allowedMonths", allowedMonths);
        model.addAttribute("renewalTotals", renewalTotals);
        model.addAttribute("subscription", tenantSubscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null));
        addNavigationModel(model);
        return "shop/billing-renewal";
    }

    @PostMapping("/shop/billing/renew")
    public String createRenewal(
            @RequestParam Long planId,
            @RequestParam int billingMonths,
            RedirectAttributes redirect) {
        try {
            SmilePayCheckout checkout = smilePayCheckoutService.createRenewalCheckout(
                    current.tenantId(),
                    planId,
                    billingMonths,
                    current.userId());
            return "redirect:/shop/billing/renew/" + checkout.getOrderReference();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/shop/billing/renew?planId=" + planId;
        }
    }

    @GetMapping("/shop/billing/renew/{orderReference}")
    public String renewalCheckout(
            @PathVariable String orderReference,
            Model model) {
        var view = smilePayCheckoutService.renewalCheckoutView(orderReference, current.tenantId());
        model.addAttribute("module", "billing");
        model.addAttribute("checkout", view.checkout());
        model.addAttribute("tenant", view.tenant());
        model.addAttribute("selectedPlan", view.plan());
        model.addAttribute("planModules", view.plan().allowedModuleList());
        model.addAttribute("paymentMethods", paymentMethods());
        addNavigationModel(model);
        return "shop/billing-renewal";
    }

    @PostMapping("/shop/billing/renew/{orderReference}/initiate")
    public Object initiateRenewal(
            @PathVariable String orderReference,
            @RequestParam SmilePayCheckout.PaymentMethod paymentMethod,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String pan,
            @RequestParam(required = false) String expMonth,
            @RequestParam(required = false) String expYear,
            @RequestParam(required = false) String securityCode,
            RedirectAttributes redirect) {
        try {
            smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
            Map<String, String> card = new LinkedHashMap<>();
            card.put("firstName", firstName);
            card.put("lastName", lastName);
            card.put("pan", pan);
            card.put("expMonth", expMonth);
            card.put("expYear", expYear);
            card.put("securityCode", securityCode);
            var result = smilePayCheckoutService.initiateExpress(
                    orderReference, paymentMethod, mobile, card);
            if (result.redirectHtml() != null && !result.redirectHtml().isBlank()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(result.redirectHtml());
            }
            if (result.requiresOtp()) {
                redirect.addFlashAttribute("message",
                        result.message() == null ? "Enter the OTP sent to your phone." : result.message());
                return "redirect:/shop/billing/renew/" + orderReference + "?otp=true";
            }
            redirect.addFlashAttribute("message",
                    result.message() == null
                            ? "Payment started. Approve it and keep this page open."
                            : result.message());
            return "redirect:/shop/billing/renew/" + orderReference + "?pending=true";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/shop/billing/renew/" + orderReference;
        }
    }

    @PostMapping("/shop/billing/renew/{orderReference}/confirm-otp")
    public String confirmRenewalOtp(
            @PathVariable String orderReference,
            @RequestParam String otp,
            RedirectAttributes redirect) {
        try {
            smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
            var result = smilePayCheckoutService.confirmOtp(orderReference, otp);
            if (SmilePayCheckout.CheckoutStatus.PAID.equals(result.checkout().getStatus())) {
                redirect.addFlashAttribute("message", "Renewal payment confirmed.");
                return "redirect:/shop/billing?renewal=success";
            }
            redirect.addFlashAttribute("message", "OTP accepted. We are confirming the payment.");
            return "redirect:/shop/billing/renew/" + orderReference + "?pending=true";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/shop/billing/renew/" + orderReference + "?otp=true";
        }
    }

    @GetMapping("/shop/billing/renew/{orderReference}/status")
    @ResponseBody
    public Map<String, Object> renewalStatus(@PathVariable String orderReference) {
        smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
        SmilePayCheckout checkout = smilePayCheckoutService.checkoutState(orderReference);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", checkout.getStatus());
        payload.put("providerStatus", checkout.getProviderStatus());
        payload.put("paid", SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus()));
        payload.put("redirectUrl", SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())
                ? "/shop/billing?renewal=success"
                : null);
        return payload;
    }

    @GetMapping("/shop/billing/renew/{orderReference}/recheck")
    public String recheckRenewal(
            @PathVariable String orderReference,
            RedirectAttributes redirect) {
        try {
            smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
            SmilePayCheckout checkout = smilePayCheckoutService.verifyAndApply(orderReference);
            if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
                redirect.addFlashAttribute("message", "Renewal confirmed and access extended.");
                return "redirect:/shop/billing?renewal=success";
            }
            redirect.addFlashAttribute("message",
                    SmilePayCheckout.CheckoutStatus.PROCESSING.equals(checkout.getStatus())
                            ? "The payment is still being verified."
                            : "Payment has not been confirmed. Retry only if no money was deducted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", "Payment status is temporarily unavailable.");
        }
        return "redirect:/shop/billing/renew/" + orderReference;
    }

    @GetMapping("/shop/billing/renew/return")
    public String renewalReturn(
            @RequestParam String orderReference,
            RedirectAttributes redirect) {
        smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
        SmilePayCheckout checkout = smilePayCheckoutService.verifyAndApply(orderReference);
        if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            redirect.addFlashAttribute("message", "Renewal payment received and access extended.");
            return "redirect:/shop/billing?renewal=success";
        }
        redirect.addFlashAttribute("message", "Payment is still being confirmed.");
        return "redirect:/shop/billing/renew/" + orderReference + "?pending=true";
    }

    @GetMapping("/shop/billing/renew/cancel")
    public String cancelRenewal(
            @RequestParam String orderReference,
            RedirectAttributes redirect) {
        smilePayCheckoutService.requireRenewalCheckout(orderReference, current.tenantId());
        smilePayCheckoutService.cancel(orderReference);
        redirect.addFlashAttribute("message", "Renewal payment cancelled.");
        return "redirect:/shop/billing/renew/" + orderReference;
    }

    @GetMapping("/shop/{module}")
    public String module(@PathVariable String module,
                         @RequestParam(required = false) String search,
                         @RequestParam(required = false) Long branchId,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Long cashierId,
                         @RequestParam(required = false) Long collectShiftId,
                         @RequestParam(required = false) Long closeShiftId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "25") int size,
                         Model model) {
        Long tenantId = current.tenantId();
        Long activeBranchId = selectedBranch(branchId);
        int currentPage = safePage(page);
        int pageSize = safeSize(size);
        String activeModule = safeModule(module);
        if ("gas".equals(activeModule) && !packageModuleAccessService.hasGas(tenantId)) {
            return "redirect:/shop/billing";
        }
        if (packageModuleAccessService.retailWebModules().contains(activeModule)
                && !packageModuleAccessService.hasRetailShop(tenantId)) {
            return "redirect:/shop/billing";
        }
        model.addAttribute("module", activeModule);
        addNavigationModel(model);
        model.addAttribute("workspaceModules", WORKSPACE_MODULES);
        model.addAttribute("modalPlaceholderModules", MODAL_PLACEHOLDER_MODULES);
        model.addAttribute("selectedBranchId", activeBranchId);
        model.addAttribute("branchById", branchById(tenantId));
        model.addAttribute("selectedBranchName", branchById(tenantId).get(activeBranchId));
        model.addAttribute("products", products.findByTenantIdAndIsActiveTrue(tenantId));
        model.addAttribute("categories", categories.findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(tenantId));
        model.addAttribute("uoms", uoms.findByTenantId(tenantId));
        model.addAttribute("customers", customers.findByTenantId(tenantId, PageRequest.of(0, 100)).getContent());
        model.addAttribute("suppliers", suppliers.findByTenantId(tenantId, PageRequest.of(0, 100)).getContent());
        model.addAttribute("branches", branches.findByTenantIdAndIsActiveTrue(tenantId));
        model.addAttribute("users", users.findByTenantId(tenantId));
        model.addAttribute("roles", roles.findAll());
        model.addAttribute("inventory", inventory.findByTenantIdAndBranchId(tenantId, activeBranchId));
        model.addAttribute("adjustments", adjustments.findByTenantIdAndBranchId(tenantId, activeBranchId, PageRequest.of(0, 50)).getContent());
        model.addAttribute("orders", purchaseOrders.findByTenantIdAndBranchId(tenantId, activeBranchId, PageRequest.of(0, 50)).getContent());
        model.addAttribute("sales", operations.recentSales(tenantId, activeBranchId));
        model.addAttribute("drawers", drawers.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, activeBranchId));
        model.addAttribute("sessions", cashSessions.findByTenantIdAndBranchId(tenantId, activeBranchId, PageRequest.of(0, 50)).getContent());
        model.addAttribute("notifications", notifications.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, current.userId(), PageRequest.of(0, 50)));
        model.addAttribute("chatMessages", chatMessages.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 30)).stream()
                .sorted(java.util.Comparator.comparing(TenantChatMessage::getCreatedAt))
                .toList());
        model.addAttribute("pageTitle", title(activeModule));
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("pageSize", pageSize);
        if ("products".equals(activeModule)) {
            addProductManagementModel(model, tenantId, activeBranchId, search, categoryId, currentPage, pageSize);
            return "shop/products";
        }
        if ("categories".equals(activeModule)) {
            addCategoryManagementModel(model, tenantId, search, currentPage, pageSize);
            return "shop/categories";
        }
        if ("inventory".equals(activeModule)) {
            addInventoryManagementModel(model, tenantId, activeBranchId, search, status, currentPage, pageSize);
            return "shop/inventory";
        }
        if ("inventory-intelligence".equals(activeModule)) {
            var transferRows = inventoryIntelligenceService.transfers(tenantId);
            var stocktakeRows = inventoryIntelligenceService.stocktakes(tenantId, activeBranchId);
            model.addAttribute("transfers", transferRows);
            model.addAttribute("stocktakes", stocktakeRows);
            model.addAttribute("stocktakeItems", stocktakeRows.stream().collect(java.util.stream.Collectors.toMap(
                    StocktakeSession::getId,
                    session -> inventoryIntelligenceService.stocktakeItems(session.getId())
            )));
            model.addAttribute("inventoryLots", inventoryIntelligenceService.lots(tenantId, activeBranchId));
            model.addAttribute("varianceInvestigations", inventoryIntelligenceService.investigations(tenantId, activeBranchId));
            model.addAttribute("supplierPrices", inventoryIntelligenceService.supplierPrices(tenantId));
            model.addAttribute("productById", products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                    .collect(java.util.stream.Collectors.toMap(Product::getId, Function.identity())));
            model.addAttribute("supplierById", suppliers.findByTenantIdAndIsActiveTrue(tenantId).stream()
                    .collect(java.util.stream.Collectors.toMap(Supplier::getId, Function.identity())));
            return "shop/inventory-intelligence";
        }
        if ("gas".equals(activeModule)) {
            addGasManagementModel(model, tenantId, activeBranchId);
            return "shop/gas";
        }
        if ("users".equals(activeModule)) {
            addUserManagementModel(model, tenantId, activeBranchId, currentPage, pageSize);
            return "shop/users";
        }
        if ("customers".equals(activeModule)) {
            addCustomerManagementModel(model, tenantId, search, currentPage, pageSize);
            return "shop/customers";
        }
        if ("borrowers".equals(activeModule)) {
            var borrowerPage = creditAndChangeService.borrowers(tenantId, search, currentPage, pageSize);
            model.addAttribute("borrowerPage", borrowerPage);
            model.addAttribute("borrowerAccounts", borrowerPage.getContent());
            model.addAttribute("borrowerSearch", search);
            model.addAttribute("borrowerTransactions", borrowerPage.getContent().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Borrower::getId,
                            borrower -> creditAndChangeService.borrowerTransactions(tenantId, borrower.getId())
                    )));
            addPaginationModel(model, "borrower", borrowerPage, "/shop/borrowers", params("search", search));
            return "shop/borrowers";
        }
        if ("change".equals(activeModule)) {
            HeldChange.Status changeStatus = parseEnum(status, HeldChange.Status.class);
            LocalDateTime fromDate = parseDate(from, false);
            LocalDateTime toDate = parseDate(to, true);
            var changePage = creditAndChangeService.changeRecords(
                    tenantId, changeStatus, search, fromDate, toDate, currentPage, pageSize);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime tomorrowStart = todayStart.plusDays(1);
            model.addAttribute("changePage", changePage);
            model.addAttribute("changeRecords", changePage.getContent());
            model.addAttribute("changeSearch", search);
            model.addAttribute("changeStatus", status);
            model.addAttribute("changeDateFrom", from);
            model.addAttribute("changeDateTo", to);
            model.addAttribute("openChangeCount", creditAndChangeService.changeRecordCount(tenantId, HeldChange.Status.OPEN));
            model.addAttribute("filteredChangeCount", changePage.getTotalElements());
            model.addAttribute("heldChangeUsd", creditAndChangeService.sumChangeRecords(
                    tenantId, HeldChange.Status.OPEN, null, null, null, CurrencyCode.USD));
            model.addAttribute("heldChangeZwg", creditAndChangeService.sumChangeRecords(
                    tenantId, HeldChange.Status.OPEN, null, null, null, CurrencyCode.ZWG));
            model.addAttribute("collectedTodayUsd", creditAndChangeService.sumChangeRecords(
                    tenantId, HeldChange.Status.COLLECTED, null, todayStart, tomorrowStart, CurrencyCode.USD));
            model.addAttribute("collectedTodayZwg", creditAndChangeService.sumChangeRecords(
                    tenantId, HeldChange.Status.COLLECTED, null, todayStart, tomorrowStart, CurrencyCode.ZWG));
            model.addAttribute("filteredChangeUsd", creditAndChangeService.sumChangeRecords(
                    tenantId, changeStatus, search, fromDate, toDate, CurrencyCode.USD));
            model.addAttribute("filteredChangeZwg", creditAndChangeService.sumChangeRecords(
                    tenantId, changeStatus, search, fromDate, toDate, CurrencyCode.ZWG));
            addPaginationModel(model, "change", changePage, "/shop/change", params(
                    "search", search,
                    "from", from,
                    "to", to,
                    "status", status
            ));
            return "shop/change";
        }
        if ("suppliers".equals(activeModule)) {
            addSupplierManagementModel(model, tenantId, search, currentPage, pageSize);
            return "shop/suppliers";
        }
        if ("branches".equals(activeModule)) {
            addBranchManagementModel(model, tenantId, currentPage, pageSize);
            return "shop/branches";
        }
        if ("sales".equals(activeModule)) {
            addSalesHistoryModel(model, tenantId, activeBranchId, search, from, to, status, cashierId, currentPage, pageSize);
            return "shop/sales";
        }
        if ("cash".equals(activeModule)) {
            addCashDrawerModel(model, tenantId, activeBranchId, collectShiftId != null ? collectShiftId : closeShiftId, currentPage, pageSize);
            return "shop/cash";
        }
        if ("returns".equals(activeModule)) {
            addReturnsModel(model, tenantId, activeBranchId, search, from, to, currentPage, pageSize);
            return "shop/returns";
        }
        if ("purchasing".equals(activeModule)) {
            addPurchasingModel(model, tenantId, activeBranchId, search, status, currentPage, pageSize);
            return "shop/purchasing";
        }
        if ("reports".equals(activeModule)) {
            addReportsModel(model, tenantId, activeBranchId, from, to);
            return "shop/reports";
        }
        if ("company".equals(activeModule)) {
            addCompanyProfileModel(model, tenantId);
            return "shop/company";
        }
        return "shop/module";
    }

    @GetMapping("/shop/gas/{section}")
    public String gasSection(@PathVariable String section,
                             @RequestParam(required = false) Long branchId,
                             Model model) {
        Long tenantId = current.tenantId();
        if (!packageModuleAccessService.hasGas(tenantId)) {
            return "redirect:/shop/billing";
        }
        Long activeBranchId = selectedBranch(branchId);
        String activeModule = switch (section) {
            case "sales" -> "gas-sales";
            case "restocking" -> "gas-restocking";
            case "expenses" -> "gas-expenses";
            case "tanks" -> "gas-tanks";
            case "accounting" -> "gas-accounting";
            default -> "gas";
        };
        model.addAttribute("module", activeModule);
        model.addAttribute("pageTitle", title(activeModule));
        model.addAttribute("selectedBranchId", activeBranchId);
        model.addAttribute("selectedBranchName", branchById(tenantId).get(activeBranchId));
        model.addAttribute("branchById", branchById(tenantId));
        addNavigationModel(model);
        addGasManagementModel(model, tenantId, activeBranchId);
        return switch (activeModule) {
            case "gas-sales" -> "shop/gas-sales";
            case "gas-restocking" -> "shop/gas-restocking";
            case "gas-expenses" -> "shop/gas-expenses";
            case "gas-tanks" -> "shop/gas-tanks";
            case "gas-accounting" -> "shop/gas-accounting";
            default -> "shop/gas";
        };
    }

    @PostMapping("/shop/products")
    public String createProduct(@Valid @ModelAttribute CreateProductRequest request, RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(request.getBranchId());
        request.setBranchId(targetBranch);
        try {
            operations.createProduct(current.tenantId(), request, current.userId());
            redirect.addFlashAttribute("message", "Product saved and tied to " + branchById(current.tenantId()).get(targetBranch) + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping(value = "/shop/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String importProducts(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) Long branchId,
                                 RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            ProductImportResult result = productImportService.importProducts(current.tenantId(), targetBranch, current.userId(), file);
            String message = "Imported products: " + result.getCreatedProducts() + " created, "
                    + result.getUpdatedProducts() + " updated, "
                    + result.getStockRowsUpdated() + " stock rows updated";
            if (result.getSkippedRows() > 0) {
                message += ", " + result.getSkippedRows() + " skipped. " + String.join(" ", result.getErrors());
            }
            redirect.addFlashAttribute("message", message);
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/borrowers")
    public String createBorrower(@RequestParam(required = false) String accountNumber,
                                 @RequestParam String fullName,
                                 @RequestParam String phone,
                                 @RequestParam(required = false) String nationalId,
                                 @RequestParam(defaultValue = "USD") CurrencyCode currency,
                                 @RequestParam BigDecimal creditLimit,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirect) {
        try {
            creditAndChangeService.saveBorrower(current.tenantId(), null, accountNumber, fullName, phone,
                    nationalId, currency, creditLimit, notes, current.userId());
            redirect.addFlashAttribute("message", "Borrower account created.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/borrowers";
    }

    @PostMapping("/shop/inventory-intelligence/transfers")
    public String createTransfer(@RequestParam Long fromBranchId, @RequestParam Long toBranchId,
                                 @RequestParam Long productId, @RequestParam BigDecimal quantity,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.createTransfer(current.tenantId(), fromBranchId, toBranchId,
                    productId, quantity, notes, current.userId());
            redirect.addFlashAttribute("message", "Stock transfer created for approval and dispatch.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + fromBranchId;
    }

    @PostMapping("/shop/inventory-intelligence/transfers/{id}/{action}")
    public String transferAction(@PathVariable Long id, @PathVariable String action,
                                 @RequestParam(required = false) Long branchId,
                                 RedirectAttributes redirect) {
        try {
            if ("dispatch".equals(action)) {
                inventoryIntelligenceService.dispatchTransfer(current.tenantId(), id, current.userId());
            } else if ("receive".equals(action)) {
                inventoryIntelligenceService.receiveTransfer(current.tenantId(), id, current.userId());
            } else {
                throw new IllegalArgumentException("Unsupported transfer action.");
            }
            redirect.addFlashAttribute("message", "Transfer updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/stocktakes")
    public String startStocktake(@RequestParam Long branchId,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.startStocktake(current.tenantId(), selectedBranch(branchId), notes, current.userId());
            redirect.addFlashAttribute("message", "Stocktake started with a snapshot of current stock.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/stocktakes/{sessionId}/count/{itemId}")
    public String countStock(@PathVariable Long sessionId, @PathVariable Long itemId,
                             @RequestParam BigDecimal countedQuantity,
                             @RequestParam(required = false) String notes,
                             @RequestParam Long branchId,
                             RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.countStock(current.tenantId(), sessionId, itemId,
                    countedQuantity, notes, current.userId());
            redirect.addFlashAttribute("message", "Count saved.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/stocktakes/{id}/{action}")
    public String stocktakeAction(@PathVariable Long id, @PathVariable String action,
                                  @RequestParam Long branchId, RedirectAttributes redirect) {
        try {
            if ("submit".equals(action)) {
                inventoryIntelligenceService.submitStocktake(current.tenantId(), id, current.userId());
            } else if ("approve".equals(action)) {
                inventoryIntelligenceService.approveStocktake(current.tenantId(), id, current.userId());
            } else {
                throw new IllegalArgumentException("Unsupported stocktake action.");
            }
            redirect.addFlashAttribute("message", "Stocktake updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/variances/{id}/resolve")
    public String resolveVariance(@PathVariable Long id, @RequestParam String reason,
                                  @RequestParam String notes, @RequestParam Long branchId,
                                  RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.resolveInvestigation(current.tenantId(), id, reason, notes, current.userId());
            redirect.addFlashAttribute("message", "Variance investigation resolved.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/lots")
    public String createLot(@RequestParam Long branchId, @RequestParam Long productId,
                            @RequestParam(required = false) String batchNumber,
                            @RequestParam(required = false) String serialNumber,
                            @RequestParam(required = false) LocalDate expiryDate,
                            @RequestParam BigDecimal quantity,
                            @RequestParam(required = false) Long supplierId,
                            @RequestParam(required = false) String notes,
                            RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.saveLot(current.tenantId(), selectedBranch(branchId), productId,
                    batchNumber, serialNumber, expiryDate, quantity, supplierId, notes);
            redirect.addFlashAttribute("message", "Batch or serial record saved.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/tracking")
    public String setTracking(@RequestParam Long productId, @RequestParam Product.TrackingMode trackingMode,
                              @RequestParam(defaultValue = "false") boolean expiryTracking,
                              @RequestParam Long branchId, RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.setTracking(current.tenantId(), productId, trackingMode, expiryTracking);
            redirect.addFlashAttribute("message", "Product traceability settings updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/inventory-intelligence/supplier-prices")
    public String saveSupplierPrice(@RequestParam Long productId, @RequestParam Long supplierId,
                                    @RequestParam(defaultValue = "0") BigDecimal costPriceUsd,
                                    @RequestParam(defaultValue = "0") BigDecimal costPriceZwg,
                                    @RequestParam(defaultValue = "0") BigDecimal minimumOrderQty,
                                    @RequestParam(defaultValue = "0") Integer leadTimeDays,
                                    @RequestParam(defaultValue = "false") boolean preferred,
                                    @RequestParam Long branchId, RedirectAttributes redirect) {
        try {
            inventoryIntelligenceService.saveSupplierPrice(current.tenantId(), productId, supplierId,
                    costPriceUsd, costPriceZwg, minimumOrderQty, leadTimeDays, preferred);
            redirect.addFlashAttribute("message", "Supplier price comparison updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory-intelligence?branchId=" + selectedBranch(branchId);
    }

    @PostMapping("/shop/borrowers/{id}/update")
    public String updateBorrower(@PathVariable Long id,
                                 @RequestParam String accountNumber,
                                 @RequestParam String fullName,
                                 @RequestParam String phone,
                                 @RequestParam(required = false) String nationalId,
                                 @RequestParam CurrencyCode currency,
                                 @RequestParam BigDecimal creditLimit,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirect) {
        try {
            creditAndChangeService.saveBorrower(current.tenantId(), id, accountNumber, fullName, phone,
                    nationalId, currency, creditLimit, notes, current.userId());
            redirect.addFlashAttribute("message", "Borrower account updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/borrowers";
    }

    @PostMapping("/shop/borrowers/{id}/{action}")
    public String borrowerAction(@PathVariable Long id, @PathVariable String action,
                                 @RequestParam(required = false) BigDecimal amount,
                                 @RequestParam(required = false) String notes,
                                 RedirectAttributes redirect) {
        try {
            switch (action) {
                case "activate" -> creditAndChangeService.setBorrowerActive(current.tenantId(), id, true);
                case "deactivate" -> creditAndChangeService.setBorrowerActive(current.tenantId(), id, false);
                case "repayment" -> creditAndChangeService.postBorrowerTransaction(
                        current.tenantId(), activeBranch(), id, BorrowerTransaction.TransactionType.REPAYMENT,
                        amount, notes, current.userId());
                case "adjustment" -> creditAndChangeService.postBorrowerTransaction(
                        current.tenantId(), activeBranch(), id, BorrowerTransaction.TransactionType.ADJUSTMENT,
                        amount, notes, current.userId());
                default -> throw new IllegalArgumentException("Unsupported borrower action.");
            }
            redirect.addFlashAttribute("message", "Borrower account updated.");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/borrowers";
    }

    @PostMapping("/shop/change/{id}/{action}")
    public String changeAction(@PathVariable Long id, @PathVariable String action,
                               RedirectAttributes redirect) {
        try {
            if ("collect".equals(action)) {
                creditAndChangeService.collectChange(current.tenantId(), activeBranch(), current.userId(), id, null);
                redirect.addFlashAttribute("message", "Held change marked as collected.");
            } else if ("cancel".equals(action)) {
                creditAndChangeService.cancelChange(current.tenantId(), id, current.userId());
                redirect.addFlashAttribute("message", "Held change cancelled.");
            } else {
                throw new IllegalArgumentException("Unsupported change action.");
            }
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/change";
    }

    @PostMapping("/shop/products/{id}/update")
    public String updateProduct(@PathVariable Long id, @ModelAttribute CreateProductRequest request, RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(request.getBranchId());
        request.setBranchId(targetBranch);
        try {
            operations.updateProduct(current.tenantId(), id, request);
            redirect.addFlashAttribute("message", "Product updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/products/assign")
    public String assignProductToBranch(@RequestParam Long productId,
                                        @RequestParam(required = false) Long branchId,
                                        @RequestParam(required = false) BigDecimal openingStock,
                                        RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.assignProductToBranch(current.tenantId(), targetBranch, productId, openingStock);
            redirect.addFlashAttribute("message", "Product assigned to " + branchById(current.tenantId()).get(targetBranch) + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/products/{id}/unassign")
    public String removeProductFromBranch(@PathVariable Long id,
                                          @RequestParam(required = false) Long branchId,
                                          RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.removeProductFromBranch(current.tenantId(), targetBranch, id);
            redirect.addFlashAttribute("message", "Product removed from this branch.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/products/{id}/activate")
    public String activateProduct(@PathVariable Long id,
                                  @RequestParam(required = false) Long branchId,
                                  RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        operations.setProductActive(current.tenantId(), id, true);
        redirect.addFlashAttribute("message", "Product activated.");
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/products/{id}/deactivate")
    public String deactivateProduct(@PathVariable Long id,
                                    @RequestParam(required = false) Long branchId,
                                    RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        operations.setProductActive(current.tenantId(), id, false);
        redirect.addFlashAttribute("message", "Product deactivated.");
        return "redirect:/shop/products?branchId=" + targetBranch;
    }

    @PostMapping("/shop/categories")
    public String createCategory(@ModelAttribute ProductCategory category, RedirectAttributes redirect) {
        category.setTenantId(current.tenantId());
        category.setIsActive(true);
        categories.save(category);
        redirect.addFlashAttribute("message", "Category saved.");
        return "redirect:/shop/categories";
    }

    @PostMapping("/shop/categories/{id}/update")
    public String updateCategory(@PathVariable Long id, @ModelAttribute ProductCategory request, RedirectAttributes redirect) {
        operations.updateCategory(current.tenantId(), id, request);
        redirect.addFlashAttribute("message", "Category updated.");
        return "redirect:/shop/categories";
    }

    @PostMapping("/shop/categories/{id}/activate")
    public String activateCategory(@PathVariable Long id, RedirectAttributes redirect) {
        operations.setCategoryActive(current.tenantId(), id, true);
        redirect.addFlashAttribute("message", "Category activated.");
        return "redirect:/shop/categories";
    }

    @PostMapping("/shop/categories/{id}/deactivate")
    public String deactivateCategory(@PathVariable Long id, RedirectAttributes redirect) {
        operations.setCategoryActive(current.tenantId(), id, false);
        redirect.addFlashAttribute("message", "Category deactivated.");
        return "redirect:/shop/categories";
    }

    @PostMapping("/shop/customers")
    public String createCustomer(@Valid @ModelAttribute CreateCustomerRequest request, RedirectAttributes redirect) {
        operations.createCustomer(current.tenantId(), activeBranch(), request, current.userId());
        redirect.addFlashAttribute("message", "Customer saved.");
        return "redirect:/shop/customers";
    }

    @PostMapping("/shop/customers/{id}/update")
    public String updateCustomer(@PathVariable Long id, @Valid @ModelAttribute CreateCustomerRequest request, RedirectAttributes redirect) {
        operations.updateCustomer(current.tenantId(), id, request);
        redirect.addFlashAttribute("message", "Customer profile updated.");
        return "redirect:/shop/customers";
    }

    @PostMapping("/shop/customers/{id}/activate")
    public String activateCustomer(@PathVariable Long id, RedirectAttributes redirect) {
        operations.setCustomerActive(current.tenantId(), id, true);
        redirect.addFlashAttribute("message", "Customer activated.");
        return "redirect:/shop/customers";
    }

    @PostMapping("/shop/customers/{id}/deactivate")
    public String deactivateCustomer(@PathVariable Long id, RedirectAttributes redirect) {
        operations.setCustomerActive(current.tenantId(), id, false);
        redirect.addFlashAttribute("message", "Customer deactivated.");
        return "redirect:/shop/customers";
    }

    @PostMapping("/shop/suppliers")
    public String createSupplier(@ModelAttribute Supplier supplier, RedirectAttributes redirect) {
        supplier.setTenantId(current.tenantId());
        supplier.setIsActive(!Boolean.FALSE.equals(supplier.getIsActive()));
        suppliers.save(supplier);
        redirect.addFlashAttribute("message", "Supplier saved.");
        return "redirect:/shop/suppliers";
    }

    @PostMapping("/shop/suppliers/{id}/update")
    public String updateSupplier(@PathVariable Long id, @ModelAttribute Supplier request, RedirectAttributes redirect) {
        try {
            Supplier supplier = suppliers.findById(id)
                    .filter(existing -> current.tenantId().equals(existing.getTenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found for this shop."));
            updateSupplierFields(supplier, request);
            suppliers.save(supplier);
            redirect.addFlashAttribute("message", "Supplier updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/suppliers";
    }

    @PostMapping("/shop/suppliers/{id}/activate")
    public String activateSupplier(@PathVariable Long id, RedirectAttributes redirect) {
        setSupplierActive(id, true, redirect);
        return "redirect:/shop/suppliers";
    }

    @PostMapping("/shop/suppliers/{id}/deactivate")
    public String deactivateSupplier(@PathVariable Long id, RedirectAttributes redirect) {
        setSupplierActive(id, false, redirect);
        return "redirect:/shop/suppliers";
    }

    @PostMapping("/shop/branches")
    public String createBranch(@ModelAttribute Branch branch, RedirectAttributes redirect) {
        try {
            operations.createBranch(current.tenantId(), branch);
            redirect.addFlashAttribute("message", "Branch created with Till 1 ready. Assign products from the Products module.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/branches";
    }

    @PostMapping("/shop/branches/{id}/update")
    public String updateBranch(@PathVariable Long id, @ModelAttribute Branch branch, RedirectAttributes redirect) {
        try {
            operations.updateBranch(current.tenantId(), id, branch);
            redirect.addFlashAttribute("message", "Branch updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/branches";
    }

    @PostMapping("/shop/branches/{id}/activate")
    public String activateBranch(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            operations.setBranchActive(current.tenantId(), id, true);
            redirect.addFlashAttribute("message", "Branch activated and ready for stock/cashier use.");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/branches";
    }

    @PostMapping("/shop/branches/{id}/deactivate")
    public String deactivateBranch(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            operations.setBranchActive(current.tenantId(), id, false);
            redirect.addFlashAttribute("message", "Branch deactivated.");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/branches";
    }

    @PostMapping("/shop/gas/tanks")
    public String createGasTank(@Valid @ModelAttribute GasTankRequest request,
                                @RequestParam(required = false) String returnTo,
                                RedirectAttributes redirect) {
        try {
            gasOperations.createTank(current.tenantId(), request);
            redirect.addFlashAttribute("message", "Gas tank saved.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/tanks");
    }

    @PostMapping("/shop/gas/tanks/{id}/update")
    public String updateGasTank(@PathVariable Long id,
                                @Valid @ModelAttribute GasTankRequest request,
                                @RequestParam(required = false) String returnTo,
                                RedirectAttributes redirect) {
        try {
            gasOperations.updateTank(current.tenantId(), id, request);
            redirect.addFlashAttribute("message", "Gas tank updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/tanks");
    }

    @PostMapping("/shop/gas/prices")
    public String setGasPrice(@Valid @ModelAttribute GasPriceRequest request,
                              @RequestParam(required = false) String returnTo,
                              RedirectAttributes redirect) {
        try {
            gasOperations.setPrice(current.tenantId(), request);
            redirect.addFlashAttribute("message", "Gas price updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/tanks");
    }

    @PostMapping("/shop/gas/restocks")
    public String restockGasTank(@Valid @ModelAttribute GasRestockRequest request,
                                 @RequestParam(required = false) String returnTo,
                                 RedirectAttributes redirect) {
        try {
            gasOperations.restock(current.tenantId(), current.userId(), request);
            redirect.addFlashAttribute("message", "Gas stock received and costing captured.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/restocking");
    }

    @PostMapping("/shop/gas/stock/reconcile")
    public String reconcileGasStock(@Valid @ModelAttribute GasStockReconciliationRequest request,
                                    @RequestParam(required = false) String returnTo,
                                    RedirectAttributes redirect) {
        try {
            GasStockAdjustment adjustment = gasOperations.reconcileStock(
                    current.tenantId(), current.userId(), request);
            redirect.addFlashAttribute("message", "Gas stock reconciled. Variance: "
                    + adjustment.getVarianceKg() + " kg.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/tanks");
    }

    @PostMapping("/shop/gas/expenses")
    public String recordGasExpense(@Valid @ModelAttribute GasExpenseRequest request,
                                   @RequestParam(required = false) String returnTo,
                                   RedirectAttributes redirect) {
        try {
            gasOperations.recordExpense(current.tenantId(), current.userId(), request);
            redirect.addFlashAttribute("message", "Gas expense recorded.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return gasRedirect(returnTo, request.getBranchId(), "/shop/gas/expenses");
    }

    @PostMapping("/shop/users")
    public String createUser(@Valid @ModelAttribute CreateUserRequest request, RedirectAttributes redirect) {
        try {
            operations.createUser(current.tenantId(), request);
            redirect.addFlashAttribute("message", "User saved.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/users";
    }

    @PostMapping("/shop/users/{id}/update")
    public String updateUser(@PathVariable Long id, @ModelAttribute CreateUserRequest request, RedirectAttributes redirect) {
        try {
            operations.updateUser(current.tenantId(), id, request);
            redirect.addFlashAttribute("message", "User profile updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/users";
    }

    @PostMapping("/shop/users/{id}/activate")
    public String activateUser(@PathVariable Long id, RedirectAttributes redirect) {
        operations.setUserActive(current.tenantId(), id, true);
        redirect.addFlashAttribute("message", "User activated.");
        return "redirect:/shop/users";
    }

    @PostMapping("/shop/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Long id, RedirectAttributes redirect) {
        if (id.equals(current.userId())) {
            redirect.addFlashAttribute("message", "You cannot deactivate your own active session.");
            return "redirect:/shop/users";
        }
        operations.setUserActive(current.tenantId(), id, false);
        redirect.addFlashAttribute("message", "User deactivated.");
        return "redirect:/shop/users";
    }

    @PostMapping("/shop/inventory/adjust")
    public String adjust(@Valid @ModelAttribute StockAdjustmentRequest request,
                         @RequestParam(required = false) Long branchId,
                         RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.adjustStock(current.tenantId(), targetBranch, request, current.userId());
            redirect.addFlashAttribute("message", "Stock adjusted for " + branchById(current.tenantId()).get(targetBranch) + ".");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/inventory?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/drawers")
    public String drawer(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) Long branchId,
                         RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.createDrawer(current.tenantId(), targetBranch, name, description);
            redirect.addFlashAttribute("message", "Cash drawer saved.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/cash?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/drawers/{id}/update")
    public String updateDrawer(@PathVariable Long id,
                               @RequestParam String name,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) Long branchId,
                               RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.updateDrawer(current.tenantId(), targetBranch, id, name, description);
            redirect.addFlashAttribute("message", "Cash drawer updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/cash?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/drawers/{id}/activate")
    public String activateDrawer(@PathVariable Long id, @RequestParam(required = false) Long branchId, RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        operations.setDrawerActive(current.tenantId(), targetBranch, id, true);
        redirect.addFlashAttribute("message", "Cash drawer activated.");
        return "redirect:/shop/cash?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/drawers/{id}/deactivate")
    public String deactivateDrawer(@PathVariable Long id, @RequestParam(required = false) Long branchId, RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            operations.setDrawerActive(current.tenantId(), targetBranch, id, false);
            redirect.addFlashAttribute("message", "Cash drawer deactivated.");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/cash?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/shifts/collect")
    public String collectShiftMoney(@Valid @ModelAttribute CloseSessionRequest request,
                                    @RequestParam(required = false) Long branchId,
                                    RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            CashSession saved = operations.collectSessionMoneyAsAdmin(current.tenantId(), targetBranch, current.userId(), request);
            redirect.addFlashAttribute("message", "Money collected for shift #" + saved.getId() + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            String sessionParam = request.getSessionId() == null ? "" : "&collectShiftId=" + request.getSessionId();
            return "redirect:/shop/cash?branchId=" + targetBranch + sessionParam;
        }
        return "redirect:/shop/cash?branchId=" + targetBranch;
    }

    @PostMapping("/shop/cash/shifts/close")
    public String closeShiftFromCashDesk(@Valid @ModelAttribute CloseSessionRequest request,
                                         @RequestParam(required = false) Long branchId,
                                         RedirectAttributes redirect) {
        return collectShiftMoney(request, branchId, redirect);
    }

    @PostMapping("/shop/returns")
    public String createReturn(@Valid @ModelAttribute CreateReturnRequest request,
                               @RequestParam(required = false) Long branchId,
                               RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            request.setItems(request.getItems() == null ? List.of() : request.getItems().stream()
                    .filter(item -> item.getProductId() != null)
                    .filter(item -> item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .toList());
            if (request.getItems().isEmpty()) {
                throw new IllegalArgumentException("Select at least one product quantity to return.");
            }
            Return saved = returnService.processReturn(current.tenantId(), targetBranch, current.userId(), request);
            redirect.addFlashAttribute("message", "Return processed: " + saved.getReturnNumber());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/returns?branchId=" + targetBranch;
    }

    @PostMapping("/shop/sales/{id}/void")
    public String voidSale(@PathVariable Long id,
                           @RequestParam(required = false) Long branchId,
                           @RequestParam String reason,
                           RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        Sale sale = sales.findById(id).orElseThrow();
        if (!sale.getTenantId().equals(current.tenantId()) || !sale.getBranchId().equals(targetBranch)) {
            redirect.addFlashAttribute("message", "Sale not found for this branch.");
            return "redirect:/shop/sales?branchId=" + targetBranch;
        }
        sale.setStatus(Sale.SaleStatus.VOIDED);
        sale.setVoidReason(reason);
        sale.setVoidedBy(current.userId());
        sale.setVoidedAt(LocalDateTime.now());
        sales.save(sale);
        redirect.addFlashAttribute("message", "Sale voided.");
        return "redirect:/shop/sales?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing")
    public String createPurchaseOrder(@RequestParam(required = false) Long branchId,
                                      @RequestParam Long supplierId,
                                      @RequestParam(required = false) CurrencyCode currency,
                                      @RequestParam(required = false) String expectedDeliveryDate,
                                      @RequestParam(required = false) String notes,
                                      @RequestParam(required = false) Boolean submit,
                                      @RequestParam(required = false, name = "productId") List<Long> productIds,
                                      @RequestParam(required = false, name = "quantity") List<BigDecimal> quantities,
                                      @RequestParam(required = false, name = "unitCostUsd") List<BigDecimal> unitCostUsd,
                                      @RequestParam(required = false, name = "unitCostZwg") List<BigDecimal> unitCostZwg,
                                      @RequestParam(required = false, name = "taxRate") List<BigDecimal> taxRates,
                                      @RequestParam(required = false, name = "lineNotes") List<String> lineNotes,
                                      RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            CreatePurchaseOrderRequest request = buildPurchaseOrderRequest(
                    targetBranch, supplierId, currency, expectedDeliveryDate, notes, productIds, quantities, unitCostUsd, unitCostZwg, taxRates, lineNotes);
            PurchaseOrder saved = purchaseOrderService.createPurchaseOrder(current.tenantId(), targetBranch, current.userId(), request);
            if (Boolean.TRUE.equals(submit)) {
                saved = purchaseOrderService.submitPurchaseOrder(saved.getId());
                redirect.addFlashAttribute("message", "Purchase order submitted for approval: " + saved.getPoNumber());
            } else {
                redirect.addFlashAttribute("message", "Draft purchase order created: " + saved.getPoNumber());
            }
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/submit")
    public String submitPurchaseOrder(@PathVariable Long id,
                                      @RequestParam(required = false) Long branchId,
                                      RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            PurchaseOrder po = ownedPurchaseOrder(id, targetBranch);
            if (!PurchaseOrder.PoStatus.DRAFT.equals(po.getStatus())) {
                throw new IllegalStateException("Only draft purchase orders can be submitted.");
            }
            PurchaseOrder saved = purchaseOrderService.submitPurchaseOrder(id);
            redirect.addFlashAttribute("message", "Purchase order submitted: " + saved.getPoNumber());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/approve")
    public String approvePurchaseOrder(@PathVariable Long id,
                                       @RequestParam(required = false) Long branchId,
                                       RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            PurchaseOrder po = ownedPurchaseOrder(id, targetBranch);
            if (!PurchaseOrder.PoStatus.SUBMITTED.equals(po.getStatus())) {
                throw new IllegalStateException("Only submitted purchase orders can be approved.");
            }
            PurchaseOrder saved = purchaseOrderService.approvePurchaseOrder(id, current.userId());
            redirect.addFlashAttribute("message", "Purchase order approved: " + saved.getPoNumber());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/reject")
    public String rejectPurchaseOrder(@PathVariable Long id,
                                      @RequestParam(required = false) Long branchId,
                                      @RequestParam String reason,
                                      RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            ownedPurchaseOrder(id, targetBranch);
            PurchaseOrder saved = purchaseOrderService.rejectPurchaseOrder(id, current.userId(), reason);
            redirect.addFlashAttribute("message", "Purchase order rejected: " + saved.getPoNumber());
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/order")
    public String markPurchaseOrderOrdered(@PathVariable Long id,
                                           @RequestParam(required = false) Long branchId,
                                           RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            PurchaseOrder po = ownedPurchaseOrder(id, targetBranch);
            if (!PurchaseOrder.PoStatus.APPROVED.equals(po.getStatus())) {
                throw new IllegalStateException("Only approved purchase orders can be marked ordered.");
            }
            po.setStatus(PurchaseOrder.PoStatus.ORDERED);
            po.setSentToSupplierAt(LocalDateTime.now());
            purchaseOrders.save(po);
            redirect.addFlashAttribute("message", "Purchase order marked as sent to supplier.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/receive")
    public String receivePurchaseOrder(@PathVariable Long id,
                                       @RequestParam(required = false) Long branchId,
                                       @RequestParam Map<String, String> requestParams,
                                       RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            PurchaseOrder po = ownedPurchaseOrder(id, targetBranch);
            if (PurchaseOrder.PoStatus.DRAFT.equals(po.getStatus()) || PurchaseOrder.PoStatus.SUBMITTED.equals(po.getStatus())
                    || PurchaseOrder.PoStatus.RECEIVED.equals(po.getStatus()) || PurchaseOrder.PoStatus.CANCELLED.equals(po.getStatus())) {
                throw new IllegalStateException("This purchase order is not ready for goods receiving.");
            }
            Map<Long, BigDecimal> received = new HashMap<>();
            requestParams.forEach((key, value) -> {
                if (key.startsWith("received_") && value != null && !value.isBlank()) {
                    BigDecimal quantity = new BigDecimal(value);
                    if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                        received.put(Long.valueOf(key.substring("received_".length())), quantity);
                    }
                }
            });
            if (received.isEmpty()) {
                throw new IllegalArgumentException("Enter at least one received quantity.");
            }
            PurchaseOrder saved = purchaseOrderService.receiveGoods(id, received, current.userId());
            redirect.addFlashAttribute("message", "Goods received for " + saved.getPoNumber() + ". Branch inventory updated.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/{id}/cancel")
    public String cancelPurchaseOrder(@PathVariable Long id,
                                      @RequestParam(required = false) Long branchId,
                                      RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        try {
            PurchaseOrder po = ownedPurchaseOrder(id, targetBranch);
            if (PurchaseOrder.PoStatus.RECEIVED.equals(po.getStatus())) {
                throw new IllegalStateException("Received purchase orders cannot be cancelled.");
            }
            purchaseOrderService.cancelPurchaseOrder(id);
            redirect.addFlashAttribute("message", "Purchase order cancelled.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/purchasing/auto-reorder")
    public String autoReorder(@RequestParam(required = false) Long branchId,
                              RedirectAttributes redirect) {
        Long targetBranch = selectedBranch(branchId);
        List<PurchaseOrder> generated = purchaseOrderService.autoGeneratePurchaseOrders(current.tenantId(), targetBranch, current.userId());
        redirect.addFlashAttribute("message", generated.isEmpty()
                ? "No low-stock products with suppliers were ready for auto reorder."
                : "Auto reorder created " + generated.size() + " draft purchase order(s).");
        return "redirect:/shop/purchasing?branchId=" + targetBranch;
    }

    @PostMapping("/shop/company")
    public String updateCompanyProfile(@ModelAttribute Tenant request, RedirectAttributes redirect) {
        Tenant tenant = tenants.findById(current.tenantId()).orElseThrow();
        tenant.setCompanyName(request.getCompanyName());
        tenant.setRegistrationNumber(request.getRegistrationNumber());
        tenant.setVatNumber(request.getVatNumber());
        tenant.setEmail(request.getEmail());
        tenant.setPhone(request.getPhone());
        tenant.setAddress(request.getAddress());
        tenant.setCity(request.getCity());
        tenant.setCountry(request.getCountry());
        tenant.setLogoUrl(request.getLogoUrl());
        tenant.setWebsite(request.getWebsite());
        tenant.setDefaultCurrency(request.getDefaultCurrency() == null ? CurrencyCode.USD : request.getDefaultCurrency());
        tenant.setSecondaryCurrency(request.getSecondaryCurrency() == null ? CurrencyCode.ZWG : request.getSecondaryCurrency());
        tenant.setDefaultTaxRate(request.getDefaultTaxRate() == null ? BigDecimal.ZERO : request.getDefaultTaxRate());
        tenant.setReceiptFooter(request.getReceiptFooter());
        tenants.save(tenant);
        redirect.addFlashAttribute("message", "Company profile updated. Receipts and reports will use these details.");
        return "redirect:/shop/company";
    }

    @PostMapping("/shop/notifications/read-all")
    public String markNotificationsRead(RedirectAttributes redirect) {
        notificationService.markAllRead(current.userId());
        redirect.addFlashAttribute("message", "Notifications marked as read.");
        return "redirect:/shop/notifications";
    }

    @PostMapping("/shop/support/chat")
    public String sendSupportChat(@RequestParam String message, RedirectAttributes redirect) {
        User user = users.findById(current.userId()).orElseThrow();
        chatMessages.save(TenantChatMessage.builder()
                .tenantId(current.tenantId())
                .senderType(TenantChatMessage.SenderType.SHOP)
                .senderName(user.getFirstName() + " " + user.getLastName())
                .message(message)
                .readByPlatform(false)
                .readByShop(true)
                .build());
        redirect.addFlashAttribute("message", "Support message sent.");
        return "redirect:/shop/notifications#live-chat";
    }

    @GetMapping("/shop/support/chat/feed")
    @ResponseBody
    public List<Map<String, Object>> supportChatFeed() {
        markShopChatRead(current.tenantId());
        return chatMessages.findByTenantIdOrderByCreatedAtDesc(current.tenantId(), PageRequest.of(0, 30)).stream()
                .sorted(java.util.Comparator.comparing(TenantChatMessage::getCreatedAt))
                .map(message -> Map.<String, Object>of(
                        "id", message.getId(),
                        "senderType", message.getSenderType().name(),
                        "senderName", message.getSenderName(),
                        "message", message.getMessage(),
                        "createdAt", message.getCreatedAt() == null ? "" : message.getCreatedAt().toString(),
                        "readByPlatform", Boolean.TRUE.equals(message.getReadByPlatform()),
                        "readByShop", Boolean.TRUE.equals(message.getReadByShop())
                ))
                .toList();
    }

    private Long activeBranch() {
        if (current.branchId() != null) return current.branchId();
        return branches.findByTenantIdAndIsActiveTrue(current.tenantId()).stream().findFirst().orElseThrow().getId();
    }

    private Long selectedBranch(Long requestedBranchId) {
        if (current.branchId() != null) return current.branchId();
        if (requestedBranchId == null) return activeBranch();
        return branches.findByTenantIdAndIsActiveTrue(current.tenantId()).stream()
                .filter(branch -> branch.getId().equals(requestedBranchId))
                .findFirst()
                .map(Branch::getId)
                .orElseGet(this::activeBranch);
    }

    private String gasRedirect(String returnTo, Long branchId, String fallbackPath) {
        String path = (returnTo != null && returnTo.startsWith("/shop/gas")) ? returnTo : fallbackPath;
        return "redirect:" + path + "?branchId=" + branchId;
    }

    private String safeModule(String module) {
        return switch (module) {
            case "products", "categories", "customers", "borrowers", "change", "suppliers", "branches", "users", "inventory", "gas",
                 "inventory-intelligence", "purchasing", "sales", "cash", "returns", "reports", "company", "notifications", "audit" -> module;
            default -> "dashboard";
        };
    }

    private void addNavigationModel(Model model) {
        Long tenantId = current.tenantId();
        Long activeBranchId = activeBranch();
        Map<Long, String> branchNames = branchById(tenantId);
        List<BusinessModule> enabledBusinessModules = packageModuleAccessService.syncAndGetEnabledModules(tenantId);
        boolean shopEnabled = enabledBusinessModules.contains(BusinessModule.SHOP_MODULE);
        boolean gasEnabled = enabledBusinessModules.contains(BusinessModule.GAS_MODULE);
        BillingAccessService.BillingAccess billingAccess = billingAccessService.evaluate(tenantId);
        boolean tenantBillingOnly = billingAccess.locked();

        model.addAttribute("tenantBillingOnly", tenantBillingOnly);
        tenants.findById(tenantId).ifPresent(tenant -> {
            String packageName = tenant.getPlanId() == null
                    ? "Starter"
                    : plans.findById(tenant.getPlanId()).map(SaasPlan::getName).orElse("Package");
            model.addAttribute("tenantPackageName", packageName);
            model.addAttribute("tenantPackageLabel", packageName.toLowerCase().endsWith("plan")
                    ? packageName
                    : packageName + " Plan");
            model.addAttribute("tenantSubscriptionEnd", tenant.getSubscriptionEnd());
            model.addAttribute("tenantStatus", tenant.getStatus());
        });
        model.addAttribute("tenantActiveBranchName", branchNames.getOrDefault(activeBranchId, "Main Shop"));
        model.addAttribute("topbarBranchName", branchNames.getOrDefault(activeBranchId, "Head Office Branch"));
        model.addAttribute("topbarDateLabel", LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        model.addAttribute("moduleLabels", MODULE_LABELS);
        model.addAttribute("moduleIcons", MODULE_ICONS);
        model.addAttribute("moduleUrls", MODULE_URLS);

        if (billingAccess.locked()) {
            model.addAttribute("salesModules", List.of());
            model.addAttribute("stockModules", List.of());
            model.addAttribute("gasModules", List.of());
            model.addAttribute("peopleModules", List.of());
            model.addAttribute("financeModules", List.of());
            model.addAttribute("systemModules", List.of());
            model.addAttribute("enabledBusinessModules", List.of());
            model.addAttribute("gasModuleEnabled", false);
            model.addAttribute("shopModuleEnabled", false);
            model.addAttribute("billingAccessLocked", true);
            model.addAttribute("billingLockMessage", billingAccess.message());
            model.addAttribute("billingOverdueDays", billingAccess.overdueDays());
            model.addAttribute("supportChatMessages", List.of());
            model.addAttribute("supportUnreadCount", 0L);
            return;
        }

        model.addAttribute("billingAccessLocked", false);
        model.addAttribute("salesModules", shopEnabled ? SALES_MODULES : List.of());
        List<String> stockModules = new ArrayList<>();
        if (shopEnabled) {
            stockModules.addAll(STOCK_MODULES.stream().filter(module -> !"gas".equals(module)).toList());
        }
        model.addAttribute("stockModules", stockModules);
        model.addAttribute("gasModules", gasEnabled ? GAS_MODULES : List.of());
        model.addAttribute("peopleModules", shopEnabled ? PEOPLE_MODULES : (gasEnabled ? List.of("users") : List.of()));
        model.addAttribute("financeModules", shopEnabled ? FINANCE_MODULES : List.of());
        model.addAttribute("systemModules", shopEnabled ? SYSTEM_MODULES : List.of());
        model.addAttribute("enabledBusinessModules", enabledBusinessModules);
        model.addAttribute("gasModuleEnabled", gasEnabled);
        model.addAttribute("shopModuleEnabled", shopEnabled);
        model.addAttribute("supportChatMessages", chatMessages.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, 30)).stream()
                .sorted(java.util.Comparator.comparing(TenantChatMessage::getCreatedAt))
                .toList());
        model.addAttribute("supportUnreadCount", chatMessages.countByTenantIdAndReadByShopFalseAndSenderType(tenantId, TenantChatMessage.SenderType.PLATFORM));
    }

    private void markShopChatRead(Long tenantId) {
        List<TenantChatMessage> unread = chatMessages
                .findByTenantIdAndReadByShopFalseAndSenderTypeOrderByCreatedAtAsc(tenantId, TenantChatMessage.SenderType.PLATFORM);
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(message -> message.setReadByShop(true));
        chatMessages.saveAll(unread);
    }

    private void addUserManagementModel(Model model, Long tenantId, Long selectedBranchId, int page, int size) {
        List<User> allUsers = users.findByTenantId(tenantId);
        Page<User> userPage = pageList(allUsers, page, size);
        List<User> tenantUsers = userPage.getContent();
        Set<Long> activeBranchIds = branches.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(Branch::getId)
                .collect(java.util.stream.Collectors.toSet());
        model.addAttribute("tenantUsers", tenantUsers);
        model.addAttribute("userPage", userPage);
        addPaginationModel(model, "user", userPage, "/shop/users", params("branchId", selectedBranchId));
        model.addAttribute("branchById", branchById(tenantId));
        model.addAttribute("activeBranchIds", activeBranchIds);
        model.addAttribute("selectedBranchUserCount", allUsers.stream().filter(u -> selectedBranchId.equals(u.getBranchId())).count());
        model.addAttribute("userRoleOptions", roles.findAll().stream()
                .filter(role -> USER_MANAGEMENT_ROLES.contains(role.getName()))
                .toList());
        model.addAttribute("superAdminCount", allUsers.stream().filter(u -> hasRole(u, UserRole.SUPER_ADMIN)).count());
        model.addAttribute("accountsCount", allUsers.stream().filter(u -> hasRole(u, UserRole.ACCOUNTANT)).count());
        model.addAttribute("cashierCount", allUsers.stream().filter(u -> hasRole(u, UserRole.CASHIER)).count());
        model.addAttribute("activeUserCount", allUsers.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count());
        model.addAttribute("branchlessCashierCount", allUsers.stream()
                .filter(u -> hasRole(u, UserRole.CASHIER))
                .filter(u -> u.getBranchId() == null)
                .count());
        model.addAttribute("invalidBranchUserCount", allUsers.stream()
                .filter(u -> u.getBranchId() != null)
                .filter(u -> !activeBranchIds.contains(u.getBranchId()))
                .count());
    }

    private void addCustomerManagementModel(Model model, Long tenantId, String search, int page, int size) {
        String cleanSearch = search == null || search.isBlank() ? null : search;
        Page<Customer> customerPage = customers.searchCustomers(tenantId, cleanSearch, pageRequest(page, size));
        List<Customer> tenantCustomers = customerPage.getContent();
        model.addAttribute("tenantCustomers", tenantCustomers);
        model.addAttribute("customerPage", customerPage);
        addPaginationModel(model, "customer", customerPage, "/shop/customers", params("search", search));
        model.addAttribute("customerSearch", search);
        model.addAttribute("activeCustomerCount", tenantCustomers.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count());
        model.addAttribute("bronzeCount", tenantCustomers.stream().filter(c -> Customer.LoyaltyTier.BRONZE.equals(c.getLoyaltyTier())).count());
        model.addAttribute("silverCount", tenantCustomers.stream().filter(c -> Customer.LoyaltyTier.SILVER.equals(c.getLoyaltyTier())).count());
        model.addAttribute("goldCount", tenantCustomers.stream().filter(c -> Customer.LoyaltyTier.GOLD.equals(c.getLoyaltyTier())).count());
        model.addAttribute("platinumCount", tenantCustomers.stream().filter(c -> Customer.LoyaltyTier.PLATINUM.equals(c.getLoyaltyTier())).count());
        model.addAttribute("totalLoyaltyPoints", tenantCustomers.stream().map(Customer::getLoyaltyPoints).filter(java.util.Objects::nonNull).reduce(0, Integer::sum));
        model.addAttribute("totalCustomerSpendUsd", tenantCustomers.stream().map(Customer::getTotalSpentUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalCustomerSpendZwg", tenantCustomers.stream().map(Customer::getTotalSpentZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void addSupplierManagementModel(Model model, Long tenantId, String search, int page, int size) {
        String cleanSearch = search == null || search.isBlank() ? null : search.trim();
        Page<Supplier> supplierPage = suppliers.searchSuppliers(tenantId, cleanSearch, pageRequest(page, size));
        List<Supplier> supplierList = supplierPage.getContent();
        Map<Long, Long> poCounts = new HashMap<>();
        Map<Long, Long> openPoCounts = new HashMap<>();
        Map<Long, BigDecimal> orderValueUsd = new HashMap<>();
        Map<Long, BigDecimal> orderValueZwg = new HashMap<>();
        Map<Long, String> lastOrderBySupplier = new HashMap<>();

        for (Supplier supplier : supplierList) {
            List<PurchaseOrder> supplierOrders = purchaseOrders.findByTenantIdAndSupplierId(tenantId, supplier.getId());
            poCounts.put(supplier.getId(), (long) supplierOrders.size());
            openPoCounts.put(supplier.getId(), supplierOrders.stream()
                    .filter(order -> !PurchaseOrder.PoStatus.RECEIVED.equals(order.getStatus()))
                    .filter(order -> !PurchaseOrder.PoStatus.CANCELLED.equals(order.getStatus()))
                    .count());
            orderValueUsd.put(supplier.getId(), supplierOrders.stream()
                    .map(PurchaseOrder::getTotalUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            orderValueZwg.put(supplier.getId(), supplierOrders.stream()
                    .map(PurchaseOrder::getTotalZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            supplierOrders.stream()
                    .map(PurchaseOrder::getCreatedAt)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .ifPresent(date -> lastOrderBySupplier.put(supplier.getId(), date.toLocalDate().toString()));
        }

        model.addAttribute("supplierList", supplierList);
        model.addAttribute("supplierPage", supplierPage);
        addPaginationModel(model, "supplier", supplierPage, "/shop/suppliers", params("search", search));
        model.addAttribute("supplierSearch", search);
        model.addAttribute("supplierPoCounts", poCounts);
        model.addAttribute("supplierOpenPoCounts", openPoCounts);
        model.addAttribute("supplierOrderValueUsd", orderValueUsd);
        model.addAttribute("supplierOrderValueZwg", orderValueZwg);
        model.addAttribute("supplierLastOrder", lastOrderBySupplier);
        model.addAttribute("activeSupplierCount", supplierList.stream().filter(supplier -> Boolean.TRUE.equals(supplier.getIsActive())).count());
        model.addAttribute("inactiveSupplierCount", supplierList.stream().filter(supplier -> !Boolean.TRUE.equals(supplier.getIsActive())).count());
        model.addAttribute("localSupplierCount", supplierList.stream()
                .filter(supplier -> supplier.getCountry() == null || supplier.getCountry().isBlank() || "zimbabwe".equalsIgnoreCase(supplier.getCountry().trim()))
                .count());
        model.addAttribute("totalPurchaseOrderCount", poCounts.values().stream().reduce(0L, Long::sum));
        model.addAttribute("totalPurchaseValueUsd", orderValueUsd.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("totalPurchaseValueZwg", orderValueZwg.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void addProductManagementModel(Model model, Long tenantId, Long selectedBranchId, String search, Long categoryId, int page, int size) {
        String cleanSearch = search == null || search.isBlank() ? null : search;
        List<Inventory> branchStockRows = inventory.findBranchProductStock(tenantId, selectedBranchId, cleanSearch, categoryId);
        Map<Long, Product> allActiveProducts = products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        List<Product> productList = branchStockRows.stream()
                .map(stock -> allActiveProducts.get(stock.getProductId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Inventory> tenantInventory = inventory.findByTenantId(tenantId);
        Map<Long, Inventory> selectedBranchStock = new HashMap<>();
        Map<Long, BigDecimal> totalStockByProduct = new HashMap<>();
        for (Inventory stock : tenantInventory) {
            totalStockByProduct.merge(stock.getProductId(), stock.getQuantityOnHand() == null ? BigDecimal.ZERO : stock.getQuantityOnHand(), BigDecimal::add);
            if (selectedBranchId.equals(stock.getBranchId())) {
                selectedBranchStock.put(stock.getProductId(), stock);
            }
        }
        Set<Long> assignedProductIds = selectedBranchStock.keySet();
        List<Product> unassignedProducts = allActiveProducts.values().stream()
                .filter(product -> !assignedProductIds.contains(product.getId()))
                .sorted(java.util.Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Page<Product> productPage = pageList(productList, page, size);
        model.addAttribute("productList", productPage.getContent());
        model.addAttribute("productPage", productPage);
        addPaginationModel(model, "product", productPage, "/shop/products", params(
                "branchId", selectedBranchId,
                "categoryId", categoryId,
                "search", search
        ));
        model.addAttribute("unassignedProducts", unassignedProducts);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("productSearch", search);
        model.addAttribute("selectedBranchStock", selectedBranchStock);
        model.addAttribute("totalStockByProduct", totalStockByProduct);
        model.addAttribute("branchById", branchById(tenantId));
        model.addAttribute("selectedBranchName", branchById(tenantId).get(selectedBranchId));
        model.addAttribute("activeProductCount", productList.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).count());
        model.addAttribute("lowStockProductCount", productList.stream().filter(p -> isLowStock(p, selectedBranchStock.get(p.getId()))).count());
        model.addAttribute("branchProductCount", branchStockRows.size());
        model.addAttribute("branchStockValueUsd", branchStockRows.stream()
                .map(stock -> nvl(stock.getQuantityOnHand()).multiply(nvl(stock.getAverageCostUsd())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("branchStockValueZwg", branchStockRows.stream()
                .map(stock -> nvl(stock.getQuantityOnHand()).multiply(nvl(stock.getAverageCostZwg())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void addCategoryManagementModel(Model model, Long tenantId, String search, int page, int size) {
        String cleanSearch = search == null || search.isBlank() ? null : search.toLowerCase();
        List<ProductCategory> allCategories = categories.findByTenantIdOrderBySortOrderAsc(tenantId);
        List<ProductCategory> categoryList = allCategories.stream()
                .filter(category -> cleanSearch == null
                        || contains(category.getName(), cleanSearch)
                        || contains(category.getCode(), cleanSearch)
                        || contains(category.getDescription(), cleanSearch))
                .toList();
        List<Product> activeProducts = products.findByTenantIdAndIsActiveTrue(tenantId);
        Map<Long, Long> productCounts = new HashMap<>();
        activeProducts.stream()
                .filter(product -> product.getCategory() != null)
                .forEach(product -> productCounts.merge(product.getCategory().getId(), 1L, Long::sum));
        Page<ProductCategory> categoryPage = pageList(categoryList, page, size);
        model.addAttribute("categoryList", categoryPage.getContent());
        model.addAttribute("categoryPage", categoryPage);
        addPaginationModel(model, "category", categoryPage, "/shop/categories", params("search", search));
        model.addAttribute("categorySearch", search);
        model.addAttribute("categoryProductCounts", productCounts);
        model.addAttribute("parentCategoryById", allCategories.stream().collect(java.util.stream.Collectors.toMap(ProductCategory::getId, ProductCategory::getName)));
        model.addAttribute("activeCategoryCount", categoryList.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count());
        model.addAttribute("emptyCategoryCount", categoryList.stream().filter(c -> !productCounts.containsKey(c.getId())).count());
    }

    private void addInventoryManagementModel(Model model, Long tenantId, Long selectedBranchId, String search,
                                             String status, int page, int size) {
        Map<Long, Product> productById = products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        String cleanSearch = search == null || search.isBlank() ? null : search.toLowerCase();
        String cleanStatus = status == null || status.isBlank() ? null : status.toUpperCase();
        List<Inventory> branchInventory = inventory.findByTenantIdAndBranchId(tenantId, selectedBranchId).stream()
                .filter(item -> productById.containsKey(item.getProductId()))
                .filter(item -> {
                    if (cleanSearch == null) return true;
                    Product product = productById.get(item.getProductId());
                    if (product == null) return false;
                    return contains(product.getName(), cleanSearch)
                            || contains(product.getSku(), cleanSearch)
                            || contains(product.getBarcode(), cleanSearch);
                })
                .filter(item -> {
                    if (cleanStatus == null || "ALL".equals(cleanStatus)) return true;
                    boolean lowStock = isLowStock(productById.get(item.getProductId()), item);
                    return "LOW_STOCK".equals(cleanStatus) ? lowStock
                            : !"HEALTHY".equals(cleanStatus) || !lowStock;
                })
                .toList();
        List<Product> branchProductOptions = inventory.findBranchProductStock(tenantId, selectedBranchId, null, null).stream()
                .map(item -> productById.get(item.getProductId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        Page<Inventory> inventoryPage = pageList(branchInventory, page, size);
        model.addAttribute("inventoryItems", inventoryPage.getContent());
        model.addAttribute("inventoryPage", inventoryPage);
        addPaginationModel(model, "inventory", inventoryPage, "/shop/inventory", params(
                "branchId", selectedBranchId,
                "search", search,
                "status", status
        ));
        model.addAttribute("inventoryProductById", productById);
        model.addAttribute("branchProductOptions", branchProductOptions);
        model.addAttribute("inventorySearch", search);
        model.addAttribute("inventoryStatus", status);
        model.addAttribute("selectedBranchName", branchById(tenantId).get(selectedBranchId));
        model.addAttribute("summaryTotalProducts", branchInventory.size());
        model.addAttribute("summaryLowStock", branchInventory.stream().filter(item -> isLowStock(productById.get(item.getProductId()), item)).count());
        model.addAttribute("summaryReserved", branchInventory.stream()
                .map(item -> nvl(item.getQuantityReserved()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("summaryValueUsd", branchInventory.stream()
                .map(item -> nvl(item.getQuantityOnHand()).multiply(nvl(item.getAverageCostUsd())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("summaryValueZwg", branchInventory.stream()
                .map(item -> nvl(item.getQuantityOnHand()).multiply(nvl(item.getAverageCostZwg())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<InventoryTransaction> recentMovements = inventoryTransactions
                .findByTenantIdAndBranchId(tenantId, selectedBranchId, PageRequest.of(0, 20))
                .getContent();
        List<PurchaseOrder> branchOrders = purchaseOrders
                .findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, selectedBranchId);
        model.addAttribute("recentInventoryMovements", recentMovements);
        model.addAttribute("pendingReceivingCount", branchOrders.stream()
                .filter(order -> order.getStatus() == PurchaseOrder.PoStatus.APPROVED
                        || order.getStatus() == PurchaseOrder.PoStatus.ORDERED
                        || order.getStatus() == PurchaseOrder.PoStatus.PARTIAL)
                .count());
        model.addAttribute("openPurchaseOrderCount", branchOrders.stream()
                .filter(order -> order.getStatus() != PurchaseOrder.PoStatus.RECEIVED
                        && order.getStatus() != PurchaseOrder.PoStatus.CANCELLED
                        && order.getStatus() != PurchaseOrder.PoStatus.REJECTED)
                .count());
    }

    private void addSalesHistoryModel(Model model, Long tenantId, Long selectedBranchId, String search,
                                      String from, String to, String status, Long cashierId, int page, int size) {
        LocalDateTime fromDate = parseDate(from, false);
        LocalDateTime toDate = parseDate(to, true);
        Sale.SaleStatus saleStatus = parseSaleStatus(status);
        var salesPage = sales.searchSales(tenantId, selectedBranchId, fromDate, toDate, saleStatus, cashierId, blank(search), pageRequest(page, size));
        List<Sale> saleList = salesPage.getContent();
        List<Sale> filteredSales = sales.searchSalesList(tenantId, selectedBranchId,
                fromDate, toDate, saleStatus, cashierId, blank(search));
        Map<Long, String> cashierNames = userNamesById(tenantId);
        List<CashierSalesSummary> cashierSummaries = cashierSalesSummaries(filteredSales, cashierNames);
        Map<Long, List<Sale>> cashierSales = filteredSales.stream()
                .collect(Collectors.groupingBy(Sale::getCashierId, LinkedHashMap::new, Collectors.toList()));
        LocalDateTime summaryStart = fromDate == null ? LocalDate.now().atStartOfDay() : fromDate;
        LocalDateTime summaryEnd = toDate == null ? summaryStart.plusDays(1) : toDate;
        model.addAttribute("salesPage", salesPage);
        addPaginationModel(model, "sale", salesPage, "/shop/sales", params(
                "branchId", selectedBranchId,
                "search", search,
                "from", from,
                "to", to,
                "status", status,
                "cashierId", cashierId
        ));
        model.addAttribute("saleList", saleList);
        model.addAttribute("saleSearch", search);
        model.addAttribute("dateFrom", from);
        model.addAttribute("dateTo", to);
        model.addAttribute("statusFilter", status);
        model.addAttribute("cashierFilter", cashierId);
        model.addAttribute("cashierById", cashierNames);
        model.addAttribute("paymentsBySale", paymentsBySale(saleList));
        model.addAttribute("cashierSummaries", cashierSummaries);
        model.addAttribute("cashierSales", cashierSales);
        model.addAttribute("cashierPaymentsBySale", paymentsBySale(filteredSales));
        model.addAttribute("branchCashiers", users.findByTenantId(tenantId).stream()
                .filter(user -> selectedBranchId.equals(user.getBranchId()))
                .filter(user -> hasRole(user, UserRole.CASHIER))
                .toList());
        model.addAttribute("summaryRevenueUsd", filteredSales.stream()
                .filter(sale -> CurrencyCode.USD.equals(sale.getCurrency()))
                .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                .map(Sale::getGrandTotal).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("summaryRevenueZwg", filteredSales.stream()
                .filter(sale -> CurrencyCode.ZWG.equals(sale.getCurrency()))
                .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                .map(Sale::getGrandTotal).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("summaryTransactions", filteredSales.size());
        model.addAttribute("summaryVoided", filteredSales.stream().filter(sale -> Sale.SaleStatus.VOIDED.equals(sale.getStatus())).count());
        model.addAttribute("summaryToday", sales.sumGrandTotal(tenantId, selectedBranchId, summaryStart, summaryEnd));
    }

    private List<CashierSalesSummary> cashierSalesSummaries(List<Sale> filteredSales,
                                                             Map<Long, String> cashierNames) {
        return filteredSales.stream()
                .collect(Collectors.groupingBy(Sale::getCashierId, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<Sale> cashierSales = entry.getValue();
                    BigDecimal revenueUsd = cashierSales.stream()
                            .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                            .filter(sale -> CurrencyCode.USD.equals(sale.getCurrency()))
                            .map(Sale::getGrandTotal).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal revenueZwg = cashierSales.stream()
                            .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                            .filter(sale -> CurrencyCode.ZWG.equals(sale.getCurrency()))
                            .map(Sale::getGrandTotal).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
                    long completed = cashierSales.stream()
                            .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus())).count();
                    long completedUsd = cashierSales.stream()
                            .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                            .filter(sale -> CurrencyCode.USD.equals(sale.getCurrency())).count();
                    long voided = cashierSales.stream()
                            .filter(sale -> Sale.SaleStatus.VOIDED.equals(sale.getStatus())).count();
                    int items = cashierSales.stream().mapToInt(sale -> sale.getItems().size()).sum();
                    BigDecimal averageUsd = completedUsd == 0 ? BigDecimal.ZERO
                            : revenueUsd.divide(BigDecimal.valueOf(completedUsd), 2, RoundingMode.HALF_UP);
                    LocalDateTime lastSale = cashierSales.stream().map(Sale::getCreatedAt)
                            .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
                    return new CashierSalesSummary(entry.getKey(),
                            cashierNames.getOrDefault(entry.getKey(), "Cashier " + entry.getKey()),
                            cashierSales.size(), completed, voided, items,
                            revenueUsd, revenueZwg, averageUsd, lastSale);
                })
                .sorted(Comparator.comparing(CashierSalesSummary::getRevenueUsd).reversed()
                        .thenComparing(CashierSalesSummary::getName))
                .toList();
    }

    private void addCashDrawerModel(Model model, Long tenantId, Long selectedBranchId, Long collectShiftId, int page, int size) {
        List<CashDrawer> drawerList = drawers.findByTenantIdAndBranchId(tenantId, selectedBranchId);
        Page<CashSession> sessionPage = cashSessions.findByTenantIdAndBranchId(tenantId, selectedBranchId, pageRequest(page, size));
        List<CashSession> sessionList = sessionPage.getContent();
        List<CashSession> openSessions = cashSessions.findAllByTenantIdAndBranchIdAndStatus(tenantId, selectedBranchId, CashSession.SessionStatus.OPEN);
        List<CashSession> uncollectedSessions = cashSessions.findUncollectedClosedSessions(tenantId, selectedBranchId);
        Map<Long, List<CashMovement>> movementsBySession = new HashMap<>();
        Map<Long, List<Sale>> salesBySession = new HashMap<>();
        Map<Long, Long> completedSalesBySession = new HashMap<>();
        Map<Long, BigDecimal> grossRevenueUsdBySession = new HashMap<>();
        Map<Long, BigDecimal> grossRevenueZwgBySession = new HashMap<>();
        Map<Long, BigDecimal> netRevenueUsdBySession = new HashMap<>();
        Map<Long, BigDecimal> netRevenueZwgBySession = new HashMap<>();
        Map<Long, BigDecimal> grossProfitUsdBySession = new HashMap<>();
        Map<Long, BigDecimal> grossProfitZwgBySession = new HashMap<>();
        Map<Long, BigDecimal> costUsdBySession = new HashMap<>();
        Map<Long, BigDecimal> costZwgBySession = new HashMap<>();
        sessionList.forEach(session -> movementsBySession.put(session.getId(), cashMovements.findByTenantIdAndSessionId(tenantId, session.getId())));
        sessionList.forEach(session -> {
            List<Sale> sessionSales = sales.findShiftSales(tenantId, selectedBranchId, session.getCashierId(), session.getId());
            List<Sale> completedSales = sessionSales.stream()
                    .filter(sale -> Sale.SaleStatus.COMPLETED.equals(sale.getStatus()))
                    .toList();
            salesBySession.put(session.getId(), sessionSales);
            completedSalesBySession.put(session.getId(), (long) completedSales.size());
            grossRevenueUsdBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.USD, Sale::getGrandTotal));
            grossRevenueZwgBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.ZWG, Sale::getGrandTotal));
            netRevenueUsdBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.USD, Sale::getGrandTotal));
            netRevenueZwgBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.ZWG, Sale::getGrandTotal));
            grossProfitUsdBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.USD, Sale::getGrossProfit));
            grossProfitZwgBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.ZWG, Sale::getGrossProfit));
            costUsdBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.USD, Sale::getTotalCost));
            costZwgBySession.put(session.getId(), sumSales(completedSales, CurrencyCode.ZWG, Sale::getTotalCost));
        });
        model.addAttribute("drawerList", drawerList);
        model.addAttribute("sessionList", sessionList);
        model.addAttribute("sessionPage", sessionPage);
        addPaginationModel(model, "session", sessionPage, "/shop/cash", params("branchId", selectedBranchId));
        model.addAttribute("openSessions", openSessions);
        model.addAttribute("uncollectedSessions", uncollectedSessions);
        model.addAttribute("movementsBySession", movementsBySession);
        model.addAttribute("salesBySession", salesBySession);
        model.addAttribute("completedSalesBySession", completedSalesBySession);
        model.addAttribute("sessionGrossRevenueUsd", grossRevenueUsdBySession);
        model.addAttribute("sessionGrossRevenueZwg", grossRevenueZwgBySession);
        model.addAttribute("sessionNetRevenueUsd", netRevenueUsdBySession);
        model.addAttribute("sessionNetRevenueZwg", netRevenueZwgBySession);
        model.addAttribute("sessionGrossProfitUsd", grossProfitUsdBySession);
        model.addAttribute("sessionGrossProfitZwg", grossProfitZwgBySession);
        model.addAttribute("sessionCostUsd", costUsdBySession);
        model.addAttribute("sessionCostZwg", costZwgBySession);
        model.addAttribute("sessionPaymentsBySale", paymentsBySale(salesBySession.values().stream().flatMap(List::stream).toList()));
        model.addAttribute("cashierById", userNamesById(tenantId));
        model.addAttribute("drawerById", drawerList.stream().collect(java.util.stream.Collectors.toMap(CashDrawer::getId, CashDrawer::getName)));
        model.addAttribute("drawerSessionCounts", sessionList.stream().collect(java.util.stream.Collectors.groupingBy(CashSession::getDrawerId, java.util.stream.Collectors.counting())));
        model.addAttribute("drawerOpenSessionCounts", sessionList.stream()
                .filter(session -> CashSession.SessionStatus.OPEN.equals(session.getStatus()))
                .collect(java.util.stream.Collectors.groupingBy(CashSession::getDrawerId, java.util.stream.Collectors.counting())));
        model.addAttribute("activeDrawerCount", drawerList.stream().filter(drawer -> Boolean.TRUE.equals(drawer.getIsActive())).count());
        model.addAttribute("openSessionCount", (long) openSessions.size());
        model.addAttribute("uncollectedSessionCount", (long) uncollectedSessions.size());
        model.addAttribute("expectedCashUsd", uncollectedSessions.stream().map(CashSession::getExpectedCashUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("expectedCashZwg", uncollectedSessions.stream().map(CashSession::getExpectedCashZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("closedSessionCount", sessionList.stream().filter(session -> CashSession.SessionStatus.CLOSED.equals(session.getStatus())).count());
        model.addAttribute("collectedCashUsd", sessionList.stream().map(CashSession::getCollectedCashUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("collectedCashZwg", sessionList.stream().map(CashSession::getCollectedCashZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("varianceUsd", sessionList.stream().map(CashSession::getCollectionVarianceUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("varianceZwg", sessionList.stream().map(CashSession::getCollectionVarianceZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("lastCashSession", sessionList.stream()
                .filter(session -> session.getOpenedAt() != null)
                .max(Comparator.comparing(CashSession::getOpenedAt))
                .orElse(null));
        model.addAttribute("collectShiftId", collectShiftId);
        if (collectShiftId != null) {
            cashSessions.findById(collectShiftId)
                    .filter(session -> session.getTenantId().equals(tenantId))
                    .filter(session -> session.getBranchId().equals(selectedBranchId))
                    .ifPresentOrElse(session -> {
                        if (CashSession.SessionStatus.OPEN.equals(session.getStatus())) {
                            model.addAttribute("collectShiftLookupMessage", "Shift #" + collectShiftId + " is still open. The cashier must close it from the POS app first.");
                        } else if (Boolean.TRUE.equals(session.getCashCollected())) {
                            model.addAttribute("collectShiftLookupMessage", "Shift #" + collectShiftId + " has already been collected.");
                        } else {
                            model.addAttribute("collectShiftPreview", session);
                        }
                    }, () -> model.addAttribute("collectShiftLookupMessage", "Shift #" + collectShiftId + " was not found for this branch."));
        }
    }

    private void addReturnsModel(Model model, Long tenantId, Long selectedBranchId, String search, String from, String to, int page, int size) {
        LocalDateTime fromDate = parseDate(from, false);
        LocalDateTime toDate = parseDate(to, true);
        var returnsPage = returns.searchReturns(tenantId, selectedBranchId, fromDate, toDate, blank(search), pageRequest(page, size));
        List<Return> returnList = returnsPage.getContent();
        List<Sale> returnableSales = sales.searchSales(tenantId, selectedBranchId, null, null, Sale.SaleStatus.COMPLETED, null, null, PageRequest.of(0, 100)).getContent();
        model.addAttribute("returnsPage", returnsPage);
        addPaginationModel(model, "return", returnsPage, "/shop/returns", params(
                "branchId", selectedBranchId,
                "search", search,
                "from", from,
                "to", to
        ));
        model.addAttribute("returnList", returnList);
        model.addAttribute("returnableSales", returnableSales);
        model.addAttribute("returnSearch", search);
        model.addAttribute("dateFrom", from);
        model.addAttribute("dateTo", to);
        model.addAttribute("cashierById", userNamesById(tenantId));
        model.addAttribute("summaryReturnCount", returnList.size());
        model.addAttribute("summaryRefundUsd", returnList.stream().filter(ret -> CurrencyCode.USD.equals(ret.getCurrency())).map(Return::getTotalRefund).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("summaryRefundZwg", returnList.stream().filter(ret -> CurrencyCode.ZWG.equals(ret.getCurrency())).map(Return::getTotalRefund).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("approvalCount", returnList.stream().filter(ret -> Boolean.TRUE.equals(ret.getRequiresApproval()) && !Boolean.TRUE.equals(ret.getIsApproved())).count());
    }

    private void addPurchasingModel(Model model, Long tenantId, Long selectedBranchId, String search, String status, int page, int size) {
        String cleanSearch = blank(search);
        PurchaseOrder.PoStatus poStatus = parsePoStatus(status);
        List<PurchaseOrder> filteredOrders = purchaseOrders.findByTenantIdAndBranchId(tenantId, selectedBranchId, PageRequest.of(0, 1000)).getContent().stream()
                .filter(po -> poStatus == null || poStatus.equals(po.getStatus()))
                .filter(po -> cleanSearch == null
                        || contains(po.getPoNumber(), cleanSearch.toLowerCase())
                        || supplierName(tenantId, po.getSupplierId()).toLowerCase().contains(cleanSearch.toLowerCase()))
                .toList();
        Page<PurchaseOrder> purchaseOrderPage = pageList(filteredOrders, page, size);
        List<PurchaseOrder> orderList = purchaseOrderPage.getContent();
        Map<Long, Supplier> supplierById = suppliers.findByTenantId(tenantId, PageRequest.of(0, 500)).getContent().stream()
                .collect(java.util.stream.Collectors.toMap(Supplier::getId, supplier -> supplier));
        Map<Long, Product> productById = products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, product -> product));
        Map<Long, List<PurchaseOrderItem>> poItemsById = new HashMap<>();
        Map<Long, Integer> grnCounts = new HashMap<>();
        for (PurchaseOrder po : orderList) {
            poItemsById.put(po.getId(), purchaseOrderItems.findByPurchaseOrderId(po.getId()));
            grnCounts.put(po.getId(), goodsReceivedNotes.findByPurchaseOrderId(po.getId()).size());
        }
        List<Product> branchProductOptions = inventory.findBranchProductStock(tenantId, selectedBranchId, null, null).stream()
                .map(item -> productById.get(item.getProductId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        model.addAttribute("purchaseOrderList", orderList);
        model.addAttribute("purchaseOrderPage", purchaseOrderPage);
        addPaginationModel(model, "purchaseOrder", purchaseOrderPage, "/shop/purchasing", params(
                "branchId", selectedBranchId,
                "search", search,
                "status", status
        ));
        model.addAttribute("purchaseOrderItemsById", poItemsById);
        model.addAttribute("purchaseOrderStatuses", PurchaseOrder.PoStatus.values());
        model.addAttribute("poStatusFilter", status);
        model.addAttribute("poSearch", search);
        model.addAttribute("activeSuppliers", suppliers.findByTenantIdAndIsActiveTrue(tenantId));
        model.addAttribute("branchProductOptions", branchProductOptions);
        model.addAttribute("supplierById", supplierById);
        model.addAttribute("productById", productById);
        model.addAttribute("grnCountsByPo", grnCounts);
        model.addAttribute("draftPoCount", orderList.stream().filter(po -> PurchaseOrder.PoStatus.DRAFT.equals(po.getStatus())).count());
        model.addAttribute("approvalPoCount", orderList.stream().filter(po -> PurchaseOrder.PoStatus.SUBMITTED.equals(po.getStatus())).count());
        model.addAttribute("openPoCount", orderList.stream()
                .filter(po -> !PurchaseOrder.PoStatus.RECEIVED.equals(po.getStatus()))
                .filter(po -> !PurchaseOrder.PoStatus.CANCELLED.equals(po.getStatus()))
                .count());
        model.addAttribute("receivedPoCount", orderList.stream().filter(po -> PurchaseOrder.PoStatus.RECEIVED.equals(po.getStatus())).count());
        model.addAttribute("purchaseValueUsd", orderList.stream().map(PurchaseOrder::getTotalUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("purchaseValueZwg", orderList.stream().map(PurchaseOrder::getTotalZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void addReportsModel(Model model, Long tenantId, Long selectedBranchId, String from, String to) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        model.addAttribute("tenant", tenant);
        model.addAttribute("tenantPlan", tenant.getPlanId() == null ? null : plans.findById(tenant.getPlanId()).orElse(null));
        LocalDateTime fromDate = parseDate(from, false);
        LocalDateTime toDate = parseDate(to, true);
        if (fromDate == null) {
            fromDate = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        if (toDate == null) {
            toDate = LocalDate.now().plusDays(1).atStartOfDay();
        }
        final LocalDateTime reportFrom = fromDate;
        final LocalDateTime reportTo = toDate;

        List<Sale> periodSales = sales.searchSalesList(tenantId, selectedBranchId, reportFrom, reportTo, null, null, null);
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(reportFrom.toLocalDate(), reportTo.toLocalDate()));
        LocalDateTime previousFrom = reportFrom.minusDays(periodDays);
        List<Sale> previousSales = sales.searchSalesList(tenantId, selectedBranchId, previousFrom, reportFrom, null, null, null);
        List<Return> periodReturns = returns.searchReturnsList(tenantId, selectedBranchId, reportFrom, reportTo);
        List<Return> previousReturns = returns.searchReturnsList(tenantId, selectedBranchId, previousFrom, reportFrom);
        Map<Long, Sale> originalSalesById = returnOriginSales(periodReturns, previousReturns);
        List<Sale> completedSales = financeReportCalculator.reportableSales(periodSales);
        List<Return> recognizedReturns = financeReportCalculator.reportableReturns(periodReturns);
        var financeTotals = financeReportCalculator.calculate(periodSales, periodReturns, originalSalesById);
        var previousFinanceTotals = financeReportCalculator.calculate(previousSales, previousReturns, originalSalesById);
        var usdTotals = financeTotals.usd();
        var zwgTotals = financeTotals.zwg();
        List<Inventory> branchInventory = inventory.findByTenantIdAndBranchId(tenantId, selectedBranchId);
        List<CashSession> branchSessions = cashSessions.findAllByTenantIdAndBranchIdOrderByOpenedAtDesc(tenantId, selectedBranchId);
        List<CashSession> periodSessions = branchSessions.stream()
                .filter(session -> session.getOpenedAt() != null && !session.getOpenedAt().isBefore(reportFrom) && session.getOpenedAt().isBefore(reportTo))
                .toList();
        List<PurchaseOrder> branchOrders = purchaseOrders.findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, selectedBranchId);
        List<PurchaseOrder> periodOrders = branchOrders.stream()
                .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().isBefore(reportFrom) && order.getCreatedAt().isBefore(reportTo))
                .toList();

        BigDecimal revenueUsd = usdTotals.grossSales();
        BigDecimal revenueZwg = zwgTotals.grossSales();
        BigDecimal cogsUsd = usdTotals.netCogs();
        BigDecimal cogsZwg = zwgTotals.netCogs();
        BigDecimal grossProfitUsd = usdTotals.grossProfit();
        BigDecimal grossProfitZwg = zwgTotals.grossProfit();
        BigDecimal discountsUsd = usdTotals.discounts();
        BigDecimal discountsZwg = zwgTotals.discounts();
        BigDecimal taxUsd = usdTotals.taxCollected();
        BigDecimal taxZwg = zwgTotals.taxCollected();
        BigDecimal refundsUsd = usdTotals.refunds();
        BigDecimal refundsZwg = zwgTotals.refunds();
        BigDecimal netSalesUsd = usdTotals.netSales();
        BigDecimal netSalesZwg = zwgTotals.netSales();
        BigDecimal previousRevenueUsd = previousFinanceTotals.usd().netSales();
        BigDecimal previousProfitUsd = previousFinanceTotals.usd().grossProfit();
        BigDecimal averageBasketUsd = netSalesUsd;
        long usdSalesCount = completedSales.stream().filter(sale -> CurrencyCode.USD.equals(sale.getCurrency())).count();
        averageBasketUsd = usdSalesCount == 0 ? BigDecimal.ZERO : averageBasketUsd.divide(BigDecimal.valueOf(usdSalesCount), 2, RoundingMode.HALF_UP);
        BigDecimal profitMarginUsd = usdTotals.grossMargin();
        BigDecimal netMarginUsd = usdTotals.netMargin();
        BigDecimal netProfitUsd = usdTotals.netProfit();
        BigDecimal netProfitZwg = zwgTotals.netProfit();
        BigDecimal revenueChangePct = percent(netSalesUsd.subtract(previousRevenueUsd), previousRevenueUsd);
        BigDecimal profitChangePct = percent(grossProfitUsd.subtract(previousProfitUsd), previousProfitUsd);

        BigDecimal cashCollectedUsd = salePayments.sumCashCollected(tenantId, selectedBranchId, CurrencyCode.USD, reportFrom, reportTo);
        BigDecimal cashCollectedZwg = salePayments.sumCashCollected(tenantId, selectedBranchId, CurrencyCode.ZWG, reportFrom, reportTo);
        BigDecimal cashRefundsUsd = recognizedReturns.stream()
                .filter(ret -> Return.RefundMethod.CASH.equals(ret.getRefundMethod()))
                .filter(ret -> CurrencyCode.USD.equals(ret.getCurrency()))
                .map(Return::getTotalRefund).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashRefundsZwg = recognizedReturns.stream()
                .filter(ret -> Return.RefundMethod.CASH.equals(ret.getRefundMethod()))
                .filter(ret -> CurrencyCode.ZWG.equals(ret.getCurrency()))
                .map(Return::getTotalRefund).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netCashMovementUsd = cashCollectedUsd.subtract(cashRefundsUsd);
        BigDecimal netCashMovementZwg = cashCollectedZwg.subtract(cashRefundsZwg);
        BigDecimal openExpectedCashUsd = periodSessions.stream()
                .filter(session -> CashSession.SessionStatus.OPEN.equals(session.getStatus()))
                .map(CashSession::getExpectedCashUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openExpectedCashZwg = periodSessions.stream()
                .filter(session -> CashSession.SessionStatus.OPEN.equals(session.getStatus()))
                .map(CashSession::getExpectedCashZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal countedCashUsd = periodSessions.stream()
                .map(session -> Boolean.TRUE.equals(session.getCashCollected()) ? session.getCollectedCashUsd() : session.getActualCashUsd())
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal countedCashZwg = periodSessions.stream()
                .map(session -> Boolean.TRUE.equals(session.getCashCollected()) ? session.getCollectedCashZwg() : session.getActualCashZwg())
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashAtHandUsd = countedCashUsd.add(openExpectedCashUsd);
        BigDecimal cashAtHandZwg = countedCashZwg.add(openExpectedCashZwg);
        BigDecimal varianceUsd = periodSessions.stream()
                .map(session -> Boolean.TRUE.equals(session.getCashCollected()) ? session.getCollectionVarianceUsd() : session.getVarianceUsd())
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal varianceZwg = periodSessions.stream()
                .map(session -> Boolean.TRUE.equals(session.getCashCollected()) ? session.getCollectionVarianceZwg() : session.getVarianceZwg())
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal inventoryValueUsd = branchInventory.stream()
                .map(item -> nvl(item.getQuantityOnHand()).multiply(nvl(item.getAverageCostUsd())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inventoryValueZwg = branchInventory.stream()
                .map(item -> nvl(item.getQuantityOnHand()).multiply(nvl(item.getAverageCostZwg())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<PurchaseOrder.PoStatus> commitmentStatuses = Set.of(
                PurchaseOrder.PoStatus.APPROVED,
                PurchaseOrder.PoStatus.ORDERED,
                PurchaseOrder.PoStatus.PARTIAL
        );
        List<PurchaseOrder> openCommitments = branchOrders.stream()
                .filter(order -> commitmentStatuses.contains(order.getStatus()))
                .toList();
        BigDecimal openPoUsd = openCommitments.stream()
                .map(PurchaseOrder::getTotalUsd).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal openPoZwg = openCommitments.stream()
                .map(PurchaseOrder::getTotalZwg).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPositionUsd = cashAtHandUsd.add(inventoryValueUsd).subtract(openPoUsd);
        BigDecimal netPositionZwg = cashAtHandZwg.add(inventoryValueZwg).subtract(openPoZwg);

        Map<String, BigDecimal> paymentMethodTotals = new LinkedHashMap<>();
        for (Object[] row : salePayments.sumByPaymentMethod(tenantId, selectedBranchId, reportFrom, reportTo)) {
            paymentMethodTotals.put(row[0] + " " + row[1], row[2] == null ? BigDecimal.ZERO : (BigDecimal) row[2]);
        }
        Map<String, Long> salesByStatus = new LinkedHashMap<>();
        for (Sale.SaleStatus saleStatus : Sale.SaleStatus.values()) {
            salesByStatus.put(saleStatus.name(), periodSales.stream().filter(sale -> saleStatus.equals(sale.getStatus())).count());
        }
        Map<String, Long> poByStatus = new LinkedHashMap<>();
        for (PurchaseOrder.PoStatus poStatus : PurchaseOrder.PoStatus.values()) {
            poByStatus.put(poStatus.name(), periodOrders.stream().filter(order -> poStatus.equals(order.getStatus())).count());
        }

        Map<Long, Product> productById = products.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        List<ProductPerformance> productPerformance = productPerformance(completedSales, recognizedReturns, originalSalesById).stream()
                .sorted(Comparator.comparing(ProductPerformance::getProfitUsd).reversed()
                        .thenComparing(Comparator.comparing(ProductPerformance::getRevenueUsd).reversed()))
                .limit(10)
                .toList();
        List<ProductPerformance> slowMovers = branchInventory.stream()
                .filter(stock -> nvl(stock.getQuantityOnHand()).compareTo(BigDecimal.ZERO) > 0)
                .filter(stock -> productPerformance.stream().noneMatch(row -> row.getProductId().equals(stock.getProductId())))
                .map(stock -> {
                    Product product = productById.get(stock.getProductId());
                    return new ProductPerformance(stock.getProductId(), product == null ? "Product " + stock.getProductId() : product.getName(),
                            product == null ? "" : product.getSku(), nvl(stock.getQuantityOnHand()),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
                })
                .sorted(Comparator.comparing(ProductPerformance::getQuantity).reversed())
                .limit(8)
                .toList();
        List<TrendPoint> dailyTrend = dailyTrend(completedSales, recognizedReturns, reportFrom.toLocalDate(), reportTo.toLocalDate());
        BigDecimal trendMax = dailyTrend.stream().map(TrendPoint::getRevenueUsd).map(BigDecimal::abs).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        dailyTrend.forEach(point -> point.setPercent(barPercent(point.getRevenueUsd(), trendMax)));
        List<TrendPoint> hourlyTrend = hourlyTrend(completedSales);
        BigDecimal hourlyMax = hourlyTrend.stream().map(TrendPoint::getRevenueUsd).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        hourlyTrend.forEach(point -> point.setPercent(barPercent(point.getRevenueUsd(), hourlyMax)));

        Map<Long, String> cashierNames = userNamesById(tenantId);
        List<ShiftReportRow> shiftRows = periodSessions.stream()
                .map(session -> {
                    List<Sale> sessionSales = completedSales.stream()
                            .filter(sale -> session.getId().equals(sale.getCashSessionId()))
                            .toList();
                    BigDecimal sessionRevenueUsd = sumSales(sessionSales, CurrencyCode.USD, Sale::getGrandTotal);
                    BigDecimal sessionProfitUsd = sumSales(sessionSales, CurrencyCode.USD, Sale::getSubtotal)
                            .subtract(sumSales(sessionSales, CurrencyCode.USD, Sale::getTotalCost));
                    BigDecimal sessionRevenueZwg = sumSales(sessionSales, CurrencyCode.ZWG, Sale::getGrandTotal);
                    BigDecimal sessionProfitZwg = sumSales(sessionSales, CurrencyCode.ZWG, Sale::getSubtotal)
                            .subtract(sumSales(sessionSales, CurrencyCode.ZWG, Sale::getTotalCost));
                    BigDecimal sessionVarianceUsd = Boolean.TRUE.equals(session.getCashCollected())
                            ? nvl(session.getCollectionVarianceUsd()) : nvl(session.getVarianceUsd());
                    BigDecimal sessionVarianceZwg = Boolean.TRUE.equals(session.getCashCollected())
                            ? nvl(session.getCollectionVarianceZwg()) : nvl(session.getVarianceZwg());
                    return new ShiftReportRow(session.getId(), cashierNames.getOrDefault(session.getCashierId(), "Cashier " + session.getCashierId()),
                            session.getStatus().name(), sessionSales.size(), sessionRevenueUsd, sessionRevenueZwg,
                            sessionProfitUsd, sessionProfitZwg, sessionVarianceUsd, sessionVarianceZwg,
                            session.getOpenedAt(), session.getClosedAt());
                })
                .sorted(Comparator.comparing(ShiftReportRow::getOpenedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        List<Borrower> allBorrowers = borrowerRepository.search(tenantId, null, PageRequest.of(0, 1000)).getContent();
        BigDecimal borrowerOutstandingUsd = allBorrowers.stream().filter(b -> CurrencyCode.USD.equals(b.getCurrency()))
                .map(Borrower::getCurrentBalance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal borrowerOutstandingZwg = allBorrowers.stream().filter(b -> CurrencyCode.ZWG.equals(b.getCurrency()))
                .map(Borrower::getCurrentBalance).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Borrower> topBorrowers = allBorrowers.stream()
                .filter(b -> nvl(b.getCurrentBalance()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(Borrower::getCurrentBalance, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .toList();
        List<HeldChange> openChange = heldChangeRepository.search(tenantId, HeldChange.Status.OPEN, null, PageRequest.of(0, 1000)).getContent().stream()
                .filter(change -> selectedBranchId.equals(change.getBranchId()))
                .toList();
        BigDecimal heldChangeUsd = openChange.stream().filter(change -> CurrencyCode.USD.equals(change.getCurrency()))
                .map(HeldChange::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal heldChangeZwg = openChange.stream().filter(change -> CurrencyCode.ZWG.equals(change.getCurrency()))
                .map(HeldChange::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentAssetsUsd = cashAtHandUsd.add(inventoryValueUsd).add(borrowerOutstandingUsd);
        BigDecimal currentAssetsZwg = cashAtHandZwg.add(inventoryValueZwg).add(borrowerOutstandingZwg);
        BigDecimal currentLiabilitiesUsd = openPoUsd.add(heldChangeUsd);
        BigDecimal currentLiabilitiesZwg = openPoZwg.add(heldChangeZwg);
        netPositionUsd = currentAssetsUsd.subtract(currentLiabilitiesUsd);
        netPositionZwg = currentAssetsZwg.subtract(currentLiabilitiesZwg);
        BigDecimal currentRatioUsd = currentLiabilitiesUsd.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : currentAssetsUsd.divide(currentLiabilitiesUsd, 2, RoundingMode.HALF_UP);
        BigDecimal cashCoverageUsd = currentLiabilitiesUsd.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.valueOf(100)
                : percent(cashAtHandUsd, currentLiabilitiesUsd);
        BigDecimal liabilityExposureUsd = currentAssetsUsd.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : percent(currentLiabilitiesUsd, currentAssetsUsd);
        boolean hasCurrentLiabilitiesUsd = currentLiabilitiesUsd.compareTo(BigDecimal.ZERO) > 0;
        String financialHealth = netPositionUsd.compareTo(BigDecimal.ZERO) >= 0 && cashCoverageUsd.compareTo(BigDecimal.valueOf(50)) >= 0
                ? "Healthy" : netPositionUsd.compareTo(BigDecimal.ZERO) >= 0 ? "Watch" : "At risk";
        List<InventoryLot> expiringLots = inventoryLots.findByTenantIdAndBranchIdAndExpiryDateBeforeAndStatus(
                tenantId, selectedBranchId, LocalDate.now().plusDays(30), InventoryLot.Status.AVAILABLE);
        long openVarianceCount = varianceInvestigations.countByTenantIdAndBranchIdAndStatus(tenantId, selectedBranchId, StockVarianceInvestigation.Status.OPEN);
        List<InventoryRiskRow> inventoryRisks = inventoryRisks(branchInventory, productById).stream().limit(10).toList();

        List<Insight> insights = buildReportInsights(revenueUsd, revenueChangePct, grossProfitUsd, profitMarginUsd,
                varianceUsd, borrowerOutstandingUsd, heldChangeUsd, expiringLots.size(), openVarianceCount,
                dailyTrend, hourlyTrend, productPerformance, inventoryRisks);

        model.addAttribute("dateFrom", reportFrom.toLocalDate().toString());
        model.addAttribute("dateTo", reportTo.minusDays(1).toLocalDate().toString());
        model.addAttribute("completedSales", completedSales);
        model.addAttribute("reportSalesCount", completedSales.size());
        model.addAttribute("reportReturnCount", recognizedReturns.size());
        model.addAttribute("reportPoCount", periodOrders.size());
        model.addAttribute("revenueUsd", revenueUsd);
        model.addAttribute("revenueZwg", revenueZwg);
        model.addAttribute("cogsUsd", cogsUsd);
        model.addAttribute("cogsZwg", cogsZwg);
        model.addAttribute("grossCogsUsd", usdTotals.grossCogs());
        model.addAttribute("grossCogsZwg", zwgTotals.grossCogs());
        model.addAttribute("returnedCogsUsd", usdTotals.returnedCogs());
        model.addAttribute("returnedCogsZwg", zwgTotals.returnedCogs());
        model.addAttribute("grossProfitUsd", grossProfitUsd);
        model.addAttribute("grossProfitZwg", grossProfitZwg);
        model.addAttribute("discountsUsd", discountsUsd);
        model.addAttribute("discountsZwg", discountsZwg);
        model.addAttribute("taxUsd", taxUsd);
        model.addAttribute("taxZwg", taxZwg);
        model.addAttribute("refundsUsd", refundsUsd);
        model.addAttribute("refundsZwg", refundsZwg);
        model.addAttribute("netSalesUsd", netSalesUsd);
        model.addAttribute("netSalesZwg", netSalesZwg);
        model.addAttribute("netProfitUsd", netProfitUsd);
        model.addAttribute("netProfitZwg", netProfitZwg);
        model.addAttribute("operatingExpensesUsd", usdTotals.operatingExpenses());
        model.addAttribute("operatingExpensesZwg", zwgTotals.operatingExpenses());
        model.addAttribute("averageBasketUsd", averageBasketUsd);
        model.addAttribute("profitMarginUsd", profitMarginUsd);
        model.addAttribute("netMarginUsd", netMarginUsd);
        model.addAttribute("revenueChangePct", revenueChangePct);
        model.addAttribute("profitChangePct", profitChangePct);
        model.addAttribute("cashCollectedUsd", cashCollectedUsd);
        model.addAttribute("cashCollectedZwg", cashCollectedZwg);
        model.addAttribute("cashRefundsUsd", cashRefundsUsd);
        model.addAttribute("cashRefundsZwg", cashRefundsZwg);
        model.addAttribute("netCashMovementUsd", netCashMovementUsd);
        model.addAttribute("netCashMovementZwg", netCashMovementZwg);
        model.addAttribute("cashAtHandUsd", cashAtHandUsd);
        model.addAttribute("cashAtHandZwg", cashAtHandZwg);
        model.addAttribute("varianceUsd", varianceUsd);
        model.addAttribute("varianceZwg", varianceZwg);
        model.addAttribute("inventoryValueUsd", inventoryValueUsd);
        model.addAttribute("inventoryValueZwg", inventoryValueZwg);
        model.addAttribute("openPoUsd", openPoUsd);
        model.addAttribute("openPoZwg", openPoZwg);
        model.addAttribute("openCommitmentCount", openCommitments.size());
        model.addAttribute("openCommitments", openCommitments.stream().limit(20).toList());
        model.addAttribute("netPositionUsd", netPositionUsd);
        model.addAttribute("netPositionZwg", netPositionZwg);
        model.addAttribute("currentAssetsUsd", currentAssetsUsd);
        model.addAttribute("currentAssetsZwg", currentAssetsZwg);
        model.addAttribute("currentLiabilitiesUsd", currentLiabilitiesUsd);
        model.addAttribute("currentLiabilitiesZwg", currentLiabilitiesZwg);
        model.addAttribute("currentRatioUsd", currentRatioUsd);
        model.addAttribute("cashCoverageUsd", cashCoverageUsd);
        model.addAttribute("liabilityExposureUsd", liabilityExposureUsd);
        model.addAttribute("hasCurrentLiabilitiesUsd", hasCurrentLiabilitiesUsd);
        model.addAttribute("financialHealth", financialHealth);
        model.addAttribute("openCashSessionCount", periodSessions.stream().filter(session -> CashSession.SessionStatus.OPEN.equals(session.getStatus())).count());
        model.addAttribute("closedCashSessionCount", periodSessions.stream().filter(session -> CashSession.SessionStatus.CLOSED.equals(session.getStatus())).count());
        model.addAttribute("paymentMethodTotals", paymentMethodTotals);
        model.addAttribute("salesByStatus", salesByStatus);
        model.addAttribute("poByStatus", poByStatus);
        model.addAttribute("dailyTrend", dailyTrend);
        model.addAttribute("hourlyTrend", hourlyTrend);
        model.addAttribute("productPerformance", productPerformance);
        model.addAttribute("slowMovers", slowMovers);
        model.addAttribute("shiftRows", shiftRows);
        model.addAttribute("borrowerOutstandingUsd", borrowerOutstandingUsd);
        model.addAttribute("borrowerOutstandingZwg", borrowerOutstandingZwg);
        model.addAttribute("topBorrowers", topBorrowers);
        model.addAttribute("borrowerAccountCount", allBorrowers.size());
        model.addAttribute("heldChangeUsd", heldChangeUsd);
        model.addAttribute("heldChangeZwg", heldChangeZwg);
        model.addAttribute("openChangeCount", openChange.size());
        model.addAttribute("expiringLots", expiringLots);
        model.addAttribute("openVarianceCount", openVarianceCount);
        model.addAttribute("inventoryRisks", inventoryRisks);
        model.addAttribute("insights", insights);
    }

    private void addCompanyProfileModel(Model model, Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        model.addAttribute("tenant", tenant);
        model.addAttribute("tenantPlan", tenant.getPlanId() == null ? null : plans.findById(tenant.getPlanId()).orElse(null));
        model.addAttribute("currencyCodes", CurrencyCode.values());
        model.addAttribute("tenantBranches", branches.findByTenantId(tenantId));
        model.addAttribute("branchCount", branches.countByTenantId(tenantId));
        model.addAttribute("userCount", users.findByTenantId(tenantId).size());
    }

    private Map<Long, String> branchById(Long tenantId) {
        Map<Long, String> names = new HashMap<>();
        branches.findByTenantId(tenantId).forEach(branch -> names.put(branch.getId(), branch.getName()));
        return names;
    }

    private Map<Long, String> userNamesById(Long tenantId) {
        Map<Long, String> names = new HashMap<>();
        users.findByTenantId(tenantId).forEach(user -> names.put(user.getId(), (user.getFirstName() + " " + user.getLastName()).trim()));
        return names;
    }

    private Map<Long, List<String>> paymentsBySale(List<Sale> saleList) {
        Map<Long, List<String>> labels = new HashMap<>();
        saleList.forEach(sale -> labels.put(sale.getId(), salePayments.findBySaleId(sale.getId()).stream()
                .map(payment -> payment.getPaymentMethod() + " " + payment.getCurrency())
                .toList()));
        return labels;
    }

    private Map<Long, Sale> returnOriginSales(List<Return> currentReturns, List<Return> previousReturns) {
        return java.util.stream.Stream.concat(currentReturns.stream(), previousReturns.stream())
                .map(Return::getOriginalSaleId)
                .filter(Objects::nonNull)
                .distinct()
                .map(sales::findWithItemsById)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toMap(Sale::getId, Function.identity(), (first, ignored) -> first));
    }

    private List<ProductPerformance> productPerformance(List<Sale> completedSales, List<Return> recognizedReturns,
                                                        Map<Long, Sale> originalSalesById) {
        Map<Long, ProductPerformance> rows = new LinkedHashMap<>();
        for (Sale sale : completedSales) {
            for (SaleItem item : sale.getItems()) {
                ProductPerformance row = rows.computeIfAbsent(item.getProductId(), id ->
                        new ProductPerformance(id, item.getProductName(), item.getProductSku(), BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                BigDecimal quantity = nvl(item.getQuantity());
                BigDecimal revenue = nvl(item.getUnitPrice()).multiply(quantity).subtract(nvl(item.getDiscountAmount()));
                BigDecimal cost = nvl(item.getCostPrice()).multiply(quantity);
                BigDecimal profit = revenue.subtract(cost);
                row.addQuantity(quantity);
                if (CurrencyCode.USD.equals(sale.getCurrency())) {
                    row.addUsd(revenue, profit);
                } else {
                    row.addZwg(revenue, profit);
                }
            }
        }
        for (Return ret : recognizedReturns) {
            Sale originalSale = originalSalesById.get(ret.getOriginalSaleId());
            if (originalSale == null) continue;
            for (ReturnItem returnedItem : ret.getItems()) {
                SaleItem originalItem = originalSale.getItems().stream()
                        .filter(item -> returnedItem.getProductId().equals(item.getProductId()))
                        .findFirst()
                        .orElse(null);
                String name = originalItem == null ? returnedItem.getProductName() : originalItem.getProductName();
                String sku = originalItem == null ? "" : originalItem.getProductSku();
                ProductPerformance row = rows.computeIfAbsent(returnedItem.getProductId(), id ->
                        new ProductPerformance(id, name, sku, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                BigDecimal quantity = nvl(returnedItem.getQuantity());
                BigDecimal refund = nvl(returnedItem.getRefundAmount());
                BigDecimal returnedCost = originalItem == null
                        ? BigDecimal.ZERO
                        : nvl(originalItem.getCostPrice()).multiply(quantity);
                row.addQuantity(quantity.negate());
                BigDecimal profitReversal = refund.subtract(returnedCost).negate();
                if (CurrencyCode.USD.equals(ret.getCurrency())) {
                    row.addUsd(refund.negate(), profitReversal);
                } else {
                    row.addZwg(refund.negate(), profitReversal);
                }
            }
        }
        return new ArrayList<>(rows.values());
    }

    private List<TrendPoint> dailyTrend(List<Sale> completedSales, List<Return> recognizedReturns,
                                        LocalDate from, LocalDate toExclusive) {
        Map<LocalDate, TrendPoint> trend = new LinkedHashMap<>();
        LocalDate cursor = from;
        while (cursor.isBefore(toExclusive)) {
            trend.put(cursor, new TrendPoint(cursor.toString(), BigDecimal.ZERO, 0, 0));
            cursor = cursor.plusDays(1);
        }
        for (Sale sale : completedSales) {
            if (sale.getCreatedAt() == null) continue;
            TrendPoint point = trend.get(sale.getCreatedAt().toLocalDate());
            if (point != null) {
                point.addSale(CurrencyCode.USD.equals(sale.getCurrency()) ? nvl(sale.getSubtotal()) : BigDecimal.ZERO);
            }
        }
        for (Return ret : recognizedReturns) {
            if (ret.getCreatedAt() == null || !CurrencyCode.USD.equals(ret.getCurrency())) continue;
            TrendPoint point = trend.get(ret.getCreatedAt().toLocalDate());
            if (point != null) point.addAdjustment(nvl(ret.getTotalRefund()).negate());
        }
        return new ArrayList<>(trend.values());
    }

    private List<TrendPoint> hourlyTrend(List<Sale> completedSales) {
        Map<Integer, TrendPoint> trend = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            trend.put(hour, new TrendPoint(String.format("%02d:00", hour), BigDecimal.ZERO, 0, 0));
        }
        for (Sale sale : completedSales) {
            if (sale.getCreatedAt() == null) continue;
            TrendPoint point = trend.get(sale.getCreatedAt().getHour());
            if (point != null) {
                point.addSale(CurrencyCode.USD.equals(sale.getCurrency()) ? nvl(sale.getGrandTotal()) : BigDecimal.ZERO);
            }
        }
        return trend.values().stream().filter(point -> point.getSaleCount() > 0 || point.getRevenueUsd().compareTo(BigDecimal.ZERO) > 0).toList();
    }

    private List<InventoryRiskRow> inventoryRisks(List<Inventory> branchInventory, Map<Long, Product> productById) {
        return branchInventory.stream()
                .map(stock -> {
                    Product product = productById.get(stock.getProductId());
                    BigDecimal quantity = nvl(stock.getQuantityOnHand());
                    BigDecimal reorderLevel = product == null ? BigDecimal.ZERO : nvl(product.getReorderLevel());
                    BigDecimal costValue = quantity.multiply(nvl(stock.getAverageCostUsd()));
                    String risk = reorderLevel.compareTo(BigDecimal.ZERO) > 0 && quantity.compareTo(reorderLevel) <= 0
                            ? "LOW STOCK"
                            : quantity.compareTo(BigDecimal.ZERO) <= 0 ? "EMPTY" : "WATCH";
                    int percent = reorderLevel.compareTo(BigDecimal.ZERO) <= 0
                            ? 100
                            : Math.min(100, quantity.multiply(BigDecimal.valueOf(100)).divide(reorderLevel, 0, RoundingMode.HALF_UP).intValue());
                    return new InventoryRiskRow(product == null ? "Product " + stock.getProductId() : product.getName(),
                            product == null ? "" : product.getSku(), quantity, reorderLevel, costValue, risk, percent);
                })
                .sorted(Comparator.comparing((InventoryRiskRow row) -> "EMPTY".equals(row.getRisk()) ? 0 : "LOW STOCK".equals(row.getRisk()) ? 1 : 2)
                        .thenComparing(InventoryRiskRow::getCostValueUsd, Comparator.reverseOrder()))
                .toList();
    }

    private List<Insight> buildReportInsights(BigDecimal revenueUsd, BigDecimal revenueChangePct,
                                              BigDecimal grossProfitUsd, BigDecimal profitMarginUsd,
                                              BigDecimal varianceUsd, BigDecimal borrowerOutstandingUsd,
                                              BigDecimal heldChangeUsd, int expiringCount, long openVarianceCount,
                                              List<TrendPoint> dailyTrend, List<TrendPoint> hourlyTrend,
                                              List<ProductPerformance> productPerformance,
                                              List<InventoryRiskRow> inventoryRisks) {
        List<Insight> insights = new ArrayList<>();
        String revenueMood = revenueChangePct.compareTo(BigDecimal.ZERO) >= 0 ? "positive" : "warning";
        insights.add(new Insight("Revenue movement", "USD revenue is " + revenueChangePct + "% versus the previous comparable period.", revenueMood, "fa-solid fa-arrow-trend-up"));
        insights.add(new Insight("Profit quality", "Gross margin is " + profitMarginUsd + "% on USD sales.", profitMarginUsd.compareTo(new BigDecimal("20")) >= 0 ? "positive" : "warning", "fa-solid fa-seedling"));
        hourlyTrend.stream().max(Comparator.comparing(TrendPoint::getRevenueUsd))
                .ifPresent(point -> insights.add(new Insight("Peak selling hour", point.getLabel() + " produced the strongest USD activity.", "info", "fa-solid fa-clock")));
        productPerformance.stream().findFirst()
                .ifPresent(row -> insights.add(new Insight("Product leader", row.getName() + " is carrying the strongest profit in this period.", "positive", "fa-solid fa-box-open")));
        if (varianceUsd.abs().compareTo(BigDecimal.ZERO) > 0) {
            insights.add(new Insight("Cash variance", "Drawer variance is sitting at USD " + varianceUsd + ".", "warning", "fa-solid fa-triangle-exclamation"));
        }
        if (borrowerOutstandingUsd.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(new Insight("Credit exposure", "Borrower accounts owe USD " + borrowerOutstandingUsd + ".", "warning", "fa-solid fa-hand-holding-dollar"));
        }
        if (heldChangeUsd.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(new Insight("Held change liability", "Open held change is USD " + heldChangeUsd + ".", "info", "fa-solid fa-coins"));
        }
        if (expiringCount > 0) {
            insights.add(new Insight("Expiry risk", expiringCount + " batch or serial records expire within 30 days.", "warning", "fa-solid fa-hourglass-half"));
        }
        if (openVarianceCount > 0) {
            insights.add(new Insight("Stock variance", openVarianceCount + " stock investigations are still open.", "warning", "fa-solid fa-magnifying-glass-chart"));
        }
        inventoryRisks.stream().filter(row -> "LOW STOCK".equals(row.getRisk()) || "EMPTY".equals(row.getRisk())).findFirst()
                .ifPresent(row -> insights.add(new Insight("Stock action", row.getName() + " needs replenishment attention.", "warning", "fa-solid fa-warehouse")));
        if (insights.isEmpty()) {
            insights.add(new Insight("Healthy period", "No major risk signals were detected for this period.", "positive", "fa-solid fa-circle-check"));
        }
        return insights.stream().limit(8).toList();
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }

    private int barPercent(BigDecimal value, BigDecimal max) {
        if (max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) return 0;
        return Math.max(4, value.abs().multiply(BigDecimal.valueOf(100)).divide(max, 0, RoundingMode.HALF_UP).intValue());
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime parseDate(String value, boolean endExclusive) {
        if (value == null || value.isBlank()) {
            return null;
        }
        LocalDate date = LocalDate.parse(value);
        return endExclusive ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
    }

    private Sale.SaleStatus parseSaleStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Sale.SaleStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PurchaseOrder.PoStatus parsePoStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PurchaseOrder.PoStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private boolean isLowStock(Product product, Inventory stock) {
        if (product == null || stock == null || product.getReorderLevel() == null || product.getReorderLevel().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return nvl(stock.getQuantityOnHand()).compareTo(product.getReorderLevel()) <= 0;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private DashboardMoney moneyTotals(Long tenantId, Long branchId, LocalDateTime from, LocalDateTime to) {
        BigDecimal usd = salePayments.sumCompletedPaymentsByCurrency(tenantId, branchId, CurrencyCode.USD, from, to);
        BigDecimal zwg = salePayments.sumCompletedPaymentsByCurrency(tenantId, branchId, CurrencyCode.ZWG, from, to);
        return new DashboardMoney(nvl(usd), nvl(zwg), "USD " + money(usd), "ZWG " + money(zwg));
    }

    private String money(BigDecimal value) {
        return nvl(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<SmilePayCheckout.PaymentMethod> paymentMethods() {
        return List.of(
                SmilePayCheckout.PaymentMethod.ECOCASH,
                SmilePayCheckout.PaymentMethod.ONEMONEY,
                SmilePayCheckout.PaymentMethod.OMARI,
                SmilePayCheckout.PaymentMethod.SMILECASH,
                SmilePayCheckout.PaymentMethod.INNBUCKS,
                SmilePayCheckout.PaymentMethod.CARD);
    }

    private String quantity(BigDecimal value) {
        return nvl(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal dashboardAvailableGasKg(Long tenantId, List<Branch> activeBranches) {
        if (!packageModuleAccessService.hasGas(tenantId)) {
            return BigDecimal.ZERO;
        }
        return activeBranches.stream()
                .filter(branch -> BusinessModule.GAS_MODULE.equals(branch.getModuleType()))
                .flatMap(branch -> gasOperations.tanks(tenantId, branch.getId()).stream())
                .map(GasTank::getCurrentKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String percentChange(BigDecimal currentValue, BigDecimal previousValue) {
        BigDecimal currentAmount = nvl(currentValue);
        BigDecimal previousAmount = nvl(previousValue);
        if (previousAmount.compareTo(BigDecimal.ZERO) == 0) {
            return currentAmount.compareTo(BigDecimal.ZERO) > 0 ? "+100.0%" : "0.0%";
        }
        BigDecimal change = currentAmount.subtract(previousAmount)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousAmount, 1, RoundingMode.HALF_UP);
        return (change.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + change.toPlainString() + "%";
    }

    private int inventoryHealth(int stockLines, int lowStockLines) {
        if (stockLines <= 0) {
            return 100;
        }
        int healthy = Math.max(0, stockLines - lowStockLines);
        return BigDecimal.valueOf(healthy)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(stockLines), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private long lowStockCategoryCount(List<Inventory> lowStockRows, Map<Long, Product> productById) {
        return lowStockRows.stream()
                .map(stock -> productById.get(stock.getProductId()))
                .filter(Objects::nonNull)
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .map(ProductCategory::getId)
                .distinct()
                .count();
    }

    private List<DashboardLowStockItem> dashboardLowStock(List<Inventory> lowStockRows, Map<Long, Product> productById) {
        return lowStockRows.stream()
                .sorted(Comparator.comparing(stock -> nvl(stock.getQuantityOnHand())))
                .limit(5)
                .map(stock -> {
                    Product product = productById.get(stock.getProductId());
                    ProductCategory category = product == null ? null : product.getCategory();
                    return new DashboardLowStockItem(
                            product == null ? "Product " + stock.getProductId() : product.getName(),
                            category == null ? "Uncategorised" : category.getName(),
                            quantity(stock.getQuantityOnHand()),
                            product == null ? "0" : quantity(product.getReorderLevel()));
                })
                .toList();
    }

    private List<DashboardRecentSale> dashboardRecentSales(Long tenantId, Long branchId) {
        return operations.recentSales(tenantId, branchId).stream()
                .sorted(Comparator.comparing(Sale::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(sale -> new DashboardRecentSale(
                        sale.getReceiptNumber(),
                        sale.getCustomerId() == null ? "Walk-in Customer" : "Customer #" + sale.getCustomerId(),
                        sale.getCurrency() + " " + money(sale.getGrandTotal()),
                        sale.getStatus().name().replace('_', ' '),
                        saleStatusClass(sale.getStatus()),
                        dashboardTime(sale.getCreatedAt())))
                .toList();
    }

    private String saleStatusClass(Sale.SaleStatus status) {
        if (Sale.SaleStatus.COMPLETED.equals(status)) return "good";
        if (Sale.SaleStatus.PENDING.equals(status)) return "warn";
        if (Sale.SaleStatus.VOIDED.equals(status) || Sale.SaleStatus.REFUNDED.equals(status)) return "danger";
        return "blue";
    }

    private String dashboardTime(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "--";
        }
        LocalDate saleDate = createdAt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (saleDate.equals(today)) {
            return createdAt.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (saleDate.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return createdAt.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    private List<DashboardBranchPerformance> dashboardBranchPerformance(Long tenantId, List<Branch> activeBranches, LocalDateTime from, LocalDateTime to) {
        return activeBranches.stream()
                .map(branch -> {
                    DashboardMoney totals = moneyTotals(tenantId, branch.getId(), from, to);
                    long transactions = sales.countByTenantIdAndBranchIdAndStatusAndCreatedAtBetween(
                            tenantId, branch.getId(), Sale.SaleStatus.COMPLETED, from, to);
                    return new DashboardBranchPerformance(branch.getName(), totals.usd(), totals.zwg(), transactions, "Active");
                })
                .sorted(Comparator.comparing(DashboardBranchPerformance::transactions).reversed())
                .limit(5)
                .toList();
    }

    private DashboardSalesChart dashboardSalesChart(Long tenantId, Long branchId, LocalDateTime weekStart, LocalDate today) {
        List<BigDecimal> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        BigDecimal max = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.toLocalDate().plusDays(i);
            BigDecimal amount = nvl(salePayments.sumCompletedPaymentsByCurrency(
                    tenantId, branchId, CurrencyCode.USD, date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
            values.add(amount);
            labels.add(date.format(DateTimeFormatter.ofPattern("MMM d")));
            if (amount.compareTo(max) > 0) {
                max = amount;
            }
        }
        if (max.compareTo(BigDecimal.ZERO) == 0) {
            max = BigDecimal.ONE;
        }
        int width = 650;
        int top = 22;
        int bottom = 225;
        List<String> points = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            int x = values.size() == 1 ? 0 : i * width / (values.size() - 1);
            BigDecimal ratio = values.get(i).divide(max, 6, RoundingMode.HALF_UP);
            int y = bottom - ratio.multiply(BigDecimal.valueOf(bottom - top)).intValue();
            points.add(x + " " + y);
        }
        String linePath = "M " + String.join(" L ", points);
        String areaPath = linePath + " L " + width + " 260 L 0 260 Z";
        int lastIndex = values.size() - 1;
        String[] lastPoint = points.get(lastIndex).split(" ");
        return new DashboardSalesChart(
                linePath,
                areaPath,
                labels,
                labels.get(lastIndex),
                "USD " + money(values.get(lastIndex)),
                lastPoint[0],
                lastPoint[1],
                "USD " + money(max));
    }

    private BigDecimal sumSales(List<Sale> saleList, CurrencyCode currency, Function<Sale, BigDecimal> valueGetter) {
        return saleList.stream()
                .filter(sale -> currency.equals(sale.getCurrency()))
                .map(valueGetter)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumReturns(List<Return> returnList, CurrencyCode currency) {
        return returnList.stream()
                .filter(ret -> currency.equals(ret.getCurrency()))
                .map(Return::getTotalRefund)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addBranchManagementModel(Model model, Long tenantId, int page, int size) {
        List<Branch> allBranches = branches.findByTenantId(tenantId);
        Page<Branch> branchPage = pageList(allBranches, page, size);
        List<Branch> tenantBranches = branchPage.getContent();
        Map<Long, Long> userCounts = new HashMap<>();
        Map<Long, Integer> stockLineCounts = new HashMap<>();
        Map<Long, Integer> lowStockCounts = new HashMap<>();
        Map<Long, Long> todaySaleCounts = new HashMap<>();
        Map<Long, BigDecimal> todaySales = new HashMap<>();
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<User> tenantUsers = users.findByTenantId(tenantId);
        Long primaryBranchId = allBranches.stream()
                .filter(branch -> Boolean.TRUE.equals(branch.getIsActive()))
                .findFirst()
                .or(() -> allBranches.stream().findFirst())
                .map(Branch::getId)
                .orElse(null);
        for (Branch branch : tenantBranches) {
            Long branchId = branch.getId();
            userCounts.put(branchId, tenantUsers.stream()
                    .filter(user -> belongsToBranch(user, branchId, primaryBranchId))
                    .count());
            stockLineCounts.put(branchId, inventory.findByTenantIdAndBranchId(tenantId, branchId).size());
            lowStockCounts.put(branchId, inventory.findLowStockItems(tenantId, branchId).size());
            todaySaleCounts.put(branchId, sales.countByTenantIdAndBranchIdAndCreatedAtBetween(tenantId, branchId, start, end));
            todaySales.put(branchId, sales.sumGrandTotal(tenantId, branchId, start, end));
        }

        model.addAttribute("tenantBranches", tenantBranches);
        model.addAttribute("branchPage", branchPage);
        addPaginationModel(model, "branch", branchPage, "/shop/branches", Map.of());
        model.addAttribute("activeBranchCount", allBranches.stream().filter(b -> Boolean.TRUE.equals(b.getIsActive())).count());
        model.addAttribute("inactiveBranchCount", allBranches.stream().filter(b -> !Boolean.TRUE.equals(b.getIsActive())).count());
        model.addAttribute("branchUserCounts", userCounts);
        model.addAttribute("branchStockLineCounts", stockLineCounts);
        model.addAttribute("branchLowStockCounts", lowStockCounts);
        model.addAttribute("branchTodaySaleCounts", todaySaleCounts);
        model.addAttribute("branchTodaySales", todaySales);
        List<BusinessModule> enabledModules = packageModuleAccessService.syncAndGetEnabledModules(tenantId).stream()
                .filter(module -> !BusinessModule.RESTAURANT_MODULE.equals(module))
                .distinct()
                .toList();
        model.addAttribute("branchModuleOptions", enabledModules.isEmpty() ? List.of(BusinessModule.SHOP_MODULE) : enabledModules);
    }

    private void addGasManagementModel(Model model, Long tenantId, Long selectedBranchId) {
        List<Branch> gasBranches = branches.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .filter(branch -> BusinessModule.GAS_MODULE.equals(branch.getModuleType()))
                .toList();
        Long gasBranchId = gasBranches.stream()
                .filter(branch -> branch.getId().equals(selectedBranchId))
                .findFirst()
                .or(() -> gasBranches.stream().findFirst())
                .map(Branch::getId)
                .orElse(selectedBranchId);

        List<GasTank> gasTanks = List.of();
        List<GasPrice> gasPrices = List.of();
        List<GasSale> shiftSales = List.of();
        List<GasSale> allGasSales = List.of();
        List<GasRestock> gasRestocks = List.of();
        List<GasStockAdjustment> gasStockAdjustments = List.of();
        List<GasExpense> gasExpenses = List.of();
        Page<GasShift> gasShiftPage = Page.empty();
        GasOperationsService.GasDashboard gasDashboard = null;
        GasShift currentGasShift = null;
        if (!gasBranches.isEmpty()) {
            gasTanks = gasOperations.tanks(tenantId, gasBranchId);
            gasPrices = gasOperations.prices(tenantId, gasBranchId);
            currentGasShift = gasOperations.currentShift(tenantId, gasBranchId, current.userId());
            shiftSales = gasOperations.shiftSales(tenantId, gasBranchId, current.userId());
            allGasSales = gasOperations.sales(tenantId, gasBranchId);
            gasRestocks = gasOperations.restocks(tenantId, gasBranchId);
            gasStockAdjustments = gasOperations.stockAdjustments(tenantId, gasBranchId);
            gasExpenses = gasOperations.expenses(tenantId, gasBranchId);
            gasShiftPage = gasOperations.shifts(tenantId, gasBranchId, PageRequest.of(0, 50));
            gasDashboard = gasOperations.dashboard(tenantId, gasBranchId);
        }

        int maxGasTanks = tenantSubscriptions.findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .flatMap(subscription -> plans.findById(subscription.getPlanId()))
                .map(SaasPlan::getMaxGasTanks)
                .orElse(0);
        BigDecimal totalKg = gasTanks.stream()
                .map(GasTank::getCurrentKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capacityKg = gasTanks.stream()
                .map(GasTank::getCapacityKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("gasBranches", gasBranches);
        model.addAttribute("gasBranchId", gasBranchId);
        model.addAttribute("gasTanks", gasTanks);
        model.addAttribute("gasTankNameById", gasTanks.stream().collect(Collectors.toMap(GasTank::getId, GasTank::getName)));
        model.addAttribute("gasPrices", gasPrices);
        model.addAttribute("allGasSales", allGasSales);
        model.addAttribute("gasRestocks", gasRestocks);
        model.addAttribute("gasStockAdjustments", gasStockAdjustments);
        model.addAttribute("gasExpenses", gasExpenses);
        model.addAttribute("gasShifts", gasShiftPage.getContent());
        model.addAttribute("gasDashboard", gasDashboard);
        model.addAttribute("currentGasShift", currentGasShift);
        model.addAttribute("gasShiftSales", shiftSales);
        model.addAttribute("maxGasTanks", maxGasTanks);
        model.addAttribute("gasTankCount", gasTanks.size());
        model.addAttribute("gasTotalKg", totalKg);
        model.addAttribute("gasCapacityKg", capacityKg);
        model.addAttribute("gasTankStatuses", GasTankStatus.values());
        model.addAttribute("currencies", CurrencyCode.values());
    }

    private boolean belongsToBranch(User user, Long branchId, Long primaryBranchId) {
        if (user.getBranchId() != null) {
            return branchId.equals(user.getBranchId());
        }
        return !hasRole(user, UserRole.CASHIER) && primaryBranchId != null && primaryBranchId.equals(branchId);
    }

    private void setSupplierActive(Long id, boolean active, RedirectAttributes redirect) {
        try {
            Supplier supplier = suppliers.findById(id)
                    .filter(existing -> current.tenantId().equals(existing.getTenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found for this shop."));
            supplier.setIsActive(active);
            suppliers.save(supplier);
            redirect.addFlashAttribute("message", active ? "Supplier reactivated." : "Supplier archived. Purchase history was kept.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
        }
    }

    private void updateSupplierFields(Supplier supplier, Supplier request) {
        supplier.setName(request.getName());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setEmail(request.getEmail());
        supplier.setPhone(request.getPhone());
        supplier.setMobile(request.getMobile());
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setCountry(request.getCountry());
        supplier.setTaxNumber(request.getTaxNumber());
        supplier.setPaymentTerms(request.getPaymentTerms());
        supplier.setBankName(request.getBankName());
        supplier.setBankAccount(request.getBankAccount());
        supplier.setNotes(request.getNotes());
        if (request.getIsActive() != null) {
            supplier.setIsActive(request.getIsActive());
        }
    }

    private CreatePurchaseOrderRequest buildPurchaseOrderRequest(Long branchId,
                                                                 Long supplierId,
                                                                 CurrencyCode currency,
                                                                 String expectedDeliveryDate,
                                                                 String notes,
                                                                 List<Long> productIds,
                                                                 List<BigDecimal> quantities,
                                                                 List<BigDecimal> unitCostUsd,
                                                                 List<BigDecimal> unitCostZwg,
                                                                 List<BigDecimal> taxRates,
                                                                 List<String> lineNotes) {
        Long tenantId = current.tenantId();
        suppliers.findById(supplierId)
                .filter(supplier -> tenantId.equals(supplier.getTenantId()))
                .filter(supplier -> Boolean.TRUE.equals(supplier.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Select an active supplier for this shop."));
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(supplierId);
        request.setCurrency(currency == null ? CurrencyCode.USD : currency);
        request.setNotes(notes);
        if (expectedDeliveryDate != null && !expectedDeliveryDate.isBlank()) {
            request.setExpectedDeliveryDate(LocalDate.parse(expectedDeliveryDate));
        }
        List<CreatePurchaseOrderRequest.PoItemRequest> items = new ArrayList<>();
        int size = productIds == null ? 0 : productIds.size();
        for (int i = 0; i < size; i++) {
            Long productId = productIds.get(i);
            BigDecimal quantity = valueAt(quantities, i);
            if (productId == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Product product = products.findById(productId)
                    .filter(p -> tenantId.equals(p.getTenantId()))
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .orElseThrow(() -> new IllegalArgumentException("One selected product is not available."));
            inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, product.getId())
                    .orElseThrow(() -> new IllegalArgumentException(product.getName() + " is not assigned to this branch."));
            CreatePurchaseOrderRequest.PoItemRequest item = new CreatePurchaseOrderRequest.PoItemRequest();
            item.setProductId(product.getId());
            item.setQuantity(quantity);
            item.setUnitCostUsd(valueAt(unitCostUsd, i) == null ? nvl(product.getCostPriceUsd()) : valueAt(unitCostUsd, i));
            item.setUnitCostZwg(valueAt(unitCostZwg, i) == null ? nvl(product.getCostPriceZwg()) : valueAt(unitCostZwg, i));
            item.setTaxRate(valueAt(taxRates, i) == null ? BigDecimal.ZERO : valueAt(taxRates, i));
            item.setNotes(textAt(lineNotes, i));
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Add at least one product line with a quantity.");
        }
        request.setItems(items);
        return request;
    }

    private PurchaseOrder ownedPurchaseOrder(Long id, Long branchId) {
        return purchaseOrders.findById(id)
                .filter(po -> current.tenantId().equals(po.getTenantId()))
                .filter(po -> branchId.equals(po.getBranchId()))
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found for this branch."));
    }

    private BigDecimal valueAt(List<BigDecimal> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    private String textAt(List<String> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    private String supplierName(Long tenantId, Long supplierId) {
        return suppliers.findById(supplierId)
                .filter(supplier -> tenantId.equals(supplier.getTenantId()))
                .map(Supplier::getName)
                .orElse("");
    }

    private boolean hasRole(User user, UserRole role) {
        return user.getRole() != null && role.equals(user.getRole().getName());
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(safePage(page), safeSize(size));
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private <T> Page<T> pageList(List<T> rows, int page, int size) {
        int safePage = safePage(page);
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

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record PageLink(int number, String label, String url, boolean active) {
    }

    public static class Insight {
        private final String title;
        private final String body;
        private final String tone;
        private final String icon;

        public Insight(String title, String body, String tone, String icon) {
            this.title = title;
            this.body = body;
            this.tone = tone;
            this.icon = icon;
        }

        public String getTitle() { return title; }
        public String getBody() { return body; }
        public String getTone() { return tone; }
        public String getIcon() { return icon; }
    }

    public static class CashierSalesSummary {
        private final Long cashierId;
        private final String name;
        private final int transactions;
        private final long completed;
        private final long voided;
        private final int items;
        private final BigDecimal revenueUsd;
        private final BigDecimal revenueZwg;
        private final BigDecimal averageUsd;
        private final LocalDateTime lastSaleAt;

        public CashierSalesSummary(Long cashierId, String name, int transactions,
                                   long completed, long voided, int items,
                                   BigDecimal revenueUsd, BigDecimal revenueZwg,
                                   BigDecimal averageUsd, LocalDateTime lastSaleAt) {
            this.cashierId = cashierId;
            this.name = name;
            this.transactions = transactions;
            this.completed = completed;
            this.voided = voided;
            this.items = items;
            this.revenueUsd = revenueUsd;
            this.revenueZwg = revenueZwg;
            this.averageUsd = averageUsd;
            this.lastSaleAt = lastSaleAt;
        }

        public Long getCashierId() { return cashierId; }
        public String getName() { return name; }
        public int getTransactions() { return transactions; }
        public long getCompleted() { return completed; }
        public long getVoided() { return voided; }
        public int getItems() { return items; }
        public BigDecimal getRevenueUsd() { return revenueUsd; }
        public BigDecimal getRevenueZwg() { return revenueZwg; }
        public BigDecimal getAverageUsd() { return averageUsd; }
        public LocalDateTime getLastSaleAt() { return lastSaleAt; }
    }

    public static class TrendPoint {
        private final String label;
        private BigDecimal revenueUsd;
        private int saleCount;
        private int percent;

        public TrendPoint(String label, BigDecimal revenueUsd, int saleCount, int percent) {
            this.label = label;
            this.revenueUsd = revenueUsd;
            this.saleCount = saleCount;
            this.percent = percent;
        }

        public void addSale(BigDecimal amount) {
            revenueUsd = revenueUsd.add(amount == null ? BigDecimal.ZERO : amount);
            saleCount++;
        }

        public void addAdjustment(BigDecimal amount) {
            revenueUsd = revenueUsd.add(amount == null ? BigDecimal.ZERO : amount);
        }

        public String getLabel() { return label; }
        public BigDecimal getRevenueUsd() { return revenueUsd; }
        public int getSaleCount() { return saleCount; }
        public int getPercent() { return percent; }
        public void setPercent(int percent) { this.percent = percent; }
    }

    public static class ProductPerformance {
        private final Long productId;
        private final String name;
        private final String sku;
        private BigDecimal quantity;
        private BigDecimal revenueUsd;
        private BigDecimal revenueZwg;
        private BigDecimal profitUsd;
        private BigDecimal profitZwg;
        private BigDecimal marginUsd;

        public ProductPerformance(Long productId, String name, String sku, BigDecimal quantity,
                                  BigDecimal revenueUsd, BigDecimal revenueZwg,
                                  BigDecimal profitUsd, BigDecimal profitZwg, BigDecimal marginUsd) {
            this.productId = productId;
            this.name = name;
            this.sku = sku;
            this.quantity = quantity;
            this.revenueUsd = revenueUsd;
            this.revenueZwg = revenueZwg;
            this.profitUsd = profitUsd;
            this.profitZwg = profitZwg;
            this.marginUsd = marginUsd;
        }

        public void addQuantity(BigDecimal value) {
            quantity = quantity.add(value == null ? BigDecimal.ZERO : value);
        }

        public void addUsd(BigDecimal revenue, BigDecimal profit) {
            revenueUsd = revenueUsd.add(revenue == null ? BigDecimal.ZERO : revenue);
            profitUsd = profitUsd.add(profit == null ? BigDecimal.ZERO : profit);
            marginUsd = revenueUsd.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : profitUsd.multiply(BigDecimal.valueOf(100)).divide(revenueUsd, 1, RoundingMode.HALF_UP);
        }

        public void addZwg(BigDecimal revenue, BigDecimal profit) {
            revenueZwg = revenueZwg.add(revenue == null ? BigDecimal.ZERO : revenue);
            profitZwg = profitZwg.add(profit == null ? BigDecimal.ZERO : profit);
        }

        public Long getProductId() { return productId; }
        public String getName() { return name; }
        public String getSku() { return sku; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getRevenueUsd() { return revenueUsd; }
        public BigDecimal getRevenueZwg() { return revenueZwg; }
        public BigDecimal getProfitUsd() { return profitUsd; }
        public BigDecimal getProfitZwg() { return profitZwg; }
        public BigDecimal getMarginUsd() { return marginUsd; }
    }

    public static class ShiftReportRow {
        private final Long id;
        private final String cashier;
        private final String status;
        private final int salesCount;
        private final BigDecimal revenueUsd;
        private final BigDecimal revenueZwg;
        private final BigDecimal profitUsd;
        private final BigDecimal profitZwg;
        private final BigDecimal varianceUsd;
        private final BigDecimal varianceZwg;
        private final LocalDateTime openedAt;
        private final LocalDateTime closedAt;

        public ShiftReportRow(Long id, String cashier, String status, int salesCount,
                              BigDecimal revenueUsd, BigDecimal revenueZwg,
                              BigDecimal profitUsd, BigDecimal profitZwg,
                              BigDecimal varianceUsd, BigDecimal varianceZwg,
                              LocalDateTime openedAt, LocalDateTime closedAt) {
            this.id = id;
            this.cashier = cashier;
            this.status = status;
            this.salesCount = salesCount;
            this.revenueUsd = revenueUsd;
            this.revenueZwg = revenueZwg;
            this.profitUsd = profitUsd;
            this.profitZwg = profitZwg;
            this.varianceUsd = varianceUsd;
            this.varianceZwg = varianceZwg;
            this.openedAt = openedAt;
            this.closedAt = closedAt;
        }

        public Long getId() { return id; }
        public String getCashier() { return cashier; }
        public String getStatus() { return status; }
        public int getSalesCount() { return salesCount; }
        public BigDecimal getRevenueUsd() { return revenueUsd; }
        public BigDecimal getRevenueZwg() { return revenueZwg; }
        public BigDecimal getProfitUsd() { return profitUsd; }
        public BigDecimal getProfitZwg() { return profitZwg; }
        public BigDecimal getVarianceUsd() { return varianceUsd; }
        public BigDecimal getVarianceZwg() { return varianceZwg; }
        public LocalDateTime getOpenedAt() { return openedAt; }
        public LocalDateTime getClosedAt() { return closedAt; }
    }

    public static class InventoryRiskRow {
        private final String name;
        private final String sku;
        private final BigDecimal quantity;
        private final BigDecimal reorderLevel;
        private final BigDecimal costValueUsd;
        private final String risk;
        private final int percent;

        public InventoryRiskRow(String name, String sku, BigDecimal quantity, BigDecimal reorderLevel,
                                BigDecimal costValueUsd, String risk, int percent) {
            this.name = name;
            this.sku = sku;
            this.quantity = quantity;
            this.reorderLevel = reorderLevel;
            this.costValueUsd = costValueUsd;
            this.risk = risk;
            this.percent = percent;
        }

        public String getName() { return name; }
        public String getSku() { return sku; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getReorderLevel() { return reorderLevel; }
        public BigDecimal getCostValueUsd() { return costValueUsd; }
        public String getRisk() { return risk; }
        public int getPercent() { return percent; }
    }

    public record DashboardMoney(BigDecimal usdRaw, BigDecimal zwgRaw, String usd, String zwg) {
    }

    public record DashboardRecentSale(String receipt, String customer, String amount, String status, String statusClass, String time) {
    }

    public record DashboardLowStockItem(String item, String category, String currentStock, String reorderLevel) {
    }

    public record DashboardBranchPerformance(String branch, String salesUsd, String salesZwg, long transactions, String status) {
    }

    public record DashboardSalesChart(String linePath,
                                      String areaPath,
                                      List<String> labels,
                                      String latestLabel,
                                      String latestValue,
                                      String latestX,
                                      String latestY,
                                      String maxLabel) {
    }

    private String title(String module) {
        return module.substring(0, 1).toUpperCase() + module.substring(1).replace('-', ' ');
    }
}

