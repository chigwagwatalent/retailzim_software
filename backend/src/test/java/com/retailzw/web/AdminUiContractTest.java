package com.retailzw.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUiContractTest {

    private static final Path TEMPLATE_ROOT = Path.of("src/main/resources/templates");
    private static final Path ADMIN_ROOT = TEMPLATE_ROOT.resolve("admin");
    private static final Path LAYOUT = TEMPLATE_ROOT.resolve("common/layout.html");
    private static final Path STYLES = Path.of("src/main/resources/static/css/retailzw.css");

    @Test
    void everyAdminPageUsesTheSharedModernWorkspace() throws IOException {
        List<Path> pages;
        try (var files = Files.list(ADMIN_ROOT)) {
            pages = files.filter(path -> path.toString().endsWith(".html")).toList();
        }

        assertThat(pages).isNotEmpty();
        for (Path page : pages) {
            assertThat(Files.readString(page))
                    .as(page.getFileName().toString())
                    .contains("platform-shell")
                    .contains("platformSidebar(")
                    .contains("platformTopbar(");
        }
    }

    @Test
    void adminBrandingDoesNotExposeLegacySaasWording() throws IOException {
        StringBuilder visibleAdminUi = new StringBuilder(Files.readString(LAYOUT));
        try (var files = Files.list(ADMIN_ROOT)) {
            for (Path page : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                visibleAdminUi.append(Files.readString(page));
            }
        }
        visibleAdminUi.append(Files.readString(
                TEMPLATE_ROOT.resolve("auth/admin-login.html")));
        visibleAdminUi.append(Files.readString(
                TEMPLATE_ROOT.resolve("auth/admin-forgot-password.html")));

        assertThat(visibleAdminUi.toString().toLowerCase())
                .doesNotContain("saas");
    }

    @Test
    void dashboardAndStylesContainApprovedCommandCenterComponents() throws IOException {
        String dashboard = Files.readString(ADMIN_ROOT.resolve("dashboard.html"));
        String layout = Files.readString(LAYOUT);
        String css = Files.readString(STYLES);

        assertThat(layout)
                .contains("System Admin")
                .contains("Overview")
                .contains("Search shops, users, plans or tickets")
                .contains("platform-sidebar-user");
        assertThat(dashboard)
                .contains("System Overview")
                .contains("Platform activity")
                .contains("System health")
                .contains("Recent shops")
                .contains("Recent activity");
        assertThat(css)
                .contains("Retail Zim system administration")
                .contains(".platform-command-grid")
                .contains(".platform-health-panel")
                .contains(".platform-sidebar-bottom");
    }

    @Test
    void shopDirectoryAndAccountingUseDedicatedProductionWorkspaces() throws IOException {
        String shops = Files.readString(ADMIN_ROOT.resolve("tenants.html"));
        String accounting = Files.readString(ADMIN_ROOT.resolve("accounting.html"));
        String payments = Files.readString(ADMIN_ROOT.resolve("accounting-payments.html"));
        String expenses = Files.readString(ADMIN_ROOT.resolve("accounting-expenses.html"));
        String layout = Files.readString(LAYOUT);

        assertThat(shops)
                .contains("tenant-kpi-grid")
                .contains("Registered Shops")
                .contains("Recent Activity")
                .contains("row-actions")
                .contains("Edit billing period")
                .contains("billing-period-modal")
                .contains("/billing-period");
        assertThat(layout)
                .contains("Accounting")
                .contains("accountingTabs");
        assertThat(accounting)
                .contains("Accounting Overview")
                .contains("Ledger integrity")
                .contains("Monthly Revenue Trend");
        assertThat(payments)
                .contains("Confirmed Payments")
                .contains("Immutable revenue evidence");
        assertThat(expenses)
                .contains("System Expenses")
                .contains("Post an Expense")
                .contains("Void this Expense?");
    }

    @Test
    void subscriptionsUseIndependentModulePackageFamilies() throws IOException {
        String subscriptions = Files.readString(ADMIN_ROOT.resolve("subscriptions.html"));
        String layout = Files.readString(LAYOUT);
        String css = Files.readString(STYLES);

        assertThat(subscriptions)
                .contains("Subscription Packages")
                .contains("Retail POS Packages")
                .contains("LPG Gas Packages")
                .contains("Restaurant Packages")
                .contains("Packages cannot combine business modules")
                .contains("Each subscription activates one business module")
                .doesNotContain("shop mix")
                .doesNotContain("Registered shops")
                .doesNotContain("Billing Queue")
                .doesNotContain("<th>Shop</th>")
                .doesNotContain("module-package-card");
        assertThat(layout)
                .contains(">Shops</span>")
                .contains("Search packages");
        assertThat(css)
                .contains(".package-family-tabs")
                .contains(".package-table-wrap")
                .contains(".package-management-table")
                .contains(".billing-period-modal")
                .contains(".legacy-package-review");
    }
}
