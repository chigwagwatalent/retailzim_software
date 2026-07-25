package com.retailzw.controller.web;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SubscriptionPayment;
import com.retailzw.model.SystemExpense;
import com.retailzw.model.Tenant;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantChatMessageRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.service.SystemAccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/accounting")
public class SystemAccountingController {
    private static final int PAGE_SIZE = 25;

    private final SystemAccountingService accounting;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final TenantChatMessageRepository chatMessages;

    @GetMapping
    public String overview(@RequestParam(required = false) String month, Model model) {
        YearMonth selectedMonth = parseMonth(month);
        SystemAccountingService.AccountingOverview overview = accounting.overview(selectedMonth);
        Page<SubscriptionPayment> recentPayments = accounting.paymentPage(
                selectedMonth, null, null, PageRequest.of(0, 6));
        Page<SystemExpense> recentExpenses = accounting.expensePage(
                selectedMonth, null, null, SystemExpense.ExpenseStatus.POSTED, null, PageRequest.of(0, 6));

        addCommon(model, "overview", selectedMonth);
        model.addAttribute("overview", overview);
        model.addAttribute("usdSummary", overview.summaries().get(CurrencyCode.USD));
        model.addAttribute("zwgSummary", overview.summaries().get(CurrencyCode.ZWG));
        model.addAttribute("recentPayments", recentPayments.getContent());
        model.addAttribute("recentExpenses", recentExpenses.getContent());
        addPaymentNames(model, recentPayments.getContent());
        return "admin/accounting";
    }

    @GetMapping("/payments")
    public String payments(@RequestParam(required = false) String month,
                           @RequestParam(required = false) CurrencyCode currency,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        YearMonth selectedMonth = parseMonth(month);
        Page<SubscriptionPayment> paymentPage = accounting.paymentPage(
                selectedMonth, currency, search, PageRequest.of(Math.max(0, page), PAGE_SIZE));

        addCommon(model, "payments", selectedMonth);
        model.addAttribute("paymentPage", paymentPage);
        model.addAttribute("payments", paymentPage.getContent());
        model.addAttribute("selectedCurrency", currency);
        model.addAttribute("search", search);
        addPaymentNames(model, paymentPage.getContent());
        addPagination(model, paymentPage, "/admin/accounting/payments",
                params("month", selectedMonth, "currency", currency, "search", search));
        return "admin/accounting-payments";
    }

    @GetMapping("/expenses")
    public String expenses(@RequestParam(required = false) String month,
                           @RequestParam(required = false) CurrencyCode currency,
                           @RequestParam(required = false) SystemExpense.ExpenseCategory category,
                           @RequestParam(required = false) SystemExpense.ExpenseStatus status,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        YearMonth selectedMonth = parseMonth(month);
        Page<SystemExpense> expensePage = accounting.expensePage(
                selectedMonth, currency, category, status, search,
                PageRequest.of(Math.max(0, page), PAGE_SIZE));

        addCommon(model, "expenses", selectedMonth);
        model.addAttribute("expensePage", expensePage);
        model.addAttribute("expenses", expensePage.getContent());
        model.addAttribute("selectedCurrency", currency);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("search", search);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("usdCategories", accounting.categoryTotals(selectedMonth, CurrencyCode.USD));
        model.addAttribute("zwgCategories", accounting.categoryTotals(selectedMonth, CurrencyCode.ZWG));
        addPagination(model, expensePage, "/admin/accounting/expenses",
                params("month", selectedMonth, "currency", currency, "category", category, "status", status, "search", search));
        return "admin/accounting-expenses";
    }

