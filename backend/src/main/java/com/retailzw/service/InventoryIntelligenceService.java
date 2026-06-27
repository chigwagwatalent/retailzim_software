package com.retailzw.service;

import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryIntelligenceService {
    private final StockTransferRepository transfers;
    private final StocktakeSessionRepository stocktakes;
    private final StocktakeItemRepository stocktakeItems;
    private final StockVarianceInvestigationRepository investigations;
    private final InventoryLotRepository lots;
    private final ProductSupplierRepository productSuppliers;
    private final InventoryRepository inventory;
    private final InventoryTransactionRepository inventoryTransactions;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final BranchRepository branches;

    @Transactional(readOnly = true)
    public List<StockTransfer> transfers(Long tenantId) {
        List<StockTransfer> rows = transfers.findByTenantId(tenantId, PageRequest.of(0, 100)).getContent();
        rows.forEach(transfer -> transfer.getItems().size());
        return rows;
    }

    public List<StocktakeSession> stocktakes(Long tenantId, Long branchId) {
        return stocktakes.findByTenantIdAndBranchId(tenantId, branchId, PageRequest.of(0, 100)).getContent();
    }

    public List<InventoryLot> lots(Long tenantId, Long branchId) {
        return lots.findByTenantIdAndBranchIdOrderByExpiryDateAsc(tenantId, branchId);
    }

    public List<StockVarianceInvestigation> investigations(Long tenantId, Long branchId) {
        return investigations.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public List<StocktakeItem> stocktakeItems(Long sessionId) {
        return stocktakeItems.findByStocktakeSessionId(sessionId);
    }

    public List<ProductSupplier> supplierPrices(Long tenantId) {
        return productSuppliers.findByTenantIdOrderByProductIdAscCostPriceUsdAsc(tenantId);
    }

    @Transactional
    public StockTransfer createTransfer(Long tenantId, Long fromBranchId, Long toBranchId,
                                        Long productId, BigDecimal quantity, String notes, Long userId) {
        if (fromBranchId.equals(toBranchId)) throw new IllegalArgumentException("Choose two different branches.");
        ownedBranch(tenantId, fromBranchId);
        ownedBranch(tenantId, toBranchId);
        Product product = ownedProduct(tenantId, productId);
        BigDecimal qty = positive(quantity, "Transfer quantity must be greater than zero.");
        Inventory source = inventory.lockStock(tenantId, fromBranchId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Product is not stocked at the source branch."));
        if (available(source).compareTo(qty) < 0) {
            throw new IllegalArgumentException(product.getName() + " has only " + available(source) + " available.");
        }
        StockTransfer transfer = StockTransfer.builder()
                .tenantId(tenantId).transferNumber(reference("TRF")).fromBranchId(fromBranchId)
                .toBranchId(toBranchId).status(StockTransfer.TransferStatus.PENDING)
                .initiatedBy(userId).notes(clean(notes)).build();
        transfer.getItems().add(StockTransferItem.builder()
                .stockTransfer(transfer).productId(productId).quantitySent(qty)
                .quantityReceived(BigDecimal.ZERO).unitCostUsd(source.getAverageCostUsd())
                .unitCostZwg(source.getAverageCostZwg()).build());
        return transfers.save(transfer);
    }

    @Transactional
    public StockTransfer dispatchTransfer(Long tenantId, Long transferId, Long userId) {
        StockTransfer transfer = ownedTransfer(tenantId, transferId);
        if (transfer.getStatus() != StockTransfer.TransferStatus.PENDING) {
            throw new IllegalStateException("Only pending transfers can be dispatched.");
        }
        for (StockTransferItem item : transfer.getItems()) {
            Inventory source = inventory.lockStock(tenantId, transfer.getFromBranchId(), item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Source stock is missing."));
            BigDecimal before = nvl(source.getQuantityOnHand());
            if (available(source).compareTo(item.getQuantitySent()) < 0) {
                throw new IllegalArgumentException("Source stock changed and is no longer sufficient.");
            }
            source.setQuantityOnHand(before.subtract(item.getQuantitySent()));
            inventory.save(source);
            writeInventoryTransaction(tenantId, transfer.getFromBranchId(), item.getProductId(),
                    InventoryTransaction.TransactionType.TRANSFER_OUT, item.getQuantitySent().negate(),
                    before, source.getQuantityOnHand(), transfer.getId(), userId);
        }
        transfer.setStatus(StockTransfer.TransferStatus.IN_TRANSIT);
        return transfers.save(transfer);
    }

    @Transactional
    public StockTransfer receiveTransfer(Long tenantId, Long transferId, Long userId) {
        StockTransfer transfer = ownedTransfer(tenantId, transferId);
        if (transfer.getStatus() != StockTransfer.TransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only in-transit stock can be received.");
        }
        for (StockTransferItem item : transfer.getItems()) {
            Inventory target = inventory.lockStock(tenantId, transfer.getToBranchId(), item.getProductId())
                    .orElseGet(() -> inventory.save(Inventory.builder()
                            .tenantId(tenantId).branchId(transfer.getToBranchId()).productId(item.getProductId())
                            .quantityOnHand(BigDecimal.ZERO).quantityReserved(BigDecimal.ZERO)
                            .quantityOnOrder(BigDecimal.ZERO).averageCostUsd(nvl(item.getUnitCostUsd()))
                            .averageCostZwg(nvl(item.getUnitCostZwg())).build()));
            BigDecimal before = nvl(target.getQuantityOnHand());
            target.setQuantityOnHand(before.add(item.getQuantitySent()));
            inventory.save(target);
            item.setQuantityReceived(item.getQuantitySent());
            writeInventoryTransaction(tenantId, transfer.getToBranchId(), item.getProductId(),
                    InventoryTransaction.TransactionType.TRANSFER_IN, item.getQuantitySent(),
                    before, target.getQuantityOnHand(), transfer.getId(), userId);
        }
        transfer.setStatus(StockTransfer.TransferStatus.RECEIVED);
        transfer.setReceivedBy(userId);
        transfer.setReceivedAt(LocalDateTime.now());
        return transfers.save(transfer);
    }

    @Transactional
    public StocktakeSession startStocktake(Long tenantId, Long branchId, String notes, Long userId) {
        ownedBranch(tenantId, branchId);
        if (!stocktakes.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, StocktakeSession.StocktakeStatus.OPEN).isEmpty()
                || !stocktakes.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, StocktakeSession.StocktakeStatus.COUNTING).isEmpty()) {
            throw new IllegalStateException("Finish the active stocktake before starting another.");
        }
        StocktakeSession session = StocktakeSession.builder()
                .tenantId(tenantId).branchId(branchId).sessionNumber(reference("STK"))
                .status(StocktakeSession.StocktakeStatus.COUNTING).startedBy(userId).notes(clean(notes)).build();
        for (Inventory stock : inventory.findByTenantIdAndBranchId(tenantId, branchId)) {
            session.getItems().add(StocktakeItem.builder()
                    .stocktakeSession(session).productId(stock.getProductId())
                    .systemQuantity(nvl(stock.getQuantityOnHand())).isCounted(false).build());
        }
        return stocktakes.save(session);
    }

    @Transactional
    public StocktakeItem countStock(Long tenantId, Long sessionId, Long itemId,
                                    BigDecimal countedQuantity, String notes, Long userId) {
        StocktakeSession session = ownedStocktake(tenantId, sessionId);
        if (session.getStatus() != StocktakeSession.StocktakeStatus.COUNTING) {
            throw new IllegalStateException("This stocktake is not accepting counts.");
        }
        StocktakeItem item = stocktakeItems.findById(itemId)
                .filter(i -> i.getStocktakeSession().getId().equals(sessionId))
                .orElseThrow(() -> new IllegalArgumentException("Stocktake item not found."));
        BigDecimal counted = nonNegative(countedQuantity, "Count cannot be negative.");
        BigDecimal variance = counted.subtract(nvl(item.getSystemQuantity()));
        Inventory stock = inventory.findByTenantIdAndBranchIdAndProductId(tenantId, session.getBranchId(), item.getProductId())
                .orElseThrow();
        item.setCountedQuantity(counted);
        item.setVarianceValueUsd(variance.multiply(nvl(stock.getAverageCostUsd())));
        item.setVarianceValueZwg(variance.multiply(nvl(stock.getAverageCostZwg())));
        item.setIsCounted(true);
        item.setCountedBy(userId);
        item.setCountedAt(LocalDateTime.now());
        item.setNotes(clean(notes));
        return stocktakeItems.save(item);
    }

    @Transactional
    public StocktakeSession submitStocktake(Long tenantId, Long sessionId, Long userId) {
        StocktakeSession session = ownedStocktake(tenantId, sessionId);
        List<StocktakeItem> items = stocktakeItems.findByStocktakeSessionId(sessionId);
        if (items.isEmpty() || items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getIsCounted()))) {
            throw new IllegalStateException("Count every stocktake line before submitting.");
        }
        for (StocktakeItem item : items) {
            BigDecimal variance = nvl(item.getCountedQuantity()).subtract(nvl(item.getSystemQuantity()));
            if (variance.compareTo(BigDecimal.ZERO) != 0) {
                investigations.save(StockVarianceInvestigation.builder()
                        .tenantId(tenantId).branchId(session.getBranchId()).stocktakeSessionId(sessionId)
                        .stocktakeItemId(item.getId()).productId(item.getProductId())
                        .systemQuantity(item.getSystemQuantity()).countedQuantity(item.getCountedQuantity())
                        .variance(variance).status(StockVarianceInvestigation.Status.OPEN)
                        .createdBy(userId).build());
            }
        }
        session.setStatus(StocktakeSession.StocktakeStatus.SUBMITTED);
        session.setSubmittedBy(userId);
        session.setSubmittedAt(LocalDateTime.now());
        return stocktakes.save(session);
    }

    @Transactional
    public StockVarianceInvestigation resolveInvestigation(Long tenantId, Long investigationId,
                                                            String reason, String notes, Long userId) {
        StockVarianceInvestigation investigation = investigations.findById(investigationId)
                .filter(i -> i.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Variance investigation not found."));
        investigation.setReason(clean(reason));
        investigation.setResolutionNotes(required(notes, "Resolution notes are required."));
        investigation.setStatus(StockVarianceInvestigation.Status.RESOLVED);
        investigation.setResolvedBy(userId);
        investigation.setResolvedAt(LocalDateTime.now());
        return investigations.save(investigation);
    }

    @Transactional
    public StocktakeSession approveStocktake(Long tenantId, Long sessionId, Long userId) {
        StocktakeSession session = ownedStocktake(tenantId, sessionId);
        if (session.getStatus() != StocktakeSession.StocktakeStatus.SUBMITTED) {
            throw new IllegalStateException("Only submitted stocktakes can be approved.");
        }
        boolean unresolved = investigations.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, session.getBranchId())
                .stream().anyMatch(i -> sessionId.equals(i.getStocktakeSessionId())
                        && i.getStatus() != StockVarianceInvestigation.Status.RESOLVED);
        if (unresolved) throw new IllegalStateException("Resolve all stock variances before approval.");
        for (StocktakeItem item : stocktakeItems.findByStocktakeSessionId(sessionId)) {
            Inventory stock = inventory.lockStock(tenantId, session.getBranchId(), item.getProductId()).orElseThrow();
            BigDecimal before = nvl(stock.getQuantityOnHand());
            stock.setQuantityOnHand(item.getCountedQuantity());
            stock.setLastCountedAt(LocalDateTime.now());
            inventory.save(stock);
            writeInventoryTransaction(tenantId, session.getBranchId(), item.getProductId(),
                    InventoryTransaction.TransactionType.STOCKTAKE,
                    item.getCountedQuantity().subtract(before), before, item.getCountedQuantity(), sessionId, userId);
        }
        session.setStatus(StocktakeSession.StocktakeStatus.APPROVED);
        session.setApprovedBy(userId);
        session.setApprovedAt(LocalDateTime.now());
        return stocktakes.save(session);
    }

    @Transactional
    public InventoryLot saveLot(Long tenantId, Long branchId, Long productId, String batchNumber,
                                String serialNumber, LocalDate expiryDate, BigDecimal quantity,
                                Long supplierId, String notes) {
        ownedBranch(tenantId, branchId);
        Product product = ownedProduct(tenantId, productId);
        if (product.getTrackingMode() == Product.TrackingMode.SERIAL && clean(serialNumber) == null) {
            throw new IllegalArgumentException("Serial number is required for this product.");
        }
        if (product.getTrackingMode() == Product.TrackingMode.BATCH && clean(batchNumber) == null) {
            throw new IllegalArgumentException("Batch number is required for this product.");
        }
        InventoryLot.Status status = expiryDate != null && expiryDate.isBefore(LocalDate.now())
                ? InventoryLot.Status.EXPIRED : InventoryLot.Status.AVAILABLE;
        return lots.save(InventoryLot.builder()
                .tenantId(tenantId).branchId(branchId).productId(productId)
                .batchNumber(clean(batchNumber)).serialNumber(clean(serialNumber)).expiryDate(expiryDate)
                .quantityOnHand(nonNegative(quantity, "Quantity cannot be negative."))
                .supplierId(supplierId).notes(clean(notes)).status(status).build());
    }

    @Transactional
    public Product setTracking(Long tenantId, Long productId, Product.TrackingMode mode, boolean expiryTracking) {
        Product product = ownedProduct(tenantId, productId);
        product.setTrackingMode(mode == null ? Product.TrackingMode.NONE : mode);
        product.setExpiryTracking(expiryTracking);
        return products.save(product);
    }

    @Transactional
    public ProductSupplier saveSupplierPrice(Long tenantId, Long productId, Long supplierId,
                                              BigDecimal usd, BigDecimal zwg, BigDecimal minimumOrder,
                                              Integer leadDays, boolean preferred) {
        ownedProduct(tenantId, productId);
        suppliers.findById(supplierId).filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found."));
        ProductSupplier quote = productSuppliers.findByProductIdAndSupplierId(productId, supplierId)
                .orElseGet(() -> ProductSupplier.builder().tenantId(tenantId).productId(productId).supplierId(supplierId).build());
        quote.setCostPriceUsd(nonNegative(usd, "USD price cannot be negative."));
        quote.setCostPriceZwg(nonNegative(zwg, "ZWG price cannot be negative."));
        quote.setMinimumOrderQty(nonNegative(minimumOrder, "Minimum order cannot be negative."));
        quote.setLeadTimeDays(leadDays == null ? 0 : Math.max(0, leadDays));
        quote.setIsPreferred(preferred);
        if (preferred) {
            productSuppliers.findByProductId(productId).stream()
                    .filter(existing -> !Objects.equals(existing.getId(), quote.getId()))
                    .forEach(existing -> {
                        existing.setIsPreferred(false);
                        productSuppliers.save(existing);
                    });
        }
        return productSuppliers.save(quote);
    }

    private void writeInventoryTransaction(Long tenantId, Long branchId, Long productId,
                                           InventoryTransaction.TransactionType type, BigDecimal quantity,
                                           BigDecimal before, BigDecimal after, Long referenceId, Long userId) {
        inventoryTransactions.save(InventoryTransaction.builder()
                .tenantId(tenantId).branchId(branchId).productId(productId).type(type)
                .quantity(quantity).quantityBefore(before).quantityAfter(after)
                .referenceType(type.name()).referenceId(referenceId).createdBy(userId).build());
    }

    private StockTransfer ownedTransfer(Long tenantId, Long id) {
        return transfers.findById(id).filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found."));
    }
    private StocktakeSession ownedStocktake(Long tenantId, Long id) {
        return stocktakes.findById(id).filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Stocktake not found."));
    }
    private Product ownedProduct(Long tenantId, Long id) {
        return products.findById(id).filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
    }
    private Branch ownedBranch(Long tenantId, Long id) {
        return branches.findById(id).filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
    }
    private BigDecimal available(Inventory stock) { return nvl(stock.getQuantityOnHand()).subtract(nvl(stock.getQuantityReserved())); }
    private BigDecimal positive(BigDecimal value, String message) {
        BigDecimal result = nvl(value);
        if (result.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(message);
        return result;
    }
    private BigDecimal nonNegative(BigDecimal value, String message) {
        BigDecimal result = nvl(value);
        if (result.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(message);
        return result;
    }
    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String required(String value, String message) {
        String clean = clean(value);
        if (clean == null) throw new IllegalArgumentException(message);
        return clean;
    }
    private String reference(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }
}
