package com.retailzw.service;

import com.retailzw.dto.request.CreatePurchaseOrderRequest;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository purchaseOrderItems;
    private final InventoryRepository inventory;
    private final InventoryTransactionRepository inventoryTransactions;
    private final GoodsReceivedNoteRepository goodsReceivedNotes;
    private final BranchRepository branches;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final NotificationService notificationService;
    private final PurchaseOrderApprovalRepository purchaseOrderApprovals;

    public PurchaseOrder createPurchaseOrder(Long tenantId, Long branchId, Long userId,
                                             CreatePurchaseOrderRequest request) {
        requireUser(userId);
        Branch branch = branches.findById(branchId)
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));
        suppliers.findById(request.getSupplierId())
                .filter(supplier -> tenantId.equals(supplier.getTenantId()))
                .filter(supplier -> Boolean.TRUE.equals(supplier.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Select an active supplier for this shop."));
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Purchase order must have at least one product line.");
        }
        String poNumber = generatePoNumber(branch.getBranchCode());

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .poNumber(poNumber)
                .supplierId(request.getSupplierId())
                .currency(request.getCurrency() != null ? request.getCurrency() : com.retailzw.enums.CurrencyCode.USD)
                .notes(request.getNotes())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .status(PurchaseOrder.PoStatus.DRAFT)
                .createdBy(userId)
                .subtotalUsd(BigDecimal.ZERO)
                .totalUsd(BigDecimal.ZERO)
                .subtotalZwg(BigDecimal.ZERO)
                .totalZwg(BigDecimal.ZERO)
                .taxAmountUsd(BigDecimal.ZERO)
                .build();

        PurchaseOrder saved = purchaseOrders.save(po);
        BigDecimal subUsd = BigDecimal.ZERO;
        BigDecimal taxUsd = BigDecimal.ZERO;
        BigDecimal subZwg = BigDecimal.ZERO;

        for (CreatePurchaseOrderRequest.PoItemRequest item : request.getItems()) {
            Product product = products.findById(item.getProductId())
                    .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElseThrow(() -> new IllegalArgumentException("A selected product is not available for this shop."));
            inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, product.getId())
                    .orElseThrow(() -> new IllegalArgumentException(product.getName() + " is not assigned to this branch."));
            BigDecimal qty = item.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Purchase order quantities must be greater than zero.");
            }
            BigDecimal costUsd = item.getUnitCostUsd() != null ? item.getUnitCostUsd() : BigDecimal.ZERO;
            BigDecimal costZwg = item.getUnitCostZwg() != null ? item.getUnitCostZwg() : BigDecimal.ZERO;
            BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
            if (costUsd.compareTo(BigDecimal.ZERO) < 0 || costZwg.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Purchase costs and tax rates cannot be negative.");
            }

            BigDecimal lineTotalUsd = costUsd.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTaxUsd = lineTotalUsd.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotalZwg = costZwg.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            PurchaseOrderItem poi = PurchaseOrderItem.builder()
                    .purchaseOrder(saved)
                    .productId(item.getProductId())
                    .quantity(qty)
                    .quantityReceived(BigDecimal.ZERO)
                    .unitCostUsd(costUsd)
                    .unitCostZwg(costZwg)
                    .taxRate(taxRate)
                    .lineTotalUsd(lineTotalUsd.add(lineTaxUsd))
                    .lineTotalZwg(lineTotalZwg)
                    .notes(item.getNotes())
                    .build();
            purchaseOrderItems.save(poi);
            saved.getItems().add(poi);

            subUsd = subUsd.add(lineTotalUsd);
            taxUsd = taxUsd.add(lineTaxUsd);
            subZwg = subZwg.add(lineTotalZwg);
        }

        saved.setSubtotalUsd(subUsd);
        saved.setTaxAmountUsd(taxUsd);
        saved.setTotalUsd(subUsd.add(taxUsd));
        saved.setSubtotalZwg(subZwg);
        saved.setTotalZwg(subZwg);
        PurchaseOrder persisted = purchaseOrders.save(saved);
        log.info("Purchase order created tenant={} branch={} user={} po={} supplier={} items={} totalUsd={} totalZwg={}",
                tenantId, branchId, userId, persisted.getPoNumber(), persisted.getSupplierId(),
                request.getItems().size(), persisted.getTotalUsd(), persisted.getTotalZwg());
        return persisted;
    }

    public PurchaseOrder submitPurchaseOrder(Long poId) {
        PurchaseOrder po = findById(poId);
        requireStatus(po, PurchaseOrder.PoStatus.DRAFT, "Only draft purchase orders can be submitted.");
        po.setStatus(PurchaseOrder.PoStatus.SUBMITTED);
        PurchaseOrder saved = purchaseOrders.save(po);
        purchaseOrderApprovals.save(PurchaseOrderApproval.builder()
                .tenantId(po.getTenantId()).purchaseOrderId(po.getId())
                .action(PurchaseOrderApproval.Action.SUBMITTED)
                .comments("Submitted for approval").actedBy(po.getCreatedBy()).build());
        notificationService.notifyPOApprovalNeeded(po.getTenantId(), saved);
        log.info("Purchase order submitted tenant={} branch={} po={} id={}",
                saved.getTenantId(), saved.getBranchId(), saved.getPoNumber(), saved.getId());
        return saved;
    }

    public PurchaseOrder rejectPurchaseOrder(Long poId, Long managerId, String reason) {
        PurchaseOrder po = findById(poId);
        if (po.getStatus() != PurchaseOrder.PoStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted purchase orders can be rejected.");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason is required.");
        po.setStatus(PurchaseOrder.PoStatus.REJECTED);
        PurchaseOrder saved = purchaseOrders.save(po);
        purchaseOrderApprovals.save(PurchaseOrderApproval.builder()
                .tenantId(po.getTenantId()).purchaseOrderId(po.getId())
                .action(PurchaseOrderApproval.Action.REJECTED)
                .comments(reason.trim()).actedBy(managerId).build());
        return saved;
    }

    public PurchaseOrder approvePurchaseOrder(Long poId, Long managerId) {
        requireUser(managerId);
        PurchaseOrder po = lockById(poId);
        requireStatus(po, PurchaseOrder.PoStatus.SUBMITTED, "Only submitted purchase orders can be approved.");
        po.setStatus(PurchaseOrder.PoStatus.APPROVED);
        po.setApprovedBy(managerId);
        po.setApprovedAt(LocalDateTime.now());

        // Update quantity_on_order for each line item
        for (PurchaseOrderItem item : po.getItems()) {
            BigDecimal outstanding = nvl(item.getQuantity()).subtract(nvl(item.getQuantityReceived())).max(BigDecimal.ZERO);
            if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue;
            Inventory inv = inventory.lockStock(po.getTenantId(), po.getBranchId(), item.getProductId())
                    .orElseGet(() -> inventory.save(Inventory.builder()
                            .tenantId(po.getTenantId()).branchId(po.getBranchId()).productId(item.getProductId())
                            .quantityOnHand(BigDecimal.ZERO).quantityOnOrder(BigDecimal.ZERO)
                            .averageCostUsd(BigDecimal.ZERO).averageCostZwg(BigDecimal.ZERO).build()));
            inv.setQuantityOnOrder(nvl(inv.getQuantityOnOrder()).add(outstanding));
            inventory.save(inv);
        }
        PurchaseOrder saved = purchaseOrders.save(po);
        purchaseOrderApprovals.save(PurchaseOrderApproval.builder()
                .tenantId(po.getTenantId()).purchaseOrderId(po.getId())
                .action(PurchaseOrderApproval.Action.APPROVED)
                .comments("Purchase order approved").actedBy(managerId).build());
        log.info("Purchase order approved tenant={} branch={} po={} id={} manager={}",
                saved.getTenantId(), saved.getBranchId(), saved.getPoNumber(), saved.getId(), managerId);
        return saved;
    }

    public PurchaseOrder receiveGoods(Long poId, Map<Long, BigDecimal> receivedQuantities, Long userId) {
        requireUser(userId);
        PurchaseOrder po = lockById(poId);
        if (!EnumSet.of(PurchaseOrder.PoStatus.APPROVED, PurchaseOrder.PoStatus.ORDERED, PurchaseOrder.PoStatus.PARTIAL)
                .contains(po.getStatus())) {
            throw new IllegalStateException("Only approved, ordered or partially received purchase orders can receive goods.");
        }
        if (receivedQuantities == null || receivedQuantities.values().stream()
                .filter(Objects::nonNull).noneMatch(quantity -> quantity.compareTo(BigDecimal.ZERO) > 0)) {
            throw new IllegalArgumentException("Enter at least one received quantity.");
        }
        if (receivedQuantities.values().stream().filter(Objects::nonNull)
                .anyMatch(quantity -> quantity.compareTo(BigDecimal.ZERO) < 0)) {
            throw new IllegalArgumentException("Received quantities cannot be negative.");
        }
        Set<Long> lineIds = new HashSet<>();
        po.getItems().forEach(item -> lineIds.add(item.getId()));
        if (receivedQuantities.entrySet().stream()
                .anyMatch(entry -> !lineIds.contains(entry.getKey()) && entry.getValue() != null
                        && entry.getValue().compareTo(BigDecimal.ZERO) > 0)) {
            throw new IllegalArgumentException("A received line does not belong to this purchase order.");
        }

        boolean allFullyReceived = true;
        boolean anyReceived = false;
        String grnNumber = generateGrnNumber(po.getBranchId());

        for (PurchaseOrderItem item : po.getItems()) {
            BigDecimal receivedQty = nvl(receivedQuantities.get(item.getId()));
            if (receivedQty.compareTo(BigDecimal.ZERO) <= 0) {
                if (nvl(item.getQuantityReceived()).compareTo(nvl(item.getQuantity())) < 0) {
                    allFullyReceived = false;
                }
                continue;
            }
            BigDecimal outstanding = nvl(item.getQuantity()).subtract(nvl(item.getQuantityReceived()));
            if (receivedQty.compareTo(outstanding) > 0) {
                Product product = products.findById(item.getProductId()).orElse(null);
                String productName = product == null ? "product #" + item.getProductId() : product.getName();
                throw new IllegalArgumentException("Received quantity for " + productName + " is greater than the outstanding PO quantity.");
            }
            anyReceived = true;
            BigDecimal newReceived = nvl(item.getQuantityReceived()).add(receivedQty);
            item.setQuantityReceived(newReceived);
            purchaseOrderItems.save(item);
            if (newReceived.compareTo(nvl(item.getQuantity())) < 0) {
                allFullyReceived = false;
            }

            // Update inventory
            Inventory inv = inventory.lockStock(
                    po.getTenantId(), po.getBranchId(), item.getProductId())
                    .orElseGet(() -> {
                        Inventory newInv = Inventory.builder()
                                .tenantId(po.getTenantId())
                                .branchId(po.getBranchId())
                                .productId(item.getProductId())
                                .quantityOnHand(BigDecimal.ZERO)
                                .quantityOnOrder(BigDecimal.ZERO)
                                .averageCostUsd(BigDecimal.ZERO)
                                .averageCostZwg(BigDecimal.ZERO)
                                .build();
                        return inventory.save(newInv);
                    });

            BigDecimal existingQty = nvl(inv.getQuantityOnHand());
            BigDecimal oldCostUsd = inv.getAverageCostUsd() != null ? inv.getAverageCostUsd() : BigDecimal.ZERO;
            BigDecimal newCostUsd = item.getUnitCostUsd() != null ? item.getUnitCostUsd() : BigDecimal.ZERO;
            BigDecimal oldCostZwg = inv.getAverageCostZwg() != null ? inv.getAverageCostZwg() : BigDecimal.ZERO;
            BigDecimal newCostZwg = item.getUnitCostZwg() != null ? item.getUnitCostZwg() : BigDecimal.ZERO;

            // Weighted average cost
            BigDecimal totalQty = existingQty.add(receivedQty);
            if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newAvgUsd = existingQty.multiply(oldCostUsd)
                        .add(receivedQty.multiply(newCostUsd))
                        .divide(totalQty, 4, RoundingMode.HALF_UP);
                BigDecimal newAvgZwg = existingQty.multiply(oldCostZwg)
                        .add(receivedQty.multiply(newCostZwg))
                        .divide(totalQty, 4, RoundingMode.HALF_UP);
                inv.setAverageCostUsd(newAvgUsd);
                inv.setAverageCostZwg(newAvgZwg);
            }

            BigDecimal qtyBefore = nvl(inv.getQuantityOnHand());
            inv.setQuantityOnHand(qtyBefore.add(receivedQty));
            inv.setQuantityOnOrder(nvl(inv.getQuantityOnOrder()).subtract(receivedQty).max(BigDecimal.ZERO));
            inventory.save(inv);

            // Write inventory transaction
            inventoryTransactions.save(InventoryTransaction.builder()
                    .tenantId(po.getTenantId())
                    .branchId(po.getBranchId())
                    .productId(item.getProductId())
                    .type(InventoryTransaction.TransactionType.PURCHASE)
                    .quantity(receivedQty)
                    .quantityBefore(qtyBefore)
                    .quantityAfter(inv.getQuantityOnHand())
                    .unitCostUsd(item.getUnitCostUsd())
                    .unitCostZwg(item.getUnitCostZwg())
                    .referenceType("PURCHASE_ORDER")
                    .referenceId(po.getId())
                    .createdBy(userId)
                    .notes("GRN: " + grnNumber)
                    .build());
        }

        if (!anyReceived) throw new IllegalArgumentException("Enter at least one received quantity for a PO line.");
        po.setStatus(allFullyReceived ? PurchaseOrder.PoStatus.RECEIVED : PurchaseOrder.PoStatus.PARTIAL);

        // Create GRN
        goodsReceivedNotes.save(GoodsReceivedNote.builder()
                .tenantId(po.getTenantId())
                .branchId(po.getBranchId())
                .grnNumber(grnNumber)
                .purchaseOrderId(po.getId())
                .supplierId(po.getSupplierId())
                .receivedBy(userId)
                .notes("Received for PO " + po.getPoNumber())
                .build());

        PurchaseOrder saved = purchaseOrders.save(po);
        log.info("Goods received tenant={} branch={} po={} id={} status={} grn={} user={}",
                saved.getTenantId(), saved.getBranchId(), saved.getPoNumber(), saved.getId(),
                saved.getStatus(), grnNumber, userId);
        return saved;
    }

    public List<PurchaseOrder> autoGeneratePurchaseOrders(Long tenantId, Long branchId, Long userId) {
        requireUser(userId);
        List<Inventory> lowStockItems = inventory.findLowStockItems(tenantId, branchId);
        if (lowStockItems.isEmpty()) return Collections.emptyList();

        Branch branch = branches.findById(branchId)
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));

        // Group by default/preferred supplier — use product's cost price as estimate
        // For simplicity, group all items under one auto-generated PO per run (no supplier preference stored directly)
        // Group by finding any active supplier
        List<Supplier> activeSuppliers = suppliers.findByTenantIdAndIsActiveTrue(tenantId);
        if (activeSuppliers.isEmpty()) return Collections.emptyList();

        Supplier defaultSupplier = activeSuppliers.get(0);

        Set<Long> productsAlreadyOnOpenOrders = openOrderProductIds(tenantId, branchId);
        List<ReorderCandidate> candidates = new ArrayList<>();
        for (Inventory inv : lowStockItems) {
            if (productsAlreadyOnOpenOrders.contains(inv.getProductId()) || nvl(inv.getQuantityOnOrder()).compareTo(BigDecimal.ZERO) > 0) {
                continue;
            }
            Product product = products.findById(inv.getProductId())
                    .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                    .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                    .orElse(null);
            if (product == null) continue;
            BigDecimal reorderLevel = nvl(product.getReorderLevel());
            BigDecimal target = product.getMaxStockLevel() != null
                    && product.getMaxStockLevel().compareTo(reorderLevel) > 0
                    ? product.getMaxStockLevel() : reorderLevel.multiply(BigDecimal.valueOf(2));
            BigDecimal orderQty = target.subtract(nvl(inv.getQuantityOnHand())).subtract(nvl(inv.getQuantityOnOrder())).max(BigDecimal.ZERO);
            if (orderQty.compareTo(BigDecimal.ZERO) > 0) candidates.add(new ReorderCandidate(product, orderQty));
        }
        if (candidates.isEmpty()) return Collections.emptyList();

        BigDecimal subUsd = BigDecimal.ZERO;

        PurchaseOrder po = PurchaseOrder.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .poNumber(generatePoNumber(branch.getBranchCode()))
                .supplierId(defaultSupplier.getId())
                .currency(com.retailzw.enums.CurrencyCode.USD)
                .status(PurchaseOrder.PoStatus.DRAFT)
                .notes("Auto-generated reorder PO")
                .createdBy(userId)
                .subtotalUsd(BigDecimal.ZERO)
                .totalUsd(BigDecimal.ZERO)
                .subtotalZwg(BigDecimal.ZERO)
                .totalZwg(BigDecimal.ZERO)
                .taxAmountUsd(BigDecimal.ZERO)
                .build();
        PurchaseOrder savedPo = purchaseOrders.save(po);

        for (ReorderCandidate candidate : candidates) {
            Product product = candidate.product();
            BigDecimal orderQty = candidate.quantity();

            BigDecimal costUsd = product.getCostPriceUsd() != null ? product.getCostPriceUsd() : BigDecimal.ZERO;
            BigDecimal lineTotal = costUsd.multiply(orderQty).setScale(2, RoundingMode.HALF_UP);
            subUsd = subUsd.add(lineTotal);

            PurchaseOrderItem poi = PurchaseOrderItem.builder()
                    .purchaseOrder(savedPo)
                    .productId(product.getId())
                    .quantity(orderQty)
                    .quantityReceived(BigDecimal.ZERO)
                    .unitCostUsd(costUsd)
                    .unitCostZwg(product.getCostPriceZwg())
                    .taxRate(BigDecimal.ZERO)
                    .lineTotalUsd(lineTotal)
                    .lineTotalZwg(product.getCostPriceZwg() != null
                            ? product.getCostPriceZwg().multiply(orderQty).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO)
                    .build();
            purchaseOrderItems.save(poi);
            savedPo.getItems().add(poi);
        }

        savedPo.setSubtotalUsd(subUsd);
        savedPo.setTotalUsd(subUsd);
        purchaseOrders.save(savedPo);
        log.info("Auto purchase order generated tenant={} branch={} po={} lowStockLines={} totalUsd={}",
                tenantId, branchId, savedPo.getPoNumber(), candidates.size(), savedPo.getTotalUsd());
        return List.of(savedPo);
    }

    public PurchaseOrder cancelPurchaseOrder(Long poId) {
        PurchaseOrder po = lockById(poId);
        if (PurchaseOrder.PoStatus.RECEIVED.equals(po.getStatus())) {
            throw new IllegalStateException("Received purchase orders cannot be cancelled.");
        }
        if (PurchaseOrder.PoStatus.CANCELLED.equals(po.getStatus())) return po;
        if (EnumSet.of(PurchaseOrder.PoStatus.APPROVED, PurchaseOrder.PoStatus.ORDERED, PurchaseOrder.PoStatus.PARTIAL)
                .contains(po.getStatus())) {
            for (PurchaseOrderItem item : po.getItems()) {
                BigDecimal outstanding = nvl(item.getQuantity()).subtract(nvl(item.getQuantityReceived())).max(BigDecimal.ZERO);
                if (outstanding.compareTo(BigDecimal.ZERO) == 0) continue;
                inventory.lockStock(po.getTenantId(), po.getBranchId(), item.getProductId()).ifPresent(inv -> {
                    inv.setQuantityOnOrder(nvl(inv.getQuantityOnOrder()).subtract(outstanding).max(BigDecimal.ZERO));
                    inventory.save(inv);
                });
            }
        }
        po.setStatus(PurchaseOrder.PoStatus.CANCELLED);
        return purchaseOrders.save(po);
    }

    public PurchaseOrder findById(Long id) {
        return purchaseOrders.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + id));
    }

    private PurchaseOrder lockById(Long id) {
        return purchaseOrders.lockById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + id));
    }

    private Set<Long> openOrderProductIds(Long tenantId, Long branchId) {
        Set<Long> productIds = new HashSet<>();
        for (PurchaseOrder.PoStatus status : EnumSet.of(PurchaseOrder.PoStatus.DRAFT, PurchaseOrder.PoStatus.SUBMITTED,
                PurchaseOrder.PoStatus.APPROVED, PurchaseOrder.PoStatus.ORDERED, PurchaseOrder.PoStatus.PARTIAL)) {
            for (PurchaseOrder po : purchaseOrders.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, status)) {
                po.getItems().stream()
                        .filter(item -> nvl(item.getQuantity()).subtract(nvl(item.getQuantityReceived())).compareTo(BigDecimal.ZERO) > 0)
                        .map(PurchaseOrderItem::getProductId)
                        .forEach(productIds::add);
            }
        }
        return productIds;
    }

    private void requireStatus(PurchaseOrder po, PurchaseOrder.PoStatus expected, String message) {
        if (!expected.equals(po.getStatus())) throw new IllegalStateException(message);
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("A signed-in user is required for purchasing.");
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ReorderCandidate(Product product, BigDecimal quantity) {}

    private String generatePoNumber(String branchCode) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        return "PO-" + branchCode + "-" + date + "-" + seq;
    }

    private String generateGrnNumber(Long branchId) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        return "GRN-" + branchId + "-" + date + "-" + seq;
    }
}
