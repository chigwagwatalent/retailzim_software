package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasPriceRequest;
import com.retailzw.dto.request.GasExpenseRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
import com.retailzw.dto.request.GasStockReconciliationRequest;
import com.retailzw.dto.request.GasTankRequest;
import com.retailzw.dto.request.OpenGasShiftRequest;
import com.retailzw.dto.request.GasSalePaymentRequest;
import com.retailzw.dto.request.GasSaleTankRequest;
import com.retailzw.dto.request.GasTankClosingWeightRequest;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.GasShiftStatus;
import com.retailzw.enums.GasTankStatus;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class GasOperationsService {
    private final BranchRepository branches;
    private final GasTankRepository tanks;
    private final GasPriceRepository prices;
    private final GasShiftRepository shifts;
    private final GasSaleRepository sales;
    private final GasRestockRepository restocks;
    private final GasExpenseRepository expenses;
    private final GasStockAdjustmentRepository stockAdjustments;
    private final TenantSubscriptionRepository subscriptions;
    private final SaasPlanRepository plans;
    private final GasShiftTankRepository shiftTanks;
    private final GasSaleTankAllocationRepository saleTankAllocations;
    private final GasSalePaymentRepository salePayments;
    private final HeldChangeRepository heldChange;

    private static final List<BigDecimal> ZIMBABWE_LPG_WEIGHTS_KG = List.of(
            new BigDecimal("1.000"),
            new BigDecimal("2.000"),
            new BigDecimal("3.000"),
            new BigDecimal("5.000"),
            new BigDecimal("9.000"),
            new BigDecimal("14.000"),
            new BigDecimal("19.000"),
            new BigDecimal("48.000")
    );

    public List<GasTank> tanks(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId);
    }

    public Page<GasTank> tankPage(Long tenantId, Long branchId, Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId, pageable);
    }

    public List<GasPrice> prices(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return prices.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId);
    }

    @Transactional
    public GasTank createTank(Long tenantId, GasTankRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        enforceTankLimit(tenantId);
        TankWeights weights = resolveTankWeights(request, null);
        BigDecimal capacityKg = weights.capacityKg();
        BigDecimal currentKg = weights.currentNetKg();
        BigDecimal reorderLevelKg = nvl(request.getReorderLevelKg());
        validateTankLevels(capacityKg, currentKg, reorderLevelKg);
        GasTank tank = tanks.save(GasTank.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(requiredText(request.getName(), "Tank name is required."))
                .productName(requiredText(request.getProductName(), "Product name is required."))
                .tareWeightKg(weights.tareKg())
                .capacityKg(capacityKg)
                .fullGrossWeightKg(weights.fullGrossKg())
                .currentKg(currentKg)
                .reorderLevelKg(reorderLevelKg)
                .status(request.getStatus() == null ? GasTankStatus.ACTIVE : request.getStatus())
                .build());
        if (currentKg.compareTo(BigDecimal.ZERO) > 0) {
            recordAdjustment(tank, BigDecimal.ZERO, currentKg, "OPENING_STOCK",
                    "Opening tank quantity captured during setup.", null);
        }
        return tank;
    }

    @Transactional
    public GasTank updateTank(Long tenantId, Long tankId, GasTankRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasTank tank = tanks.lockTank(tenantId, branchId, tankId)
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        BigDecimal quantityBefore = nvl(tank.getCurrentKg());
        TankWeights weights = resolveTankWeights(request, tank);
        BigDecimal currentKg = weights.currentNetKg();
        BigDecimal capacityKg = weights.capacityKg();
        BigDecimal reorderLevelKg = nvl(request.getReorderLevelKg());
        validateTankLevels(capacityKg, currentKg, reorderLevelKg);
        boolean physicalWeightsChanged = nvl(tank.getTareWeightKg()).compareTo(weights.tareKg()) != 0
                || nvl(tank.getFullGrossWeightKg()).compareTo(weights.fullGrossKg()) != 0
                || quantityBefore.compareTo(currentKg) != 0;
        if (physicalWeightsChanged && shiftTanks.existsByTenantIdAndBranchIdAndTankIdAndStatus(
                tenantId, branchId, tankId, GasShiftTank.Status.IN_USE)) {
            throw new IllegalStateException("Close the open gas shift before changing this tank's physical weights.");
        }
        tank.setName(requiredText(request.getName(), "Tank name is required."));
        tank.setProductName(requiredText(request.getProductName(), "Product name is required."));
        tank.setTareWeightKg(weights.tareKg());
        tank.setCapacityKg(capacityKg);
        tank.setFullGrossWeightKg(weights.fullGrossKg());
        tank.setCurrentKg(currentKg);
        tank.setReorderLevelKg(reorderLevelKg);
        tank.setStatus(request.getStatus() == null ? GasTankStatus.ACTIVE : request.getStatus());
        GasTank saved = tanks.save(tank);
        if (quantityBefore.compareTo(currentKg) != 0) {
            recordAdjustment(saved, quantityBefore, currentKg, "TANK_CONFIGURATION_UPDATE",
                    "Stock changed through the existing tank update API.", null);
        }
        return saved;
    }

    @Transactional
    public GasPrice setPrice(Long tenantId, GasPriceRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        CurrencyCode currency = request.getCurrency() == null ? CurrencyCode.USD : request.getCurrency();
        for (GasPrice existing : prices.findByTenantIdAndBranchIdAndCurrencyAndIsActiveTrue(tenantId, branchId, currency)) {
            existing.setIsActive(false);
            prices.save(existing);
        }
        return prices.save(GasPrice.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .currency(currency)
                .pricePerKg(request.getPricePerKg())
                .isActive(true)
                .build());
    }

    public GasShift currentShift(Long tenantId, Long branchId, Long cashierId) {
        requireGasBranch(tenantId, branchId);
        return shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN)
                .orElse(null);
    }

    @Transactional
    public GasShift openShift(Long tenantId, Long cashierId, OpenGasShiftRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN)
                .ifPresent(existing -> {
                    throw new IllegalStateException("This cashier already has an open gas shift.");
                });
        List<GasTank> availableTanks = tanks.findByTenantIdAndBranchIdAndStatusOrderByNameAsc(
                tenantId, branchId, GasTankStatus.ACTIVE);
        Set<Long> requestedTankIds = request.getTankIds() == null
                ? Set.of()
                : request.getTankIds().stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        List<GasTank> selectedTanks = availableTanks.stream()
                .filter(tank -> requestedTankIds.isEmpty() || requestedTankIds.contains(tank.getId()))
                .filter(tank -> nvl(tank.getCurrentKg()).compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (selectedTanks.isEmpty()) {
            throw new IllegalStateException("Select at least one active tank with LPG stock.");
        }
        if (!requestedTankIds.isEmpty() && selectedTanks.size() != requestedTankIds.size()) {
            throw new IllegalArgumentException("One or more selected tanks are unavailable or have no LPG stock.");
        }
        List<GasTank> lockedSelectedTanks = new ArrayList<>();
        for (GasTank selectedTank : selectedTanks.stream()
                .sorted(Comparator.comparing(GasTank::getId)).toList()) {
            GasTank tank = tanks.lockTank(tenantId, branchId, selectedTank.getId())
                    .orElseThrow(() -> new IllegalArgumentException("A selected tank is no longer available."));
            if (!GasTankStatus.ACTIVE.equals(tank.getStatus())
                    || nvl(tank.getCurrentKg()).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(tank.getName() + " is unavailable or has no LPG stock.");
            }
            if (shiftTanks.existsByTenantIdAndBranchIdAndTankIdAndStatus(
                    tenantId, branchId, tank.getId(), GasShiftTank.Status.IN_USE)) {
                throw new IllegalStateException(tank.getName() + " is already assigned to another open shift.");
            }
            lockedSelectedTanks.add(tank);
        }
        GasShift shift = shifts.save(GasShift.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .cashierId(cashierId)
                .shiftNumber("GAS-" + branchId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis())
                .openedAt(LocalDateTime.now())
                .status(GasShiftStatus.OPEN)
                .build());
        for (GasTank tank : lockedSelectedTanks) {
            BigDecimal startingNet = nvl(tank.getCurrentKg()).setScale(3, RoundingMode.HALF_UP);
            shiftTanks.save(GasShiftTank.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .gasShiftId(shift.getId())
                    .tankId(tank.getId())
                    .startingGrossKg(tank.getCurrentGrossWeightKg().setScale(3, RoundingMode.HALF_UP))
                    .startingNetKg(startingNet)
                    .expectedClosingNetKg(startingNet)
                    .status(GasShiftTank.Status.IN_USE)
                    .build());
        }
        return shift;
    }

    @Transactional
    public GasSale completeSale(Long tenantId, Long cashierId, GasSaleRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        if (request.getOfflineReceiptNumber() != null && !request.getOfflineReceiptNumber().isBlank()) {
            var existing = sales.findByTenantIdAndOfflineReceiptNumber(tenantId, request.getOfflineReceiptNumber());
            if (existing.isPresent()) return enrichSale(existing.get());
        }
        GasShift shift = shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Open a gas shift before selling gas."));
        BigDecimal quantity = nvl(request.getQuantityKg());
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gas quantity must be greater than zero.");
        }
        List<GasSaleTankRequest> requestedTanks = saleTankRequests(request);
        List<Long> tankIds = requestedTanks.stream().map(GasSaleTankRequest::getTankId)
                .distinct().sorted().toList();
        if (tankIds.isEmpty()) throw new IllegalArgumentException("Select at least one tank for this sale.");
        List<GasTank> lockedTanks = new ArrayList<>();
        for (Long tankId : tankIds) {
            GasTank tank = tanks.lockTank(tenantId, branchId, tankId)
                    .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
            if (!GasTankStatus.ACTIVE.equals(tank.getStatus())) {
                throw new IllegalStateException(tank.getName() + " is not active.");
            }
            lockedTanks.add(tank);
        }
        BigDecimal available = lockedTanks.stream().map(GasTank::getCurrentKg)
                .map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new IllegalStateException("Selected tanks have only " + available.setScale(3, RoundingMode.HALF_UP) + " kg available.");
        }
        Map<Long, BigDecimal> distribution = distributeSale(quantity, requestedTanks, lockedTanks);
        CurrencyCode currency = request.getCurrency() == null ? CurrencyCode.USD : request.getCurrency();
        BigDecimal unitPrice = prices.findFirstByTenantIdAndBranchIdAndCurrencyAndIsActiveTrueOrderByCreatedAtDesc(tenantId, branchId, currency)
                .map(GasPrice::getPricePerKg)
                .orElseThrow(() -> new IllegalStateException("Set an active gas price for " + currency + " first."));
        BigDecimal total = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        GasSale sale = sales.save(GasSale.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .gasShiftId(shift.getId())
                .tankId(lockedTanks.get(0).getId())
                .cashierId(cashierId)
                .receiptNumber("GAS" + branchId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis())
                .customerName(blank(request.getCustomerName()))
                .customerPhone(blank(request.getCustomerPhone()))
                .quantityKg(quantity)
                .unitPrice(unitPrice)
                .total(total)
                .amountReceived(request.getAmountReceived())
                .currency(currency)
                .paymentMethod(normalizePaymentMethod(request.getPaymentMethod()))
                .paymentReference(blank(request.getPaymentReference()))
                .offlineReceiptNumber(blank(request.getOfflineReceiptNumber()))
                .offlineCreatedAt(request.getOfflineCreatedAt())
                .build());
        List<GasSaleTankAllocation> savedAllocations = new ArrayList<>();
        for (GasTank tank : lockedTanks) {
            BigDecimal allocated = distribution.getOrDefault(tank.getId(), BigDecimal.ZERO);
            if (allocated.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal before = nvl(tank.getCurrentKg()).setScale(3, RoundingMode.HALF_UP);
            BigDecimal after = before.subtract(allocated).setScale(3, RoundingMode.HALF_UP);
            tank.setCurrentKg(after);
            tanks.save(tank);
            GasShiftTank selected = shiftTanks.lockSelectedTank(
                    tenantId, branchId, shift.getId(), tank.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            tank.getName() + " is not assigned to this cashier's open shift."));
            selected.setExpectedClosingNetKg(nvl(selected.getExpectedClosingNetKg()).subtract(allocated)
                    .setScale(3, RoundingMode.HALF_UP));
            shiftTanks.save(selected);
            savedAllocations.add(saleTankAllocations.save(GasSaleTankAllocation.builder()
                    .tenantId(tenantId).branchId(branchId).gasSaleId(sale.getId())
                    .gasShiftId(shift.getId()).gasShiftTankId(selected.getId())
                    .tankId(tank.getId()).quantityKg(allocated)
                    .stockBeforeKg(before).stockAfterKg(after).build()));
        }
        List<GasSalePayment> savedPayments = savePayments(tenantId, branchId, sale, request, total);
        BigDecimal cashPaid = savedPayments.stream()
                .filter(payment -> "CASH".equals(payment.getPaymentMethod()))
                .map(GasSalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountReceived = request.getAmountReceived() == null ? cashPaid : request.getAmountReceived();
        if (amountReceived.compareTo(cashPaid) < 0) {
            throw new IllegalArgumentException("Cash received cannot be below the cash payment amount.");
        }
        BigDecimal changeDue = amountReceived.subtract(cashPaid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        sale.setAmountReceived(amountReceived.setScale(2, RoundingMode.HALF_UP));
        sale.setChangeDue(changeDue);
        sale.setChangeHeld(Boolean.TRUE.equals(request.getHoldChange()) && changeDue.compareTo(BigDecimal.ZERO) > 0);
        HeldChange gasHeldChange = null;
        if (Boolean.TRUE.equals(sale.getChangeHeld())) {
            gasHeldChange = createGasHeldChange(tenantId, branchId, cashierId, shift, sale, request, changeDue);
        }
        sales.save(sale);
        shift.setTotalKgSold(nvl(shift.getTotalKgSold()).add(quantity));
        if (CurrencyCode.ZWG.equals(currency)) {
            shift.setTotalZwg(nvl(shift.getTotalZwg()).add(total));
        } else {
            shift.setTotalUsd(nvl(shift.getTotalUsd()).add(total));
        }
        shift.setTotalTransactions((shift.getTotalTransactions() == null ? 0 : shift.getTotalTransactions()) + 1);
        shifts.save(shift);
        sale.setTankAllocations(savedAllocations);
        sale.setPayments(savedPayments);
        sale.setHeldChange(gasHeldChange);
        return sale;
    }

    @Transactional
    public GasRestock restock(Long tenantId, Long userId, GasRestockRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasTank tank = tanks.lockTank(tenantId, branchId, request.getTankId())
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        BigDecimal quantity = nvl(request.getQuantityKg()).setScale(3, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("Delivered LPG quantity must be greater than zero.");
        }
        BigDecimal unitCost = nvl(request.getUnitCost()).setScale(4, RoundingMode.HALF_UP);
        if (unitCost.signum() < 0) {
            throw new IllegalArgumentException("Unit cost cannot be negative.");
        }
        BigDecimal capacity = nvl(tank.getCapacityKg());
        if (capacity.compareTo(BigDecimal.ZERO) > 0 && nvl(tank.getCurrentKg()).add(quantity).compareTo(capacity) > 0) {
            throw new IllegalStateException("Restock would exceed " + tank.getName() + " capacity.");
        }
        tank.setCurrentKg(nvl(tank.getCurrentKg()).add(quantity));
        tanks.save(tank);
        BigDecimal totalCost = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
        return restocks.save(GasRestock.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .tankId(tank.getId())
                .quantityKg(quantity)
                .currency(request.getCurrency() == null ? CurrencyCode.USD : request.getCurrency())
                .unitCost(unitCost)
                .totalCost(totalCost)
                .supplierName(blank(request.getSupplierName()))
                .supplierInvoice(blank(request.getSupplierInvoice()))
                .notes(blank(request.getNotes()))
                .createdBy(userId)
                .build());
    }

    @Transactional
    public GasStockAdjustment reconcileStock(Long tenantId, Long userId,
                                             GasStockReconciliationRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasTank tank = tanks.lockTank(tenantId, branchId, request.getTankId())
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        BigDecimal quantityBefore = nvl(tank.getCurrentKg()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal countedKg;
        String measurementNote = null;
        if (request.getCountedGrossKg() != null) {
            BigDecimal countedGross = scaleWeight(request.getCountedGrossKg());
            BigDecimal tare = scaleWeight(tank.getTareWeightKg());
            BigDecimal fullGross = effectiveFullGross(tank);
            validateCurrentGross(tank.getName(), tare, fullGross, countedGross);
            countedKg = countedGross.subtract(tare).setScale(3, RoundingMode.HALF_UP);
            measurementNote = "Measured gross " + countedGross + " kg; tare " + tare + " kg.";
        } else if (request.getCountedKg() != null) {
            countedKg = scaleWeight(request.getCountedKg());
        } else {
            throw new IllegalArgumentException("Enter the measured gross tank weight.");
        }
        if (countedKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Counted gas stock cannot be negative.");
        }
        if (nvl(tank.getCapacityKg()).compareTo(BigDecimal.ZERO) > 0
                && countedKg.compareTo(tank.getCapacityKg()) > 0) {
            throw new IllegalStateException("Counted stock cannot exceed " + tank.getName() + " capacity.");
        }
        if (quantityBefore.compareTo(countedKg) == 0) {
            throw new IllegalArgumentException("The counted stock matches the system quantity; no adjustment is required.");
        }
        tank.setCurrentKg(countedKg);
        tanks.save(tank);
        String notes = blank(request.getNotes());
        if (measurementNote != null) {
            notes = notes == null ? measurementNote : measurementNote + " " + notes;
        }
        return recordAdjustment(tank, quantityBefore, countedKg,
                requiredText(request.getReason(), "Reconciliation reason is required."),
                notes, userId);
    }

    @Transactional
    public GasExpense recordExpense(Long tenantId, Long userId, GasExpenseRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        BigDecimal amount = nvl(request.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        return expenses.save(GasExpense.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .category(requiredText(request.getCategory(), "Expense category is required."))
                .description(requiredText(request.getDescription(), "Expense description is required."))
                .amount(amount)
                .currency(request.getCurrency() == null ? CurrencyCode.USD : request.getCurrency())
                .paymentMethod(normalizePaymentMethod(request.getPaymentMethod()))
                .reference(blank(request.getReference()))
                .createdBy(userId)
                .build());
    }

    @Transactional
    public GasShift closeShift(Long tenantId, Long cashierId, CloseGasShiftRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasShift shift = request.getShiftId() == null
                ? shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN)
                    .orElseThrow(() -> new IllegalStateException("No open gas shift found."))
                : shifts.findById(request.getShiftId())
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getBranchId().equals(branchId) && s.getCashierId().equals(cashierId))
                    .orElseThrow(() -> new IllegalArgumentException("Gas shift not found."));
        if (!GasShiftStatus.OPEN.equals(shift.getStatus())) {
            throw new IllegalStateException("Gas shift is already closed.");
        }
        List<GasShiftTank> selectedTanks = shiftTanks
                .findByTenantIdAndBranchIdAndGasShiftIdOrderByTankId(tenantId, branchId, shift.getId());
        if (!selectedTanks.isEmpty()) {
            Map<Long, BigDecimal> closingWeights = request.getClosingWeights().stream()
                    .collect(Collectors.toMap(
                            GasTankClosingWeightRequest::getTankId,
                            GasTankClosingWeightRequest::getClosingGrossKg,
                            (first, ignored) -> first,
                            LinkedHashMap::new));
            if (closingWeights.size() != selectedTanks.size()
                    || selectedTanks.stream().anyMatch(selected -> !closingWeights.containsKey(selected.getTankId()))) {
                throw new IllegalArgumentException("Enter the closing gross weight for every tank used in this shift.");
            }
            BigDecimal totalVariance = BigDecimal.ZERO;
            LocalDateTime closedAt = LocalDateTime.now();
            for (GasShiftTank selected : selectedTanks) {
                GasTank tank = tanks.lockTank(tenantId, branchId, selected.getTankId())
                        .orElseThrow(() -> new IllegalArgumentException("A selected gas tank no longer exists."));
                BigDecimal gross = nvl(closingWeights.get(tank.getId())).setScale(3, RoundingMode.HALF_UP);
                BigDecimal tare = nvl(tank.getTareWeightKg()).setScale(3, RoundingMode.HALF_UP);
                validateCurrentGross(tank.getName(), tare, effectiveFullGross(tank), gross);
                BigDecimal closingNet = gross.subtract(tare).setScale(3, RoundingMode.HALF_UP);
                if (nvl(tank.getCapacityKg()).compareTo(BigDecimal.ZERO) > 0
                        && closingNet.compareTo(tank.getCapacityKg()) > 0) {
                    throw new IllegalArgumentException(tank.getName() + " measured LPG exceeds configured capacity.");
                }
                BigDecimal expected = nvl(selected.getExpectedClosingNetKg()).setScale(3, RoundingMode.HALF_UP);
                BigDecimal variance = closingNet.subtract(expected).setScale(3, RoundingMode.HALF_UP);
                BigDecimal before = nvl(tank.getCurrentKg()).setScale(3, RoundingMode.HALF_UP);
                tank.setCurrentKg(closingNet);
                tanks.save(tank);
                if (before.compareTo(closingNet) != 0) {
                    recordAdjustment(tank, before, closingNet, "SHIFT_WEIGHT_RECONCILIATION",
                            "Shift " + shift.getShiftNumber() + " closing gross " + gross + " kg; tare " + tare + " kg.",
                            cashierId);
                }
                selected.setClosingGrossKg(gross);
                selected.setClosingNetKg(closingNet);
                selected.setVarianceKg(variance);
                selected.setStatus(GasShiftTank.Status.CLOSED);
                selected.setClosedAt(closedAt);
                shiftTanks.save(selected);
                totalVariance = totalVariance.add(variance);
            }
            shift.setClosingVarianceKg(totalVariance.setScale(3, RoundingMode.HALF_UP));
        }
        shift.setStatus(GasShiftStatus.CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        return shifts.save(shift);
    }

    public List<GasSale> shiftSales(Long tenantId, Long branchId, Long cashierId) {
        GasShift shift = currentShift(tenantId, branchId, cashierId);
        if (shift == null) return List.of();
        return sales.findByTenantIdAndBranchIdAndGasShiftIdOrderByCreatedAtDesc(tenantId, branchId, shift.getId())
                .stream().map(this::enrichSale).toList();
    }

    public List<GasShiftTank> currentShiftTanks(Long tenantId, Long branchId, Long cashierId) {
        GasShift shift = currentShift(tenantId, branchId, cashierId);
        if (shift == null) return List.of();
        return shiftTanks.findByTenantIdAndBranchIdAndGasShiftIdOrderByTankId(tenantId, branchId, shift.getId());
    }

    public List<HeldChange> openGasHeldChange(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return heldChange.findTop100ByTenantIdAndBranchIdAndGasShiftIdIsNotNullAndStatusOrderByCreatedAtDesc(
                tenantId, branchId, HeldChange.Status.OPEN);
    }

    public Page<HeldChange> gasHeldChangePage(Long tenantId, Long branchId,
                                              HeldChange.Status status, String search,
                                              LocalDateTime from, LocalDateTime to,
                                              Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return heldChange.searchGas(
                tenantId, branchId, status, blank(search), from, to, pageable);
    }

    public BigDecimal gasHeldChangeTotal(Long tenantId, Long branchId,
                                         HeldChange.Status status, String search,
                                         LocalDateTime from, LocalDateTime to,
                                         CurrencyCode currency) {
        requireGasBranch(tenantId, branchId);
        return nvl(heldChange.sumGas(
                tenantId, branchId, status, blank(search), from, to, currency));
    }

    @Transactional
    public HeldChange collectGasHeldChange(Long tenantId, Long branchId, Long cashierId, Long changeId) {
        requireGasBranch(tenantId, branchId);
        GasShift shift = currentShift(tenantId, branchId, cashierId);
        if (shift == null) throw new IllegalStateException("Open a gas shift before paying held change.");
        HeldChange record = heldChange.lockById(tenantId, changeId)
                .orElseThrow(() -> new IllegalArgumentException("Held change record not found."));
        if (!branchId.equals(record.getBranchId()) || record.getGasShiftId() == null) {
            throw new IllegalArgumentException("Held change does not belong to this gas branch.");
        }
        if (HeldChange.Status.COLLECTED.equals(record.getStatus())) return record;
        if (!HeldChange.Status.OPEN.equals(record.getStatus())) {
            throw new IllegalStateException("Held change is not available.");
        }
        record.setStatus(HeldChange.Status.COLLECTED);
        record.setCollectedBy(cashierId);
        record.setCollectedAt(LocalDateTime.now());
        return heldChange.save(record);
    }

    @Transactional
    public HeldChange cancelGasHeldChange(Long tenantId, Long branchId, Long userId, Long changeId) {
        requireGasBranch(tenantId, branchId);
        HeldChange record = heldChange.lockById(tenantId, changeId)
                .orElseThrow(() -> new IllegalArgumentException("Held change record not found."));
        if (!branchId.equals(record.getBranchId()) || record.getGasShiftId() == null) {
            throw new IllegalArgumentException("Held change does not belong to this gas branch.");
        }
        if (!HeldChange.Status.OPEN.equals(record.getStatus())) {
            throw new IllegalStateException("Only open held change can be cancelled.");
        }
        record.setStatus(HeldChange.Status.CANCELLED);
        record.setCancelledBy(userId);
        record.setCancelledAt(LocalDateTime.now());
        return heldChange.save(record);
    }

    public List<GasSale> sales(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return sales.findTop100ByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public Page<GasSale> salesPage(Long tenantId, Long branchId,
                                   LocalDateTime from, LocalDateTime to,
                                   String query, String paymentMethod, Long cashierId,
                                   Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return sales.search(
                tenantId, branchId, from, to, blank(query),
                blank(paymentMethod) == null ? null : normalizePaymentMethod(paymentMethod),
                cashierId, pageable);
    }

    public SalesAnalytics salesAnalytics(Long tenantId, Long branchId,
                                         LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        List<Object[]> summary = sales.dailySummary(tenantId, branchId, from, to);
        BigDecimal soldKg = summary.stream()
                .map(row -> (BigDecimal) row[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenueUsd = dailySummaryValue(summary, CurrencyCode.USD, 2);
        BigDecimal revenueZwg = dailySummaryValue(summary, CurrencyCode.ZWG, 2);
        long transactions = summary.stream()
                .mapToLong(row -> ((Number) row[3]).longValue())
                .sum();
        long usdTransactions = summary.stream()
                .filter(row -> CurrencyCode.USD.equals(row[0]))
                .mapToLong(row -> ((Number) row[3]).longValue())
                .findFirst()
                .orElse(0L);
        BigDecimal averageUsd = usdTransactions == 0
                ? BigDecimal.ZERO
                : revenueUsd.divide(BigDecimal.valueOf(usdTransactions), 2, RoundingMode.HALF_UP);
        List<PaymentMix> paymentMix = salePayments.paymentMix(tenantId, branchId, from, to).stream()
                .map(row -> new PaymentMix(row[0].toString(), (CurrencyCode) row[1], (BigDecimal) row[2]))
                .toList();
        return new SalesAnalytics(
                soldKg.setScale(3, RoundingMode.HALF_UP),
                revenueUsd.setScale(2, RoundingMode.HALF_UP),
                revenueZwg.setScale(2, RoundingMode.HALF_UP),
                transactions, averageUsd, paymentMix);
    }

    public List<HourlyRevenue> salesHourlyRevenue(Long tenantId, Long branchId,
                                                   LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        return hourlyRevenueRows(sales.hourlyRevenue(tenantId, branchId, from, to));
    }

    public List<DailyRevenue> salesDailyRevenue(Long tenantId, Long branchId,
                                                LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        Map<LocalDate, BigDecimal[]> daily = new LinkedHashMap<>();
        for (LocalDate date = from.toLocalDate(); date.isBefore(to.toLocalDate()); date = date.plusDays(1)) {
            daily.put(date, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (Object[] row : sales.dailyRevenue(tenantId, branchId, from, to)) {
            LocalDate date = toLocalDate(row[0]);
            BigDecimal[] values = daily.computeIfAbsent(
                    date, ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            values[CurrencyCode.USD.equals(row[1]) ? 0 : 1] = (BigDecimal) row[2];
        }
        return daily.entrySet().stream()
                .map(entry -> new DailyRevenue(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    public List<Long> salesCashierIds(Long tenantId, Long branchId,
                                      LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        return sales.distinctCashierIds(tenantId, branchId, from, to);
    }

    public Page<GasShift> shifts(Long tenantId, Long branchId, Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return shifts.findByTenantIdAndBranchIdOrderByOpenedAtDesc(tenantId, branchId, pageable);
    }

    public Page<GasShift> shifts(Long tenantId, Long branchId, String query,
                                 GasShiftStatus status, Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return shifts.search(tenantId, branchId, normalizedQuery, status, pageable);
    }

    public GasDashboard dashboard(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<GasTank> branchTanks = tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId);
        List<Object[]> dailySales = sales.dailySummary(tenantId, branchId, start, end);
        List<GasRestock> todayUsdRestocks = restocks.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.USD, start, end);
        List<GasRestock> todayZwgRestocks = restocks.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.ZWG, start, end);
        List<GasExpense> todayUsdExpenses = expenses.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.USD, start, end);
        List<GasExpense> todayZwgExpenses = expenses.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.ZWG, start, end);
        BigDecimal soldKg = dailySales.stream()
                .map(row -> (BigDecimal) row[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenueUsd = dailySummaryValue(dailySales, CurrencyCode.USD, 2);
        BigDecimal revenueZwg = dailySummaryValue(dailySales, CurrencyCode.ZWG, 2);
        BigDecimal restockCostUsd = sum(todayUsdRestocks, GasRestock::getTotalCost);
        BigDecimal restockCostZwg = sum(todayZwgRestocks, GasRestock::getTotalCost);
        BigDecimal expensesUsd = sum(todayUsdExpenses, GasExpense::getAmount);
        BigDecimal expensesZwg = sum(todayZwgExpenses, GasExpense::getAmount);
        BigDecimal currentStockKg = sum(branchTanks, GasTank::getCurrentKg).setScale(3, RoundingMode.HALF_UP);
        BigDecimal totalCapacityKg = sum(branchTanks, GasTank::getNetCapacityKg).setScale(3, RoundingMode.HALF_UP);
        List<GasPrice> activePrices = prices.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId);
        BigDecimal sellingPriceUsd = activePrices.stream()
                .filter(price -> CurrencyCode.USD.equals(price.getCurrency()))
                .map(GasPrice::getPricePerKg).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal sellingPriceZwg = activePrices.stream()
                .filter(price -> CurrencyCode.ZWG.equals(price.getCurrency()))
                .map(GasPrice::getPricePerKg).findFirst().orElse(BigDecimal.ZERO);
        List<GasShiftTank> activeShiftTanks = shiftTanks
                .findByTenantIdAndBranchIdAndStatusOrderBySelectedAtAsc(
                        tenantId, branchId, GasShiftTank.Status.IN_USE);
        Set<Long> inUseTankIds = activeShiftTanks.stream().map(GasShiftTank::getTankId).collect(Collectors.toSet());
        List<PaymentMix> paymentMix = salePayments.paymentMix(tenantId, branchId, start, end).stream()
                .map(row -> new PaymentMix(row[0].toString(), (CurrencyCode) row[1], (BigDecimal) row[2]))
                .toList();
        List<HourlyRevenue> hourlyRevenue = hourlyRevenueRows(
                sales.hourlyRevenue(tenantId, branchId, start, end));
        long transactionCount = dailySales.stream()
                .mapToLong(row -> ((Number) row[3]).longValue()).sum();
        List<GasTank> reorderTanks = branchTanks.stream()
                .filter(tank -> nvl(tank.getReorderLevelKg()).compareTo(BigDecimal.ZERO) > 0)
                .filter(tank -> nvl(tank.getCurrentKg()).compareTo(nvl(tank.getReorderLevelKg())) <= 0)
                .toList();
        return new GasDashboard(soldKg, revenueUsd, revenueZwg, restockCostUsd, restockCostZwg,
                expensesUsd, expensesZwg, revenueUsd.subtract(restockCostUsd).subtract(expensesUsd),
                revenueZwg.subtract(restockCostZwg).subtract(expensesZwg), reorderTanks, ZIMBABWE_LPG_WEIGHTS_KG,
                transactionCount, currentStockKg, totalCapacityKg,
                currentStockKg.multiply(sellingPriceUsd).setScale(2, RoundingMode.HALF_UP),
                currentStockKg.multiply(sellingPriceZwg).setScale(2, RoundingMode.HALF_UP),
                sellingPriceUsd, sellingPriceZwg,
                sum(branchTanks, GasTank::getReorderLevelKg).setScale(3, RoundingMode.HALF_UP),
                shifts.countByTenantIdAndBranchIdAndStatus(tenantId, branchId, GasShiftStatus.OPEN),
                inUseTankIds, paymentMix, hourlyRevenue);
    }

    public List<GasRestock> restocks(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return restocks.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public Page<GasRestock> restockPage(Long tenantId, Long branchId,
                                        LocalDateTime from, LocalDateTime to,
                                        String query, Long tankId, CurrencyCode currency,
                                        Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return restocks.search(
                tenantId, branchId, from, to, blank(query), tankId, currency, pageable);
    }

    public RestockSummary restockSummary(Long tenantId, Long branchId,
                                         LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        return restocks.summarize(tenantId, branchId, from, to).stream()
                .findFirst()
                .map(row -> new RestockSummary(
                        (BigDecimal) row[0],
                        ((Number) row[1]).longValue(),
                        (LocalDateTime) row[2]))
                .orElse(new RestockSummary(BigDecimal.ZERO, 0, null));
    }

    public List<GasExpense> expenses(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return expenses.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public Page<GasExpense> expensePage(Long tenantId, Long branchId,
                                        LocalDateTime from, LocalDateTime to,
                                        String query, String category, String paymentMethod,
                                        Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return expenses.search(
                tenantId, branchId, from, to,
                blank(query), blank(category),
                blank(paymentMethod) == null ? null : normalizePaymentMethod(paymentMethod),
                pageable);
    }

    public List<String> expenseCategories(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return expenses.distinctCategories(tenantId, branchId);
    }

    public ExpenseAnalytics expenseAnalytics(Long tenantId, Long branchId,
                                             LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        List<ExpenseCurrencySummary> currencies =
                expenseCurrencySummary(tenantId, branchId, from, to);
        List<ExpenseCategorySummary> categories = expenses
                .summarizeByCategory(tenantId, branchId, from, to).stream()
                .map(row -> new ExpenseCategorySummary(
                        row[0].toString(),
                        (CurrencyCode) row[1],
                        (BigDecimal) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
        List<ExpenseDailySummary> daily = expenses
                .summarizeByDay(tenantId, branchId, from, to).stream()
                .map(row -> new ExpenseDailySummary(
                        toLocalDate(row[0]),
                        (CurrencyCode) row[1],
                        (BigDecimal) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
        return new ExpenseAnalytics(currencies, categories, daily);
    }

    public List<ExpenseCurrencySummary> expenseCurrencySummary(
            Long tenantId, Long branchId, LocalDateTime from, LocalDateTime to) {
        requireGasBranch(tenantId, branchId);
        return expenses.summarizeByCurrency(tenantId, branchId, from, to).stream()
                .map(row -> new ExpenseCurrencySummary(
                        (CurrencyCode) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue(),
                        (LocalDateTime) row[3]))
                .toList();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    public List<GasStockAdjustment> stockAdjustments(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return stockAdjustments.findTop50ByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public Page<GasStockAdjustment> stockAdjustmentPage(Long tenantId, Long branchId, Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return stockAdjustments.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId, pageable);
    }

    @Transactional
    public void seedGasDefaults(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        if (prices.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId).isEmpty()) {
            prices.save(GasPrice.builder().tenantId(tenantId).branchId(branchId).currency(CurrencyCode.USD).pricePerKg(new BigDecimal("2.0000")).isActive(true).build());
            prices.save(GasPrice.builder().tenantId(tenantId).branchId(branchId).currency(CurrencyCode.ZWG).pricePerKg(new BigDecimal("60.0000")).isActive(true).build());
        }
    }

    private Branch requireGasBranch(Long tenantId, Long branchId) {
        return branches.findById(branchId)
                .filter(branch -> branch.getTenantId().equals(tenantId))
                .filter(branch -> Boolean.TRUE.equals(branch.getIsActive()))
                .filter(branch -> BusinessModule.GAS_MODULE.equals(branch.getModuleType()))
                .orElseThrow(() -> new IllegalArgumentException("Select an active gas branch first."));
    }

    private void enforceTankLimit(Long tenantId) {
        int maxTanks = subscriptions.findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .flatMap(subscription -> plans.findById(subscription.getPlanId()))
                .map(SaasPlan::getMaxGasTanks)
                .orElse(0);
        if (maxTanks > 0 && tanks.countByTenantId(tenantId) >= maxTanks) {
            throw new IllegalStateException("Your package allows up to " + maxTanks + " gas tanks.");
        }
    }

    private void validateTankLevels(BigDecimal capacityKg, BigDecimal currentKg, BigDecimal reorderLevelKg) {
        if (capacityKg.compareTo(BigDecimal.ZERO) < 0
                || currentKg.compareTo(BigDecimal.ZERO) < 0
                || reorderLevelKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tank capacity and stock levels cannot be negative.");
        }
        if (capacityKg.compareTo(BigDecimal.ZERO) > 0 && currentKg.compareTo(capacityKg) > 0) {
            throw new IllegalArgumentException("Current stock cannot exceed tank capacity.");
        }
        if (capacityKg.compareTo(BigDecimal.ZERO) > 0 && reorderLevelKg.compareTo(capacityKg) > 0) {
            throw new IllegalArgumentException("Reorder level cannot exceed tank capacity.");
        }
    }

    private TankWeights resolveTankWeights(GasTankRequest request, GasTank existing) {
        BigDecimal tare = request.getTareWeightKg() != null
                ? scaleWeight(request.getTareWeightKg())
                : existing == null ? null : scaleWeight(existing.getTareWeightKg());
        BigDecimal fullGross = request.getFullGrossWeightKg() != null
                ? scaleWeight(request.getFullGrossWeightKg())
                : existing == null ? null : effectiveFullGross(existing);
        if (tare == null || fullGross == null) {
            throw new IllegalArgumentException("Empty/tare weight and full gross weight are required.");
        }
        if (tare.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Empty/tare weight cannot be negative.");
        }
        if (fullGross.compareTo(tare) <= 0) {
            throw new IllegalArgumentException("Full gross weight must be greater than the empty/tare weight.");
        }
        BigDecimal capacity = fullGross.subtract(tare).setScale(3, RoundingMode.HALF_UP);
        if (request.getCapacityKg() != null
                && scaleWeight(request.getCapacityKg()).compareTo(capacity) != 0) {
            throw new IllegalArgumentException("LPG capacity is calculated as full gross weight minus empty/tare weight.");
        }
        BigDecimal currentGross;
        if (request.getCurrentGrossWeightKg() != null) {
            currentGross = scaleWeight(request.getCurrentGrossWeightKg());
        } else if (request.getCurrentKg() != null) {
            currentGross = tare.add(scaleWeight(request.getCurrentKg())).setScale(3, RoundingMode.HALF_UP);
        } else if (existing != null) {
            currentGross = tare.add(nvl(existing.getCurrentKg())).setScale(3, RoundingMode.HALF_UP);
        } else {
            throw new IllegalArgumentException("Current gross tank weight is required.");
        }
        validateCurrentGross(existing == null ? "Tank" : existing.getName(), tare, fullGross, currentGross);
        return new TankWeights(tare, fullGross, capacity,
                currentGross.subtract(tare).setScale(3, RoundingMode.HALF_UP));
    }

    private void validateCurrentGross(String tankName, BigDecimal tare,
                                      BigDecimal fullGross, BigDecimal currentGross) {
        if (currentGross.compareTo(tare) < 0) {
            throw new IllegalArgumentException(tankName + " current gross weight cannot be below its empty/tare weight.");
        }
        if (currentGross.compareTo(fullGross) > 0) {
            throw new IllegalArgumentException(tankName + " current gross weight cannot exceed its full gross weight.");
        }
    }

    private BigDecimal effectiveFullGross(GasTank tank) {
        if (tank.getFullGrossWeightKg() != null) {
            return scaleWeight(tank.getFullGrossWeightKg());
        }
        return scaleWeight(nvl(tank.getTareWeightKg()).add(nvl(tank.getCapacityKg())));
    }

    private BigDecimal scaleWeight(BigDecimal value) {
        return nvl(value).setScale(3, RoundingMode.HALF_UP);
    }

    private GasStockAdjustment recordAdjustment(GasTank tank, BigDecimal quantityBefore,
                                                BigDecimal countedKg, String reason,
                                                String notes, Long userId) {
        return stockAdjustments.save(GasStockAdjustment.builder()
                .tenantId(tank.getTenantId())
                .branchId(tank.getBranchId())
                .tankId(tank.getId())
                .quantityBeforeKg(quantityBefore.setScale(3, RoundingMode.HALF_UP))
                .countedKg(countedKg.setScale(3, RoundingMode.HALF_UP))
                .varianceKg(countedKg.subtract(quantityBefore).setScale(3, RoundingMode.HALF_UP))
                .reason(reason.trim().toUpperCase().replace(' ', '_'))
                .notes(notes)
                .createdBy(userId)
                .build());
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePaymentMethod(String value) {
        String clean = blank(value);
        String method = clean == null ? "CASH" : clean.toUpperCase(Locale.ROOT).replace(' ', '_');
        Set<String> supported = Set.of("CASH", "ECOCASH", "ONEMONEY", "INNBUCKS", "CARD",
                "SWIPE", "ZIPIT", "BANK_TRANSFER", "SPLIT_PAYMENT");
        if (!supported.contains(method)) {
            throw new IllegalArgumentException("Unsupported gas payment method: " + method + ".");
        }
        return method;
    }

    private <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> mapper) {
        return rows.stream().map(mapper).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal dailySummaryValue(List<Object[]> rows, CurrencyCode currency, int index) {
        if (rows == null) return BigDecimal.ZERO;
        return rows.stream()
                .filter(row -> currency.equals(row[0]))
                .map(row -> (BigDecimal) row[index])
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private List<GasSaleTankRequest> saleTankRequests(GasSaleRequest request) {
        if (request.getTanks() != null && !request.getTanks().isEmpty()) {
            Map<Long, GasSaleTankRequest> unique = new LinkedHashMap<>();
            request.getTanks().forEach(item -> {
                if (item != null && item.getTankId() != null) unique.putIfAbsent(item.getTankId(), item);
            });
            return new ArrayList<>(unique.values());
        }
        if (request.getTankId() == null) return List.of();
        GasSaleTankRequest legacy = new GasSaleTankRequest();
        legacy.setTankId(request.getTankId());
        legacy.setQuantityKg(request.getQuantityKg());
        return List.of(legacy);
    }

    private Map<Long, BigDecimal> distributeSale(BigDecimal totalQuantity,
                                                 List<GasSaleTankRequest> requested,
                                                 List<GasTank> lockedTanks) {
        boolean explicit = requested.stream().allMatch(item -> item.getQuantityKg() != null);
        Map<Long, BigDecimal> distribution = new LinkedHashMap<>();
        if (explicit) {
            requested.forEach(item -> distribution.put(
                    item.getTankId(), nvl(item.getQuantityKg()).setScale(3, RoundingMode.HALF_UP)));
            BigDecimal allocated = distribution.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocated.compareTo(totalQuantity.setScale(3, RoundingMode.HALF_UP)) != 0) {
                throw new IllegalArgumentException("Tank allocations must equal the total gas quantity.");
            }
        } else {
            BigDecimal remaining = totalQuantity.setScale(3, RoundingMode.HALF_UP);
            int tanksRemaining = lockedTanks.size();
            for (GasTank tank : lockedTanks) {
                BigDecimal fairShare = tanksRemaining == 1
                        ? remaining
                        : remaining.divide(BigDecimal.valueOf(tanksRemaining), 3, RoundingMode.DOWN);
                BigDecimal allocation = fairShare.min(nvl(tank.getCurrentKg())).setScale(3, RoundingMode.HALF_UP);
                distribution.put(tank.getId(), allocation);
                remaining = remaining.subtract(allocation);
                tanksRemaining--;
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                for (GasTank tank : lockedTanks) {
                    BigDecimal already = distribution.getOrDefault(tank.getId(), BigDecimal.ZERO);
                    BigDecimal spare = nvl(tank.getCurrentKg()).subtract(already);
                    BigDecimal extra = spare.min(remaining);
                    if (extra.compareTo(BigDecimal.ZERO) > 0) {
                        distribution.put(tank.getId(), already.add(extra));
                        remaining = remaining.subtract(extra);
                    }
                    if (remaining.compareTo(BigDecimal.ZERO) == 0) break;
                }
            }
            if (remaining.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalStateException("Selected tanks cannot supply the requested gas quantity.");
            }
        }
        for (GasTank tank : lockedTanks) {
            BigDecimal allocated = distribution.getOrDefault(tank.getId(), BigDecimal.ZERO);
            if (allocated.compareTo(nvl(tank.getCurrentKg())) > 0) {
                throw new IllegalStateException("Insufficient gas stock in " + tank.getName() + ".");
            }
        }
        return distribution;
    }

    private List<GasSalePayment> savePayments(Long tenantId, Long branchId, GasSale sale,
                                              GasSaleRequest request, BigDecimal total) {
        List<GasSalePaymentRequest> requested = request.getPayments() == null
                ? List.of() : request.getPayments();
        if (requested.isEmpty()) {
            GasSalePaymentRequest legacy = new GasSalePaymentRequest();
            legacy.setPaymentMethod(request.getPaymentMethod());
            legacy.setAmount(total);
            legacy.setReference(request.getPaymentReference());
            requested = List.of(legacy);
        }
        List<GasSalePayment> saved = new ArrayList<>();
        BigDecimal paymentTotal = BigDecimal.ZERO;
        for (GasSalePaymentRequest item : requested) {
            BigDecimal amount = nvl(item.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Every payment amount must be greater than zero.");
            }
            String method = normalizePaymentMethod(item.getPaymentMethod());
            if (!"CASH".equals(method) && blank(item.getReference()) == null) {
                throw new IllegalArgumentException("A payment reference is required for " + method.replace('_', ' ') + ".");
            }
            saved.add(salePayments.save(GasSalePayment.builder()
                    .tenantId(tenantId).branchId(branchId).gasSaleId(sale.getId())
                    .paymentMethod(method).amount(amount).reference(blank(item.getReference())).build()));
            paymentTotal = paymentTotal.add(amount);
        }
        if (paymentTotal.compareTo(total) != 0) {
            throw new IllegalArgumentException("Payment total must equal " + total + " " + sale.getCurrency() + ".");
        }
        return saved;
    }

    private HeldChange createGasHeldChange(Long tenantId, Long branchId, Long cashierId,
                                           GasShift shift, GasSale sale, GasSaleRequest request,
                                           BigDecimal changeDue) {
        String offlineReference = blank(request.getHeldChangeOfflineReference());
        if (offlineReference != null) {
            Optional<HeldChange> existing = heldChange.findByTenantIdAndOfflineReference(tenantId, offlineReference);
            if (existing.isPresent()) return existing.get();
        }
        return heldChange.save(HeldChange.builder()
                .tenantId(tenantId).branchId(branchId).gasSaleId(sale.getId()).gasShiftId(shift.getId())
                .referenceNumber("GCH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(Locale.ROOT))
                .customerName(requiredText(request.getHeldChangeName(), "Customer name is required to hold change."))
                .phone(requiredText(request.getHeldChangePhone(), "Customer phone is required to hold change."))
                .currency(sale.getCurrency()).amount(changeDue).status(HeldChange.Status.OPEN)
                .offlineReference(offlineReference).createdBy(cashierId)
                .notes("Gas change held from " + sale.getReceiptNumber()).build());
    }

    private GasSale enrichSale(GasSale sale) {
        sale.setTankAllocations(saleTankAllocations.findByGasSaleIdOrderByTankId(sale.getId()));
        sale.setPayments(salePayments.findByGasSaleIdOrderById(sale.getId()));
        return sale;
    }

    private List<HourlyRevenue> hourlyRevenueRows(List<Object[]> rows) {
        Map<Integer, BigDecimal[]> hourly = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) hourly.put(hour, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        if (rows == null) {
            return hourly.entrySet().stream()
                    .map(entry -> new HourlyRevenue(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                    .toList();
        }
        for (Object[] row : rows) {
            int hour = ((Number) row[0]).intValue();
            CurrencyCode currency = (CurrencyCode) row[1];
            hourly.get(hour)[CurrencyCode.USD.equals(currency) ? 0 : 1] = (BigDecimal) row[2];
        }
        return hourly.entrySet().stream()
                .map(entry -> new HourlyRevenue(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    public record GasDashboard(
            BigDecimal soldKgToday,
            BigDecimal revenueUsdToday,
            BigDecimal revenueZwgToday,
            BigDecimal restockCostUsdToday,
            BigDecimal restockCostZwgToday,
            BigDecimal expensesUsdToday,
            BigDecimal expensesZwgToday,
            BigDecimal marginUsdToday,
            BigDecimal marginZwgToday,
            List<GasTank> reorderTanks,
            List<BigDecimal> lpgWeightPresetsKg,
            long transactionCountToday,
            BigDecimal currentStockKg,
            BigDecimal totalCapacityKg,
            BigDecimal stockValueUsd,
            BigDecimal stockValueZwg,
            BigDecimal sellingPriceUsd,
            BigDecimal sellingPriceZwg,
            BigDecimal aggregateReorderLevelKg,
            long openShiftCount,
            Set<Long> inUseTankIds,
            List<PaymentMix> paymentMix,
            List<HourlyRevenue> hourlyRevenue
    ) {}

    public record PaymentMix(String paymentMethod, CurrencyCode currency, BigDecimal amount) {}
    public record HourlyRevenue(int hour, BigDecimal usd, BigDecimal zwg) {}
    public record DailyRevenue(LocalDate date, BigDecimal usd, BigDecimal zwg) {}
    public record SalesAnalytics(BigDecimal soldKg, BigDecimal revenueUsd,
                                 BigDecimal revenueZwg, long transactions,
                                 BigDecimal averageSaleUsd,
                                 List<PaymentMix> paymentMix) {}
    public record ExpenseCurrencySummary(CurrencyCode currency, BigDecimal total,
                                         long count, LocalDateTime lastRecordedAt) {}
    public record ExpenseCategorySummary(String category, CurrencyCode currency,
                                         BigDecimal total, long count) {}
    public record ExpenseDailySummary(LocalDate date, CurrencyCode currency,
                                      BigDecimal total, long count) {}
    public record ExpenseAnalytics(List<ExpenseCurrencySummary> currencies,
                                   List<ExpenseCategorySummary> categories,
                                   List<ExpenseDailySummary> daily) {}
    public record RestockSummary(BigDecimal totalKg, long count,
                                 LocalDateTime lastReceivedAt) {}
    private record TankWeights(BigDecimal tareKg, BigDecimal fullGrossKg,
                               BigDecimal capacityKg, BigDecimal currentNetKg) {}
}
