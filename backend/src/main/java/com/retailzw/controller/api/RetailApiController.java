package com.retailzw.controller.api;

import com.retailzw.dto.request.*;
import com.retailzw.dto.response.ApiResponse;
import com.retailzw.dto.response.ProductImportResult;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.CreditAndChangeService;
import com.retailzw.service.NotificationService;
import com.retailzw.service.ProductImportService;
import com.retailzw.service.RetailOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RetailApiController {

    private final CurrentUserService current;
    private final RetailOperationsService operations;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final InventoryRepository inventory;
    private final CustomerRepository customers;
    private final BranchRepository branches;
    private final CashSessionRepository cashSessions;
    private final SaleRepository sales;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final CreditAndChangeService creditAndChangeService;
    private final ProductImportService productImportService;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.success(Map.of(
                "tenantId", current.tenantId(),
                "branchId", current.branchId(),
                "userId", current.userId(),
                "role", current.roleName()
        ));
    }

    @GetMapping("/branches")
    public ApiResponse<List<Branch>> branches() {
        return ApiResponse.success(branches.findByTenantIdAndIsActiveTrue(current.tenantId()));
    }

    @GetMapping("/products")
    public ApiResponse<?> products(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) Long categoryId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(products.findProducts(current.tenantId(), blank(search), categoryId, PageRequest.of(page, size)));
    }

    @GetMapping("/products/branch-stock")
    public ApiResponse<List<Map<String, Object>>> productsForBranch(@RequestParam(required = false) String search,
                                                                    @RequestParam(required = false) Long categoryId,
                                                                    @RequestParam(required = false) Long branchId,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "100") int size) {
        Long targetBranch = selectedBranch(branchId);
        List<Map<String, Object>> allRows = inventory.findBranchProductStock(current.tenantId(), targetBranch, blank(search), categoryId)
                .stream()
                .map(stock -> products.findById(stock.getProductId())
                        .filter(product -> product.getTenantId().equals(current.tenantId()))
                        .map(product -> branchProductRow(product, targetBranch))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        int from = Math.min(page * size, allRows.size());
        int to = Math.min(from + size, allRows.size());
        List<Map<String, Object>> rows = allRows.subList(from, to);
        return ApiResponse.success(rows);
    }

    @GetMapping("/products/barcode/{barcode}")
    public ApiResponse<Product> barcode(@PathVariable String barcode) {
        return ApiResponse.success(products.findByTenantIdAndBarcode(current.tenantId(), barcode).orElseThrow());
    }

    @PostMapping("/products")
    public ApiResponse<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        request.setBranchId(selectedBranch(request.getBranchId()));
        return ApiResponse.success("Product created", operations.createProduct(current.tenantId(), request, current.userId()));
    }

    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductImportResult> importProducts(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(required = false) Long branchId) {
        Long targetBranch = selectedBranch(branchId);
        ProductImportResult result = productImportService.importProducts(current.tenantId(), targetBranch, current.userId(), file);
        return ApiResponse.success("Products imported", result);
    }

    @GetMapping("/categories")
    public ApiResponse<List<ProductCategory>> categories() {
        return ApiResponse.success(categories.findByTenantIdAndIsActiveTrueOrderBySortOrderAsc(current.tenantId()));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<Inventory>> inventory(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Long branchId) {
        Long targetBranch = selectedBranch(branchId);
        String cleanSearch = blank(search);
        if (cleanSearch == null) {
            return ApiResponse.success(operations.branchInventory(current.tenantId(), targetBranch));
        }
        String needle = cleanSearch.toLowerCase();
        List<Inventory> filtered = operations.branchInventory(current.tenantId(), targetBranch).stream()
                .filter(item -> products.findById(item.getProductId())
                        .filter(product -> product.getTenantId().equals(current.tenantId()))
                        .filter(product -> contains(product.getName(), needle) || contains(product.getSku(), needle) || contains(product.getBarcode(), needle))
                        .isPresent())
                .toList();
        return ApiResponse.success(filtered);
    }

    @PostMapping("/inventory/adjustments")
    public ApiResponse<InventoryAdjustment> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.success("Stock adjusted", operations.adjustStock(current.tenantId(), activeBranch(), request, current.userId()));
    }

    @GetMapping("/customers")
    public ApiResponse<?> customers(@RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(customers.searchCustomers(current.tenantId(), blank(search), PageRequest.of(page, size)));
    }

    @PostMapping("/customers")
    public ApiResponse<Customer> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ApiResponse.success("Customer created", operations.createCustomer(current.tenantId(), activeBranch(), request, current.userId()));
    }

    @GetMapping("/borrowers")
    public ApiResponse<List<Borrower>> borrowers() {
        return ApiResponse.success(creditAndChangeService.activeBorrowers(current.tenantId()));
    }

    @GetMapping("/change/open")
    public ApiResponse<?> openChange(@RequestParam(required = false) String search,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(creditAndChangeService.changeRecords(
                current.tenantId(), HeldChange.Status.OPEN, blank(search), page, size));
    }

    @PostMapping("/change/{id}/collect")
    public ApiResponse<HeldChange> collectChange(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> request) {
        Long sessionId = request == null || request.get("cashSessionId") == null
                ? null : Long.valueOf(request.get("cashSessionId").toString());
        return ApiResponse.success("Change collected", creditAndChangeService.collectChange(
                current.tenantId(), activeBranch(), current.userId(), id, sessionId));
    }

    @PostMapping("/change/collect")
    public ApiResponse<HeldChange> collectChangeByReference(@RequestBody Map<String, Object> request) {
        Long sessionId = request.get("cashSessionId") == null
                ? null : Long.valueOf(request.get("cashSessionId").toString());
        return ApiResponse.success("Change collected", creditAndChangeService.collectChangeByReference(
                current.tenantId(), activeBranch(), current.userId(),
                String.valueOf(request.get("offlineReference")), sessionId));
    }

    @GetMapping("/cash/drawers")
    public ApiResponse<List<CashDrawer>> drawers() {
        return ApiResponse.success(operations.drawers(current.tenantId(), activeBranch()));
    }

    @GetMapping("/cash/session")
    public ApiResponse<CashSession> session(@RequestParam(required = false) Long branchId) {
        Long targetBranch = selectedBranch(branchId);
        return ApiResponse.success(cashSessions.findActiveSession(current.tenantId(), targetBranch, current.userId())
                .orElse(null));
    }

    @PostMapping("/cash/open")
    public ApiResponse<CashSession> open(@Valid @RequestBody OpenSessionRequest request) {
        return ApiResponse.success("Cash session opened", operations.openSession(current.tenantId(), activeBranch(), current.userId(), request));
    }

    @PostMapping("/cash/close")
    public ApiResponse<CashSession> close(@Valid @RequestBody CloseSessionRequest request) {
        return ApiResponse.success("Cash session closed", operations.closeSession(current.tenantId(), activeBranch(), current.userId(), request));
    }

    @PostMapping("/sales")
    public ApiResponse<Sale> sale(@Valid @RequestBody SaleRequest request) {
        return ApiResponse.success("Sale completed", operations.completeSale(current.tenantId(), selectedBranch(request.getBranchId()), current.userId(), request));
    }

    @GetMapping("/sales/recent")
    public ApiResponse<List<Sale>> recentSales(@RequestParam(required = false) Long branchId) {
        return ApiResponse.success(operations.recentSales(current.tenantId(), selectedBranch(branchId)));
    }

    @GetMapping("/sales/shift")
    public ApiResponse<List<Sale>> shiftSales(@RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) Long sessionId) {
        Long targetBranch = selectedBranch(branchId);
        CashSession session = sessionId == null
                ? cashSessions.findActiveSession(current.tenantId(), targetBranch, current.userId()).orElse(null)
                : cashSessions.findById(sessionId)
                .filter(cs -> cs.getTenantId().equals(current.tenantId()))
                .filter(cs -> cs.getBranchId().equals(targetBranch))
                .filter(cs -> cs.getCashierId().equals(current.userId()))
                .orElse(null);
        if (session == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(sales.findShiftSales(current.tenantId(), targetBranch, session.getCashierId(), session.getId()));
    }

    @GetMapping("/sales/receipt/{receiptNumber}")
    public ApiResponse<Sale> saleByReceipt(@PathVariable String receiptNumber) {
        Sale sale = sales.findByReceiptNumberAndTenantId(receiptNumber, current.tenantId()).orElseThrow();
        if (!sale.getBranchId().equals(activeBranch())) {
            throw new IllegalArgumentException("Sale not found for this branch.");
        }
        return ApiResponse.success(sale);
    }

    @PostMapping("/sales/{id}/void")
    public ApiResponse<Sale> voidSale(@PathVariable Long id, @RequestBody Map<String, String> request) {
        Sale sale = sales.findById(id).orElseThrow();
        if (!sale.getTenantId().equals(current.tenantId()) || !sale.getBranchId().equals(activeBranch())) {
            throw new IllegalArgumentException("Sale not found.");
        }
        sale.setStatus(Sale.SaleStatus.VOIDED);
        sale.setVoidReason(request.getOrDefault("reason", "Voided from mobile"));
        sale.setVoidedBy(current.userId());
        return ApiResponse.success("Sale voided", sales.save(sale));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications() {
        return ApiResponse.success(notifications.findByUserId(current.userId(), PageRequest.of(0, 50)));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Long>> unreadNotificationCount() {
        return ApiResponse.success(Map.of("count", notificationService.getUnreadCount(current.userId())));
    }

    @PostMapping("/notifications/mark-all-read")
    public ApiResponse<Map<String, Boolean>> markNotificationsRead() {
        notificationService.markAllRead(current.userId());
        return ApiResponse.success("Notifications marked as read", Map.of("ok", true));
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

    private Map<String, Object> branchProductRow(Product product, Long branchId) {
        Inventory stock = inventory.findByTenantIdAndBranchIdAndProductId(current.tenantId(), branchId, product.getId()).orElse(null);
        BigDecimal onHand = stock == null || stock.getQuantityOnHand() == null ? BigDecimal.ZERO : stock.getQuantityOnHand();
        BigDecimal reserved = stock == null || stock.getQuantityReserved() == null ? BigDecimal.ZERO : stock.getQuantityReserved();
        Map<String, Object> row = new HashMap<>();
        row.put("id", product.getId());
        row.put("tenantId", product.getTenantId());
        row.put("branchId", branchId);
        row.put("name", product.getName());
        row.put("sku", product.getSku());
        row.put("barcode", product.getBarcode());
        row.put("description", product.getDescription());
        row.put("sellingPriceUsd", product.getSellingPriceUsd());
        row.put("sellingPriceZwg", product.getSellingPriceZwg());
        row.put("costPriceUsd", product.getCostPriceUsd());
        row.put("categoryId", product.getCategory() == null ? null : product.getCategory().getId());
        row.put("imageUrl", product.getImageUrl());
        row.put("taxRate", product.getTaxRate());
        row.put("isTaxable", product.getIsTaxable());
        row.put("reorderLevel", product.getReorderLevel());
        row.put("quantityOnHand", onHand);
        row.put("quantityReserved", reserved);
        row.put("quantityAvailable", onHand.subtract(reserved));
        row.put("lowStock", product.getReorderLevel() != null && product.getReorderLevel().compareTo(BigDecimal.ZERO) > 0 && onHand.compareTo(product.getReorderLevel()) <= 0);
        return row;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }
}

