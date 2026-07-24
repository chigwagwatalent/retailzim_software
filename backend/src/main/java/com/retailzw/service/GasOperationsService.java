package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasPriceRequest;
import com.retailzw.dto.request.GasExpenseRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
import com.retailzw.dto.request.GasStockReconciliationRequest;
import com.retailzw.dto.request.GasTankRequest;
import com.retailzw.dto.request.OpenGasShiftRequest;
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
import java.time.format.DateTimeFormatter;
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

    public List<GasPrice> prices(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return prices.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId);
    }

    @Transactional
    public GasTank createTank(Long tenantId, GasTankRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        enforceTankLimit(tenantId);
        BigDecimal capacityKg = nvl(request.getCapacityKg());
        BigDecimal currentKg = nvl(request.getCurrentKg());
        BigDecimal reorderLevelKg = nvl(request.getReorderLevelKg());
        validateTankLevels(capacityKg, currentKg, reorderLevelKg);
        GasTank tank = tanks.save(GasTank.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(requiredText(request.getName(), "Tank name is required."))
                .productName(requiredText(request.getProductName(), "Product name is required."))
                .capacityKg(capacityKg)
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
        BigDecimal currentKg = request.getCurrentKg() == null ? quantityBefore : nvl(request.getCurrentKg());
        BigDecimal capacityKg = nvl(request.getCapacityKg());
        BigDecimal reorderLevelKg = nvl(request.getReorderLevelKg());
        validateTankLevels(capacityKg, currentKg, reorderLevelKg);
        tank.setName(requiredText(request.getName(), "Tank name is required."));
        tank.setProductName(requiredText(request.getProductName(), "Product name is required."));
        tank.setCapacityKg(capacityKg);
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
        seedGasDefaults(tenantId, branchId);
        return shifts.save(GasShift.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .cashierId(cashierId)
                .shiftNumber("GAS-" + branchId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis())
                .openedAt(LocalDateTime.now())
                .status(GasShiftStatus.OPEN)
                .build());
    }

    @Transactional
    public GasSale completeSale(Long tenantId, Long cashierId, GasSaleRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        if (request.getOfflineReceiptNumber() != null && !request.getOfflineReceiptNumber().isBlank()) {
            var existing = sales.findByTenantIdAndOfflineReceiptNumber(tenantId, request.getOfflineReceiptNumber());
            if (existing.isPresent()) return existing.get();
        }
        GasShift shift = shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("Open a gas shift before selling gas."));
        GasTank tank = tanks.lockTank(tenantId, branchId, request.getTankId())
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        if (!GasTankStatus.ACTIVE.equals(tank.getStatus())) {
            throw new IllegalStateException("This tank is not active.");
        }
        BigDecimal quantity = nvl(request.getQuantityKg());
        if (tank.getCurrentKg().compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient gas stock in " + tank.getName() + ".");
        }
        CurrencyCode currency = request.getCurrency() == null ? CurrencyCode.USD : request.getCurrency();
        BigDecimal unitPrice = prices.findFirstByTenantIdAndBranchIdAndCurrencyAndIsActiveTrueOrderByCreatedAtDesc(tenantId, branchId, currency)
                .map(GasPrice::getPricePerKg)
                .orElseThrow(() -> new IllegalStateException("Set an active gas price for " + currency + " first."));
        BigDecimal total = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        tank.setCurrentKg(tank.getCurrentKg().subtract(quantity));
        tanks.save(tank);

        GasSale sale = sales.save(GasSale.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .gasShiftId(shift.getId())
                .tankId(tank.getId())
                .cashierId(cashierId)
                .receiptNumber("GAS" + branchId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis())
                .customerName(blank(request.getCustomerName()))
                .customerPhone(blank(request.getCustomerPhone()))
                .quantityKg(quantity)
                .unitPrice(unitPrice)
                .total(total)
                .currency(currency)
                .paymentMethod(normalizePaymentMethod(request.getPaymentMethod()))
                .paymentReference(blank(request.getPaymentReference()))
                .offlineReceiptNumber(blank(request.getOfflineReceiptNumber()))
                .build());
        shift.setTotalKgSold(nvl(shift.getTotalKgSold()).add(quantity));
        if (CurrencyCode.ZWG.equals(currency)) {
            shift.setTotalZwg(nvl(shift.getTotalZwg()).add(total));
        } else {
            shift.setTotalUsd(nvl(shift.getTotalUsd()).add(total));
        }
        shifts.save(shift);
        return sale;
    }

    @Transactional
    public GasRestock restock(Long tenantId, Long userId, GasRestockRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasTank tank = tanks.lockTank(tenantId, branchId, request.getTankId())
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        BigDecimal quantity = nvl(request.getQuantityKg());
        BigDecimal capacity = nvl(tank.getCapacityKg());
        if (capacity.compareTo(BigDecimal.ZERO) > 0 && nvl(tank.getCurrentKg()).add(quantity).compareTo(capacity) > 0) {
            throw new IllegalStateException("Restock would exceed " + tank.getName() + " capacity.");
        }
        tank.setCurrentKg(nvl(tank.getCurrentKg()).add(quantity));
        tanks.save(tank);
        BigDecimal unitCost = nvl(request.getUnitCost()).setScale(4, RoundingMode.HALF_UP);
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
        BigDecimal countedKg = nvl(request.getCountedKg()).setScale(3, RoundingMode.HALF_UP);
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
        return recordAdjustment(tank, quantityBefore, countedKg,
                requiredText(request.getReason(), "Reconciliation reason is required."),
                blank(request.getNotes()), userId);
    }

    @Transactional
    public GasExpense recordExpense(Long tenantId, Long userId, GasExpenseRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        return expenses.save(GasExpense.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .category(requiredText(request.getCategory(), "Expense category is required."))
                .description(requiredText(request.getDescription(), "Expense description is required."))
                .amount(nvl(request.getAmount()).setScale(2, RoundingMode.HALF_UP))
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
        shift.setStatus(GasShiftStatus.CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        return shifts.save(shift);
    }

    public List<GasSale> shiftSales(Long tenantId, Long branchId, Long cashierId) {
        GasShift shift = currentShift(tenantId, branchId, cashierId);
        if (shift == null) return List.of();
        return sales.findByTenantIdAndBranchIdAndGasShiftIdOrderByCreatedAtDesc(tenantId, branchId, shift.getId());
    }

    public List<GasSale> sales(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return sales.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public Page<GasShift> shifts(Long tenantId, Long branchId, Pageable pageable) {
        requireGasBranch(tenantId, branchId);
        return shifts.findByTenantIdAndBranchIdOrderByOpenedAtDesc(tenantId, branchId, pageable);
    }

    public GasDashboard dashboard(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<GasTank> branchTanks = tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId);
        List<GasSale> todayUsdSales = sales.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.USD, start, end);
        List<GasSale> todayZwgSales = sales.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.ZWG, start, end);
        List<GasRestock> todayUsdRestocks = restocks.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.USD, start, end);
        List<GasRestock> todayZwgRestocks = restocks.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.ZWG, start, end);
        List<GasExpense> todayUsdExpenses = expenses.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.USD, start, end);
        List<GasExpense> todayZwgExpenses = expenses.findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(tenantId, branchId, CurrencyCode.ZWG, start, end);
        BigDecimal soldKg = sum(todayUsdSales, GasSale::getQuantityKg).add(sum(todayZwgSales, GasSale::getQuantityKg));
        BigDecimal revenueUsd = sum(todayUsdSales, GasSale::getTotal);
        BigDecimal revenueZwg = sum(todayZwgSales, GasSale::getTotal);
        BigDecimal restockCostUsd = sum(todayUsdRestocks, GasRestock::getTotalCost);
        BigDecimal restockCostZwg = sum(todayZwgRestocks, GasRestock::getTotalCost);
        BigDecimal expensesUsd = sum(todayUsdExpenses, GasExpense::getAmount);
        BigDecimal expensesZwg = sum(todayZwgExpenses, GasExpense::getAmount);
        List<GasTank> reorderTanks = branchTanks.stream()
                .filter(tank -> nvl(tank.getReorderLevelKg()).compareTo(BigDecimal.ZERO) > 0)
                .filter(tank -> nvl(tank.getCurrentKg()).compareTo(nvl(tank.getReorderLevelKg())) <= 0)
                .toList();
        return new GasDashboard(soldKg, revenueUsd, revenueZwg, restockCostUsd, restockCostZwg,
                expensesUsd, expensesZwg, revenueUsd.subtract(restockCostUsd).subtract(expensesUsd),
                revenueZwg.subtract(restockCostZwg).subtract(expensesZwg), reorderTanks, ZIMBABWE_LPG_WEIGHTS_KG);
    }

    public List<GasRestock> restocks(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return restocks.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public List<GasExpense> expenses(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return expenses.findByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    public List<GasStockAdjustment> stockAdjustments(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        return stockAdjustments.findTop50ByTenantIdAndBranchIdOrderByCreatedAtDesc(tenantId, branchId);
    }

    @Transactional
    public void seedGasDefaults(Long tenantId, Long branchId) {
        requireGasBranch(tenantId, branchId);
        if (tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId).isEmpty()) {
            tanks.save(GasTank.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .name("Main LPG Tank")
                    .productName("LPG Gas")
                    .capacityKg(new BigDecimal("1000.000"))
                    .currentKg(BigDecimal.ZERO)
                    .reorderLevelKg(new BigDecimal("100.000"))
                    .status(GasTankStatus.ACTIVE)
                    .build());
        }
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
        return clean == null ? "CASH" : clean.toUpperCase().replace(' ', '_');
    }

    private <T> BigDecimal sum(List<T> rows, java.util.function.Function<T, BigDecimal> mapper) {
        return rows.stream().map(mapper).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
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
            List<BigDecimal> lpgWeightPresetsKg
    ) {}
}