    @PostMapping("/expenses")
    public String createExpense(@RequestParam String description,
                                @RequestParam(required = false) String vendor,
                                @RequestParam SystemExpense.ExpenseCategory category,
                                @RequestParam BigDecimal amount,
                                @RequestParam CurrencyCode currency,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incurredOn,
                                @RequestParam(required = false) String paymentReference,
                                @RequestParam(required = false) String notes,
                                @RequestParam(defaultValue = "false") boolean recurring,
                                @RequestParam(required = false) String returnMonth,
                                Authentication authentication,
                                RedirectAttributes redirect) {
        try {
            accounting.createExpense(command(description, vendor, category, amount, currency, incurredOn,
                    paymentReference, notes, recurring), actor(authentication));
            redirect.addFlashAttribute("message", "Expense posted to the accounting ledger.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + encode(parseMonth(returnMonth).toString());
    }

    @PostMapping("/expenses/{id}/edit")
    public String editExpense(@PathVariable Long id,
                              @RequestParam long version,
                              @RequestParam String description,
                              @RequestParam(required = false) String vendor,
                              @RequestParam SystemExpense.ExpenseCategory category,
                              @RequestParam BigDecimal amount,
                              @RequestParam CurrencyCode currency,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incurredOn,
                              @RequestParam(required = false) String paymentReference,
                              @RequestParam(required = false) String notes,
                              @RequestParam(defaultValue = "false") boolean recurring,
                              @RequestParam(required = false) String returnMonth,
                              Authentication authentication,
                              RedirectAttributes redirect) {
        try {
            accounting.updateExpense(id, command(description, vendor, category, amount, currency, incurredOn,
                    paymentReference, notes, recurring), version, actor(authentication));
            redirect.addFlashAttribute("message", "Expense updated.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + encode(parseMonth(returnMonth).toString());
    }

    @PostMapping("/expenses/{id}/void")
    public String voidExpense(@PathVariable Long id,
                              @RequestParam long version,
                              @RequestParam String reason,
                              @RequestParam(required = false) String returnMonth,
                              Authentication authentication,
                              RedirectAttributes redirect) {
        try {
            accounting.voidExpense(id, version, reason, actor(authentication));
            redirect.addFlashAttribute("message", "Expense voided. Its audit history has been retained.");
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/accounting/expenses?month=" + encode(parseMonth(returnMonth).toString());
    }

    private void addCommon(Model model, String accountingTab, YearMonth selectedMonth) {
        model.addAttribute("supportUnreadCount",
                chatMessages.countByReadByPlatformFalseAndSenderType(
                        com.retailzw.model.TenantChatMessage.SenderType.SHOP));
        model.addAttribute("accountingTab", accountingTab);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("currencies", CurrencyCode.values());
        model.addAttribute("expenseCategories", SystemExpense.ExpenseCategory.values());
        model.addAttribute("expenseStatuses", SystemExpense.ExpenseStatus.values());
    }

    private void addPaymentNames(Model model, List<SubscriptionPayment> paymentRows) {
        Set<Long> tenantIds = paymentRows.stream().map(SubscriptionPayment::getTenantId).collect(Collectors.toSet());
        Set<Long> planIds = paymentRows.stream().map(SubscriptionPayment::getPlanId).collect(Collectors.toSet());
        List<Tenant> paymentTenants = tenants.findAllById(tenantIds);
        Map<Long, String> tenantNames = paymentTenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getCompanyName));
        Map<Long, String> tenantCodes = paymentTenants.stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getTenantCode));
        Map<Long, String> planNames = plans.findAllById(planIds).stream()
                .collect(Collectors.toMap(SaasPlan::getId, SaasPlan::getName));
        model.addAttribute("paymentTenantNames", tenantNames);
        model.addAttribute("paymentTenantCodes", tenantCodes);
        model.addAttribute("paymentPlanNames", planNames);
    }

    private SystemAccountingService.ExpenseCommand command(String description,
                                                            String vendor,
                                                            SystemExpense.ExpenseCategory category,
                                                            BigDecimal amount,
                                                            CurrencyCode currency,
                                                            LocalDate incurredOn,
                                                            String paymentReference,
                                                            String notes,
                                                            boolean recurring) {
        return new SystemAccountingService.ExpenseCommand(description, vendor, category, amount, currency,
                incurredOn, paymentReference, notes, recurring);
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException ignored) {
            return YearMonth.now();
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }

    private Map<String, Object> params(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null && !values[i + 1].toString().isBlank()) {
                result.put(values[i].toString(), values[i + 1]);
            }
        }
        return result;
    }

    private void addPagination(Model model, Page<?> page, String path, Map<String, Object> params) {
        List<PageLink> links = new ArrayList<>();
        int start = Math.max(0, page.getNumber() - 3);
        int end = Math.min(page.getTotalPages() - 1, start + 6);
        start = Math.max(0, end - 6);
        for (int i = start; i <= end; i++) {
            links.add(new PageLink(String.valueOf(i + 1), pageUrl(path, params, i), i == page.getNumber()));
        }
        model.addAttribute("pageLinks", links);
        model.addAttribute("prevUrl", page.hasPrevious() ? pageUrl(path, params, page.getNumber() - 1) : null);
        model.addAttribute("nextUrl", page.hasNext() ? pageUrl(path, params, page.getNumber() + 1) : null);
    }

    private String pageUrl(String path, Map<String, Object> params, int page) {
        StringBuilder url = new StringBuilder(path).append("?page=").append(page);
        params.forEach((key, value) -> url.append('&').append(key).append('=').append(encode(value.toString())));
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record PageLink(String label, String url, boolean active) {
    }
}
