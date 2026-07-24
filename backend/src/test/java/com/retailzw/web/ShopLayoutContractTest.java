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
        assertTrue(gasInventory.contains("Stock Reconciliation Audit"));
    }
}
