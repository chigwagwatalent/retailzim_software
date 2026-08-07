package com.retailzw.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ShopLayoutContractTest {

    private static final Path SHOP_TEMPLATES = Path.of("src/main/resources/templates/shop");

    @Test
    void everyShopPageUsesTheSharedSidebarAndTopbar() throws IOException {
        List<Path> templates;
        try (Stream<Path> files = Files.list(SHOP_TEMPLATES)) {
            templates = files
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .toList();
        }

        assertTrue(!templates.isEmpty(), "No shop templates were found");
        for (Path template : templates) {
            String html = Files.readString(template);
            assertTrue(html.contains("common/layout :: shopSidebar("),
                    () -> template.getFileName() + " must import the shared shop sidebar");
            assertTrue(html.contains("common/layout :: shopTopbar("),
                    () -> template.getFileName() + " must import the shared shop topbar");
            assertTrue(html.contains("<div class=\"app-shell\">"),
                    () -> template.getFileName() + " must use the canonical app shell");
            assertTrue(html.contains("<main class=\"main\">"),
                    () -> template.getFileName() + " must use the canonical main container");
        }
    }

    @Test
    void sharedSidebarContainsModuleAwareRetailAndGasNavigation() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/templates/common/layout.html"));

        assertTrue(html.contains("shopModuleEnabled == true"));
        assertTrue(html.contains("gasModuleEnabled == true"));
        assertTrue(html.contains("Inventory Management"));
        assertTrue(html.contains("/shop/inventory"));
        assertTrue(html.contains("/shop/suppliers"));
        assertTrue(html.contains("/shop/purchasing"));
        assertTrue(html.contains("Purchase &amp; Receive"));
        assertTrue(html.contains("Gas Management"));
        assertTrue(html.contains("Supplier Deliveries"));
        assertTrue(html.contains("Gas Inventory"));
        assertTrue(html.contains("/shop/gas/accounting"));
        assertTrue(html.contains("/shop/gas/change"));
        assertTrue(html.contains("!#strings.startsWith(active, 'gas')"),
                "Retail Held Change must stay hidden inside the gas workspace");
        assertTrue(html.contains("supervisorUser == true"));
        assertTrue(html.contains("Supervisor Desk"));
        assertTrue(html.contains("Cash Collection"));
        assertTrue(html.contains("Stock Receiving"));
        assertTrue(html.contains("Gas Supervisor Desk"));
        assertTrue(html.contains("LPG Receiving"));
        assertTrue(html.contains("Assigned Branch"));
    }

    @Test
    void supervisorWorkspaceShowsOnlyBranchOperationalWorkflows() throws IOException {
        String dashboard = Files.readString(SHOP_TEMPLATES.resolve("supervisor.html"));
        String purchasing = Files.readString(SHOP_TEMPLATES.resolve("purchasing.html"));
        String returns = Files.readString(SHOP_TEMPLATES.resolve("returns.html"));

        assertTrue(dashboard.contains("Shifts Awaiting Collection"));
        assertTrue(dashboard.contains("Purchase Orders to Receive"));
        assertTrue(dashboard.contains("Open Held Change"));
        assertTrue(dashboard.contains("Latest Goods Received Notes"));
        assertTrue(purchasing.contains("supervisorUser != true"));
        assertTrue(returns.contains("supervisorUser != true"));
    }

    @Test
    void supervisorGasPagesKeepConfigurationOwnerOnly() throws IOException {
        String dashboard = Files.readString(SHOP_TEMPLATES.resolve("gas.html"));
        String inventory = Files.readString(SHOP_TEMPLATES.resolve("gas-tanks.html"));
        String change = Files.readString(SHOP_TEMPLATES.resolve("gas-change.html"));

        assertTrue(dashboard.contains("supervisorUser != true"));
        assertTrue(dashboard.contains("Receive LPG"));
        assertTrue(inventory.contains("supervisorUser != true"));
        assertTrue(change.contains("supervisorUser != true"));
    }

    @Test
    void shopAdminLayoutIncludesTheClickableBillingRenewalToast() throws IOException {
        String layout = Files.readString(Path.of("src/main/resources/templates/common/layout.html"));
        String javascript = Files.readString(Path.of("src/main/resources/static/js/retailzw.js"));
        String css = Files.readString(Path.of("src/main/resources/static/css/retailzw.css"));

        assertTrue(layout.contains("data-billing-renewal-toast"));
        assertTrue(layout.contains("billingRenewalNotice != null"));
        assertTrue(layout.contains("th:href=\"@{/shop/billing}\""));
        assertTrue(layout.contains("Click here to pay"));
        assertTrue(layout.contains("data-billing-toast-dismiss"));
        assertTrue(layout.contains("aria-live=\"polite\""));
        assertTrue(javascript.contains("setupBillingRenewalToast"));
        assertTrue(javascript.contains("18000"));
        assertTrue(css.contains(".billing-renewal-toast.is-urgent"));
        assertTrue(css.contains(".billing-renewal-toast.is-overdue"));
    }

    @Test
    void inventoryPagesExposeAuditedRetailAndGasStockWorkflows() throws IOException {
        String inventory = Files.readString(SHOP_TEMPLATES.resolve("inventory.html"));
        String purchasing = Files.readString(SHOP_TEMPLATES.resolve("purchasing.html"));
        String gasInventory = Files.readString(SHOP_TEMPLATES.resolve("gas-tanks.html"));

        assertTrue(inventory.contains("Recent Stock Movements"));
        assertTrue(inventory.contains("/shop/purchasing"));
        assertTrue(purchasing.contains("Confirm Receipt"));
        assertTrue(purchasing.contains("/receive"));
        assertTrue(gasInventory.contains("/shop/gas/stock/reconcile"));
        assertTrue(gasInventory.contains("Recent reconciliation"));
        assertTrue(gasInventory.contains("pagedGasTanks"));
        assertTrue(gasInventory.contains("gasTankPageLinks"));
        assertTrue(gasInventory.contains("gasAuditPageLinks"));
        assertTrue(gasInventory.contains("Receive Stock"));
        assertTrue(gasInventory.contains("currentGrossWeightKg"));
        assertTrue(gasInventory.contains("Full gross − tare"));
        assertTrue(gasInventory.contains("countedGrossKg"));
        assertTrue(!gasInventory.contains("name=\"capacityKg\""));
        assertTrue(!gasInventory.contains("name=\"currentKg\""));
    }

    @Test
    void gasAccountingUsesAuditableDashboardMetricsAndPaginatedShifts() throws IOException {
        String accounting = Files.readString(SHOP_TEMPLATES.resolve("gas-accounting.html"));

        assertTrue(accounting.contains("gas-accounting-kpis"));
        assertTrue(accounting.contains("Gross revenue"));
        assertTrue(accounting.contains("Today's profit bridge"));
        assertTrue(accounting.contains("gasAccountingHours"));
        assertTrue(accounting.contains("Shift performance"));
        assertTrue(accounting.contains("gasShiftPageLinks"));
        assertTrue(accounting.contains("name=\"shiftQuery\""));
        assertTrue(accounting.contains("name=\"shiftStatus\""));
        assertTrue(accounting.contains("closingVarianceKg"));
    }

    @Test
    void gasExpensesExposeFunctionalCostControlsAndAuditSafeDetails() throws IOException {
        String expenses = Files.readString(SHOP_TEMPLATES.resolve("gas-expenses.html"));

        assertTrue(expenses.contains("gas-expenses-kpis"));
        assertTrue(expenses.contains("Expense trend"));
        assertTrue(expenses.contains("Category breakdown"));
        assertTrue(expenses.contains("/shop/gas/expenses/export"));
        assertTrue(expenses.contains("name=\"expensePeriod\""));
        assertTrue(expenses.contains("name=\"expenseQuery\""));
        assertTrue(expenses.contains("name=\"expenseCategory\""));
        assertTrue(expenses.contains("name=\"expensePayment\""));
        assertTrue(expenses.contains("gasExpensePageLinks"));
        assertTrue(expenses.contains("Read-only audit details"));
        assertTrue(!expenses.contains("Delete expense"));
    }

    @Test
    void supplierDeliveriesExposePlanningReceivingAndAuditedHistory() throws IOException {
        String deliveries = Files.readString(SHOP_TEMPLATES.resolve("gas-restocking.html"));

        assertTrue(deliveries.contains("gas-deliveries-kpis"));
        assertTrue(deliveries.contains("Reorder watch"));
        assertTrue(deliveries.contains("Restock planner"));
        assertTrue(deliveries.contains("/shop/gas/restocking/export"));
        assertTrue(deliveries.contains("name=\"restockPeriod\""));
        assertTrue(deliveries.contains("name=\"restockQuery\""));
        assertTrue(deliveries.contains("name=\"restockTankId\""));
        assertTrue(deliveries.contains("name=\"restockCurrency\""));
        assertTrue(deliveries.contains("gasRestockPageLinks"));
        assertTrue(deliveries.contains("Net LPG delivered (kg)"));
        assertTrue(deliveries.contains("Calculated total cost"));
        assertTrue(deliveries.contains("Read-only stock and costing audit"));
        assertTrue(!deliveries.contains("Delete receipt"));
    }

    @Test
    void gasSalesExposePerformanceFiltersExportAndAuditedReceipts() throws IOException {
        String sales = Files.readString(SHOP_TEMPLATES.resolve("gas-sales.html"));

        assertTrue(sales.contains("gas-sales-kpis"));
        assertTrue(sales.contains("Sales performance"));
        assertTrue(sales.contains("Payment mix"));
        assertTrue(sales.contains("Current shift"));
        assertTrue(sales.contains("/shop/gas/sales/export"));
        assertTrue(sales.contains("name=\"salesPeriod\""));
        assertTrue(sales.contains("name=\"salesQuery\""));
        assertTrue(sales.contains("name=\"salesPayment\""));
        assertTrue(sales.contains("name=\"salesCashierId\""));
        assertTrue(sales.contains("gasSalesPageLinks"));
        assertTrue(sales.contains("Read-only transaction and payment audit"));
        assertTrue(!sales.contains("Delete receipt"));
    }

    @Test
    void gasHeldChangeHasSeparateNavigationRegisterAndGasShiftPayouts() throws IOException {
        String change = Files.readString(SHOP_TEMPLATES.resolve("gas-change.html"));

        assertTrue(change.contains("shopSidebar('gas-change')"));
        assertTrue(change.contains("Gas Held Change"));
        assertTrue(change.contains("Gas POS records only"));
        assertTrue(change.contains("name=\"gasChangeSearch\""));
        assertTrue(change.contains("name=\"gasChangeStatus\""));
        assertTrue(change.contains("gasChangePageLinks"));
        assertTrue(change.contains("/shop/gas/change/"));
        assertTrue(change.contains("currentGasShift != null"));
    }
}
