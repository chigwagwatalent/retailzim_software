package com.retailzw.service;

import com.retailzw.dto.request.CreateReturnRequest;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReturnService {

    private final ReturnRepository returns;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final InventoryRepository inventory;
    private final InventoryTransactionRepository inventoryTransactions;
    private final CustomerRepository customers;
    private final LoyaltyTransactionRepository loyaltyTransactions;
    private final SystemSettingRepository systemSettings;
    private final BranchRepository branches;

    public Return processReturn(Long tenantId, Long branchId, Long cashierId,
                                CreateReturnRequest request) {
        // 1. Find original sale
        Sale originalSale = sales.findByReceiptNumberAndTenantId(request.getOriginalReceiptNumber(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sale not found: " + request.getOriginalReceiptNumber()));

        // 2. Validate sale is COMPLETED
        if (originalSale.getStatus() != Sale.SaleStatus.COMPLETED) {
            throw new IllegalStateException("Sale " + request.getOriginalReceiptNumber()
                    + " is not eligible for return (status: " + originalSale.getStatus() + ")");
        }

        if (!originalSale.getBranchId().equals(branchId)) {
            throw new IllegalArgumentException("Sale does not belong to the selected branch.");
        }

        // 3. Validate return window
        int windowDays = getReturnWindowDays(tenantId);
        if (originalSale.getCreatedAt().plusDays(windowDays).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Return window of " + windowDays + " days has expired.");
        }

        // 4. Validate quantities don't exceed original
        List<SaleItem> originalItems = saleItems.findBySaleId(originalSale.getId());
        Map<Long, BigDecimal> alreadyReturned = returnedQuantities(tenantId, originalSale.getId());
        for (CreateReturnRequest.ReturnItemRequest ri : request.getItems()) {
            BigDecimal originalQty = originalItems.stream()
                    .filter(oi -> oi.getProductId().equals(ri.getProductId()))
                    .map(SaleItem::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previousQty = alreadyReturned.getOrDefault(ri.getProductId(), BigDecimal.ZERO);
            BigDecimal remainingQty = originalQty.subtract(previousQty);
            if (ri.getQuantity() == null || ri.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Return quantity must be greater than zero.");
            }
            if (ri.getQuantity().compareTo(remainingQty) > 0) {
                throw new IllegalArgumentException(
                        "Return quantity for product " + ri.getProductId()
                                + " exceeds remaining sold quantity.");
            }
        }

        // 5. Generate return number
        Branch branch = branches.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        String returnNumber = generateReturnNumber(branch.getBranchCode());

        // 6. Calculate total refund
        BigDecimal totalRefund = request.getItems().stream()
                .map(ri -> {
                    SaleItem si = originalItems.stream()
                            .filter(oi -> oi.getProductId().equals(ri.getProductId()))
                            .findFirst().orElse(null);
                    if (si == null) return BigDecimal.ZERO;
                    return refundAmount(si, ri.getQuantity());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean requiresApproval = requiresManagerApproval(tenantId, totalRefund);

        // 7. Create Return entity
        Return ret = Return.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .returnNumber(returnNumber)
                .originalSaleId(originalSale.getId())
                .originalReceiptNumber(originalSale.getReceiptNumber())
                .customerId(originalSale.getCustomerId())
                .cashierId(cashierId)
                .reason(request.getReason())
                .refundMethod(request.getRefundMethod())
                .currency(originalSale.getCurrency())
                .totalRefund(totalRefund)
                .notes(request.getNotes())
                .requiresApproval(requiresApproval)
                .isApproved(!requiresApproval)
                .loyaltyPointsReversed(0)
                .build();
        Return savedReturn = returns.save(ret);

        // 8. Create ReturnItems and restore inventory
        int totalPointsToReverse = 0;
        for (CreateReturnRequest.ReturnItemRequest ri : request.getItems()) {
            SaleItem si = originalItems.stream()
                    .filter(oi -> oi.getProductId().equals(ri.getProductId()))
                    .findFirst().orElse(null);
            if (si == null) continue;

            BigDecimal refundAmt = refundAmount(si, ri.getQuantity());

            ReturnItem returnItem = ReturnItem.builder()
                    .returnRecord(savedReturn)
                    .productId(ri.getProductId())
                    .productName(si.getProductName())
                    .quantity(ri.getQuantity())
                    .unitPrice(si.getUnitPrice())
                    .refundAmount(refundAmt)
                    .restockItem(Boolean.TRUE.equals(ri.getRestockItem()))
                    .notes(ri.getNotes())
                    .build();
            savedReturn.getItems().add(returnItem);

            // Restore inventory
            if (Boolean.TRUE.equals(ri.getRestockItem())) {
                Inventory inv = inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, ri.getProductId())
                        .orElseGet(() -> inventory.save(Inventory.builder()
                                .tenantId(tenantId).branchId(branchId)
                                .productId(ri.getProductId())
                                .quantityOnHand(BigDecimal.ZERO)
                                .quantityOnOrder(BigDecimal.ZERO)
                                .averageCostUsd(BigDecimal.ZERO)
                                .averageCostZwg(BigDecimal.ZERO)
                                .build()));
                BigDecimal qtyBefore = inv.getQuantityOnHand();
                inv.setQuantityOnHand(inv.getQuantityOnHand().add(ri.getQuantity()));
                inventory.save(inv);

                inventoryTransactions.save(InventoryTransaction.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .productId(ri.getProductId())
                        .type(InventoryTransaction.TransactionType.RETURN)
                        .quantity(ri.getQuantity())
                        .quantityBefore(qtyBefore)
                        .quantityAfter(inv.getQuantityOnHand())
                        .unitCostUsd(si.getCostPrice())
                        .referenceType("RETURN")
                        .referenceId(savedReturn.getId())
                        .createdBy(cashierId)
                        .notes("Return " + returnNumber)
                        .build());
            }

            // Calculate proportional loyalty points to reverse
            if (originalSale.getCustomerId() != null
                    && originalSale.getLoyaltyPointsEarned() != null
                    && originalSale.getLoyaltyPointsEarned() > 0
                    && originalSale.getGrandTotal().compareTo(BigDecimal.ZERO) > 0) {
                double proportion = refundAmt.doubleValue() / originalSale.getGrandTotal().doubleValue();
                totalPointsToReverse += (int) Math.round(originalSale.getLoyaltyPointsEarned() * proportion);
            }
        }

        updateOriginalSaleReturnStatus(originalSale);

        // 9. Reverse loyalty points
        if (originalSale.getCustomerId() != null && totalPointsToReverse > 0) {
            final int pointsToReverse = totalPointsToReverse;
            customers.findById(originalSale.getCustomerId()).ifPresent(customer -> {
                int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
                int newPoints = Math.max(0, currentPoints - pointsToReverse);
                customer.setLoyaltyPoints(newPoints);
                customers.save(customer);

                loyaltyTransactions.save(LoyaltyTransaction.builder()
                        .tenantId(tenantId)
                        .customerId(customer.getId())
                        .type(LoyaltyTransaction.LoyaltyTransactionType.REVERSAL)
                        .points(-pointsToReverse)
                        .pointsBalanceAfter(newPoints)
                        .referenceType("RETURN")
                        .referenceId(savedReturn.getId())
                        .description("Points reversed for return " + returnNumber)
                        .createdBy(cashierId)
                        .build());

                savedReturn.setLoyaltyPointsReversed(pointsToReverse);
            });
        }

        Return persisted = returns.save(savedReturn);
        log.info("Return processed tenant={} branch={} cashier={} return={} originalReceipt={} total={} currency={} items={} requiresApproval={}",
                tenantId, branchId, cashierId, persisted.getReturnNumber(), originalSale.getReceiptNumber(),
                persisted.getTotalRefund(), persisted.getCurrency(), persisted.getItems().size(), persisted.getRequiresApproval());
        return persisted;
    }

    private Map<Long, BigDecimal> returnedQuantities(Long tenantId, Long originalSaleId) {
        Map<Long, BigDecimal> quantities = new HashMap<>();
        returns.findByOriginalSaleIdAndTenantId(originalSaleId, tenantId).forEach(returnRecord ->
                returnRecord.getItems().forEach(item ->
                        quantities.merge(item.getProductId(), item.getQuantity(), BigDecimal::add)));
        return quantities;
    }

    private BigDecimal refundAmount(SaleItem saleItem, BigDecimal returnQuantity) {
        BigDecimal gross = saleItem.getUnitPrice().multiply(returnQuantity);
        BigDecimal proportionalDiscount = saleItem.getDiscountAmount() == null || saleItem.getQuantity().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : saleItem.getDiscountAmount().multiply(returnQuantity.divide(saleItem.getQuantity(), 4, RoundingMode.HALF_UP));
        return gross.subtract(proportionalDiscount).setScale(2, RoundingMode.HALF_UP);
    }

    private void updateOriginalSaleReturnStatus(Sale originalSale) {
        BigDecimal historicalRefunds = returns.findByOriginalSaleIdAndTenantId(originalSale.getId(), originalSale.getTenantId()).stream()
                .map(Return::getTotalRefund)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        originalSale.setStatus(historicalRefunds.compareTo(originalSale.getGrandTotal()) >= 0
                ? Sale.SaleStatus.REFUNDED
                : Sale.SaleStatus.PARTIAL_REFUND);
        sales.save(originalSale);
    }

    public boolean requiresManagerApproval(Long tenantId, BigDecimal returnTotal) {
        return systemSettings.findByTenantIdAndSettingKey(tenantId, "RETURN_APPROVAL_THRESHOLD_USD")
                .map(s -> {
                    try {
                        BigDecimal threshold = new BigDecimal(s.getSettingValue());
                        return returnTotal.compareTo(threshold) > 0;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .orElse(false);
    }

    public Page<Return> getReturns(Long tenantId, Long branchId, LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null) {
            return returns.findByTenantIdAndBranchIdAndCreatedAtBetween(
                    tenantId, branchId,
                    from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                    pageable);
        }
        return returns.findByTenantIdAndBranchId(tenantId, branchId, pageable);
    }

    private int getReturnWindowDays(Long tenantId) {
        return systemSettings.findByTenantIdAndSettingKey(tenantId, "RETURN_WINDOW_DAYS")
                .map(s -> {
                    try { return Integer.parseInt(s.getSettingValue()); } catch (Exception e) { return 7; }
                })
                .orElse(7);
    }

    private String generateReturnNumber(String branchCode) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%04d", (int) (Math.random() * 9000) + 1000);
        return "RET-" + branchCode + "-" + date + "-" + seq;
    }
}
