package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasPriceRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
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

@Service
@RequiredArgsConstructor
public class GasOperationsService {
    private final BranchRepository branches;
    private final GasTankRepository tanks;
    private final GasPriceRepository prices;
    private final GasShiftRepository shifts;
    private final GasSaleRepository sales;
    private final GasRestockRepository restocks;
    private final TenantSubscriptionRepository subscriptions;
    private final SaasPlanRepository plans;

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
        return tanks.save(GasTank.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(requiredText(request.getName(), "Tank name is required."))
                .productName(requiredText(request.getProductName(), "Product name is required."))
                .capacityKg(nvl(request.getCapacityKg()))
                .currentKg(nvl(request.getCurrentKg()))
                .reorderLevelKg(nvl(request.getReorderLevelKg()))
                .status(request.getStatus() == null ? GasTankStatus.ACTIVE : request.getStatus())
                .build());
    }

    @Transactional
    public GasTank updateTank(Long tenantId, Long tankId, GasTankRequest request) {
        Long branchId = request.getBranchId();
        requireGasBranch(tenantId, branchId);
        GasTank tank = tanks.findById(tankId)
                .filter(existing -> existing.getTenantId().equals(tenantId) && existing.getBranchId().equals(branchId))
                .orElseThrow(() -> new IllegalArgumentException("Gas tank not found for this branch."));
        tank.setName(requiredText(request.getName(), "Tank name is required."));
        tank.setProductName(requiredText(request.getProductName(), "Product name is required."));
        tank.setCapacityKg(nvl(request.getCapacityKg()));
        tank.setCurrentKg(nvl(request.getCurrentKg()));
        tank.setReorderLevelKg(nvl(request.getReorderLevelKg()));
        tank.setStatus(request.getStatus() == null ? GasTankStatus.ACTIVE : request.getStatus());
        return tanks.save(tank);
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
        tank.setCurrentKg(nvl(tank.getCurrentKg()).add(quantity));
        tanks.save(tank);
        return restocks.save(GasRestock.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .tankId(tank.getId())
                .quantityKg(quantity)
                .supplierName(blank(request.getSupplierName()))
                .notes(blank(request.getNotes()))
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
}
