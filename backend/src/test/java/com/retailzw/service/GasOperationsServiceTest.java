package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasExpenseRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
import com.retailzw.dto.request.GasSaleTankRequest;
import com.retailzw.dto.request.GasTankClosingWeightRequest;
import com.retailzw.dto.request.GasStockReconciliationRequest;
import com.retailzw.dto.request.GasTankRequest;
import com.retailzw.dto.request.OpenGasShiftRequest;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.GasShiftStatus;
import com.retailzw.enums.GasTankStatus;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GasOperationsServiceTest {
    private BranchRepository branches;
    private GasTankRepository tanks;
    private GasPriceRepository prices;
    private GasShiftRepository shifts;
    private GasSaleRepository sales;
    private GasRestockRepository restocks;
    private GasExpenseRepository expenses;
    private GasStockAdjustmentRepository stockAdjustments;
    private TenantSubscriptionRepository subscriptions;
    private SaasPlanRepository plans;
    private GasShiftTankRepository shiftTanks;
    private GasSaleTankAllocationRepository saleTankAllocations;
    private GasSalePaymentRepository salePayments;
    private HeldChangeRepository heldChange;
    private GasOperationsService service;

    @BeforeEach
    public void setUp() {
        branches = mock(BranchRepository.class);
        tanks = mock(GasTankRepository.class);
        prices = mock(GasPriceRepository.class);
        shifts = mock(GasShiftRepository.class);
        sales = mock(GasSaleRepository.class);
        restocks = mock(GasRestockRepository.class);
        expenses = mock(GasExpenseRepository.class);
        stockAdjustments = mock(GasStockAdjustmentRepository.class);
        subscriptions = mock(TenantSubscriptionRepository.class);
        plans = mock(SaasPlanRepository.class);
        shiftTanks = mock(GasShiftTankRepository.class);
        saleTankAllocations = mock(GasSaleTankAllocationRepository.class);
        salePayments = mock(GasSalePaymentRepository.class);
        heldChange = mock(HeldChangeRepository.class);
        service = new GasOperationsService(
                branches, tanks, prices, shifts, sales, restocks, expenses,
                stockAdjustments, subscriptions, plans, shiftTanks,
                saleTankAllocations, salePayments, heldChange);
    }

    @Test
    public void tankSetupDerivesNetCapacityAndStockFromPhysicalWeights() {
        Long tenantId = 1L;
        Long branchId = 2L;
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> {
            GasTank saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(stockAdjustments.save(any(GasStockAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GasTankRequest request = new GasTankRequest();
        request.setBranchId(branchId);
        request.setName("Tank A");
        request.setProductName("LPG Gas");
        request.setTareWeightKg(new BigDecimal("200.000"));
        request.setFullGrossWeightKg(new BigDecimal("1200.000"));
        request.setCurrentGrossWeightKg(new BigDecimal("950.000"));
        request.setReorderLevelKg(new BigDecimal("100.000"));

        GasTank tank = service.createTank(tenantId, request);

        assertThat(tank.getCapacityKg()).isEqualByComparingTo("1000.000");
        assertThat(tank.getCurrentKg()).isEqualByComparingTo("750.000");
        assertThat(tank.getCurrentGrossWeightKg()).isEqualByComparingTo("950.000");
        verify(stockAdjustments).save(argThat(adjustment ->
                adjustment.getCountedKg().compareTo(new BigDecimal("750.000")) == 0));
    }

    @Test
    public void tankSetupRejectsCurrentGrossAboveFullGross() {
        Long tenantId = 1L;
        Long branchId = 2L;
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));

        GasTankRequest request = new GasTankRequest();
        request.setBranchId(branchId);
        request.setName("Tank A");
        request.setProductName("LPG Gas");
        request.setTareWeightKg(new BigDecimal("200.000"));
        request.setFullGrossWeightKg(new BigDecimal("1200.000"));
        request.setCurrentGrossWeightKg(new BigDecimal("1200.001"));
        request.setReorderLevelKg(new BigDecimal("100.000"));

        assertThatThrownBy(() -> service.createTank(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed its full gross weight");
        verify(tanks, never()).save(any(GasTank.class));
    }

    @Test
    public void expenseRecordingRejectsNonPositiveAmountsBeforePersistence() {
        Long tenantId = 1L;
        Long branchId = 2L;
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));

        GasExpenseRequest request = new GasExpenseRequest();
        request.setBranchId(branchId);
        request.setCategory("Rent");
        request.setDescription("Monthly storage rent");
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency(CurrencyCode.USD);
        request.setPaymentMethod("CASH");

        assertThatThrownBy(() -> service.recordExpense(tenantId, 9L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
        verify(expenses, never()).save(any(GasExpense.class));
    }

    @Test
    public void openingShiftSnapshotsDerivedGrossAndNetWeights() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long cashierId = 3L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId)
                .name("Tank A").productName("LPG Gas")
                .tareWeightKg(new BigDecimal("200.000"))
                .fullGrossWeightKg(new BigDecimal("1200.000"))
                .capacityKg(new BigDecimal("1000.000"))
                .currentKg(new BigDecimal("750.000"))
                .status(GasTankStatus.ACTIVE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(
                tenantId, branchId, cashierId, GasShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(tanks.findByTenantIdAndBranchIdAndStatusOrderByNameAsc(
                tenantId, branchId, GasTankStatus.ACTIVE)).thenReturn(List.of(tank));
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));
        when(shifts.save(any(GasShift.class))).thenAnswer(invocation -> {
            GasShift saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });
        when(shiftTanks.save(any(GasShiftTank.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OpenGasShiftRequest request = new OpenGasShiftRequest();
        request.setBranchId(branchId);
        request.setTankIds(List.of(tank.getId()));
        service.openShift(tenantId, cashierId, request);

        verify(shiftTanks).save(argThat(snapshot ->
                snapshot.getStartingGrossKg().compareTo(new BigDecimal("950.000")) == 0
                        && snapshot.getStartingNetKg().compareTo(new BigDecimal("750.000")) == 0
                        && snapshot.getExpectedClosingNetKg().compareTo(new BigDecimal("750.000")) == 0));
    }

    @Test
    public void gasShiftSaleAndCloseUpdatesTankStockAndTotals() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long cashierId = 3L;
        Branch branch = Branch.builder().id(branchId).tenantId(tenantId).moduleType(BusinessModule.GAS_MODULE).isActive(true).build();
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .currentKg(new BigDecimal("50.000")).status(GasTankStatus.ACTIVE).build();
        GasShift shift = GasShift.builder()
                .id(20L).tenantId(tenantId).branchId(branchId).cashierId(cashierId)
                .status(GasShiftStatus.OPEN).totalKgSold(BigDecimal.ZERO)
                .totalUsd(BigDecimal.ZERO).totalZwg(BigDecimal.ZERO).build();

        when(branches.findById(branchId)).thenReturn(Optional.of(branch));
        when(tanks.findByTenantIdAndBranchIdOrderByNameAsc(tenantId, branchId)).thenReturn(List.of(tank));
        when(tanks.findByTenantIdAndBranchIdAndStatusOrderByNameAsc(
                tenantId, branchId, GasTankStatus.ACTIVE)).thenReturn(List.of(tank));
        when(prices.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId)).thenReturn(List.of(
                GasPrice.builder().tenantId(tenantId).branchId(branchId).currency(CurrencyCode.USD).pricePerKg(new BigDecimal("2.0000")).build()
        ));
        when(shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(tenantId, branchId, cashierId, GasShiftStatus.OPEN))
                .thenReturn(Optional.empty(), Optional.of(shift), Optional.of(shift));
        when(shifts.save(any(GasShift.class))).thenAnswer(invocation -> {
            GasShift value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(20L);
            return value;
        });
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        GasShiftTank selectedTank = GasShiftTank.builder()
                .id(21L).tenantId(tenantId).branchId(branchId).gasShiftId(20L).tankId(tank.getId())
                .expectedClosingNetKg(new BigDecimal("50.000")).status(GasShiftTank.Status.IN_USE).build();
        when(shiftTanks.save(any(GasShiftTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTanks.lockSelectedTank(tenantId, branchId, 20L, tank.getId()))
                .thenReturn(Optional.of(selectedTank));
        when(shiftTanks.findByTenantIdAndBranchIdAndGasShiftIdOrderByTankId(
                tenantId, branchId, 20L)).thenReturn(List.of());
        when(saleTankAllocations.save(any(GasSaleTankAllocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(salePayments.save(any(GasSalePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prices.findFirstByTenantIdAndBranchIdAndCurrencyAndIsActiveTrueOrderByCreatedAtDesc(tenantId, branchId, CurrencyCode.USD))
                .thenReturn(Optional.of(GasPrice.builder().pricePerKg(new BigDecimal("2.0000")).build()));
        List<GasSale> savedSales = new ArrayList<>();
        when(sales.save(any(GasSale.class))).thenAnswer(invocation -> {
            GasSale value = invocation.getArgument(0);
            value.setId(30L);
            savedSales.add(value);
            return value;
        });

        OpenGasShiftRequest open = new OpenGasShiftRequest();
        open.setBranchId(branchId);
        GasShift opened = service.openShift(tenantId, cashierId, open);

        GasSaleRequest saleRequest = new GasSaleRequest();
        saleRequest.setBranchId(branchId);
        saleRequest.setTankId(tank.getId());
        saleRequest.setQuantityKg(new BigDecimal("5.500"));
        saleRequest.setCurrency(CurrencyCode.USD);
        GasSale sale = service.completeSale(tenantId, cashierId, saleRequest);

        CloseGasShiftRequest close = new CloseGasShiftRequest();
        close.setBranchId(branchId);
        close.setShiftId(opened.getId());
        when(shifts.findById(opened.getId())).thenReturn(Optional.of(shift));
        GasShift closed = service.closeShift(tenantId, cashierId, close);

        assertThat(sale.getTotal()).isEqualByComparingTo("11.00");
        assertThat(tank.getCurrentKg()).isEqualByComparingTo("44.500");
        assertThat(shift.getTotalKgSold()).isEqualByComparingTo("5.500");
        assertThat(shift.getTotalUsd()).isEqualByComparingTo("11.00");
        assertThat(closed.getStatus()).isEqualTo(GasShiftStatus.CLOSED);
        assertThat(savedSales).hasSize(2);
        assertThat(savedSales).allMatch(saved -> saved.getId().equals(sale.getId()));
    }

    @Test
    public void restockIncreasesTankStock() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .tareWeightKg(new BigDecimal("20.000"))
                .capacityKg(new BigDecimal("100.000"))
                .fullGrossWeightKg(new BigDecimal("120.000"))
                .currentKg(new BigDecimal("10.000")).status(GasTankStatus.ACTIVE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId).moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restocks.save(any(GasRestock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GasRestockRequest request = new GasRestockRequest();
        request.setBranchId(branchId);
        request.setTankId(tank.getId());
        request.setQuantityKg(new BigDecimal("25.000"));

        GasRestock restock = service.restock(tenantId, userId, request);

        assertThat(tank.getCurrentKg()).isEqualByComparingTo("35.000");
        assertThat(tank.getCurrentGrossWeightKg()).isEqualByComparingTo("55.000");
        assertThat(restock.getQuantityKg()).isEqualByComparingTo("25.000");
    }

    @Test
    public void restockRejectsNegativeCostBeforeStockChanges() {
        Long tenantId = 1L;
        Long branchId = 2L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .capacityKg(new BigDecimal("100.000"))
                .currentKg(new BigDecimal("10.000")).status(GasTankStatus.ACTIVE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));

        GasRestockRequest request = new GasRestockRequest();
        request.setBranchId(branchId);
        request.setTankId(tank.getId());
        request.setQuantityKg(new BigDecimal("25.000"));
        request.setUnitCost(new BigDecimal("-0.0100"));

        assertThatThrownBy(() -> service.restock(tenantId, 3L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
        assertThat(tank.getCurrentKg()).isEqualByComparingTo("10.000");
        verify(tanks, never()).save(any(GasTank.class));
        verify(restocks, never()).save(any(GasRestock.class));
    }

    @Test
    public void salesAnalyticsCalculatesPeriodTotalsAndAverageUsdSale() {
        Long tenantId = 1L;
        Long branchId = 2L;
        LocalDateTime from = LocalDateTime.of(2026, 7, 29, 0, 0);
        LocalDateTime to = from.plusDays(1);
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(sales.dailySummary(tenantId, branchId, from, to)).thenReturn(List.of(
                new Object[]{CurrencyCode.USD, new BigDecimal("65.000"), new BigDecimal("143.00"), 6L},
                new Object[]{CurrencyCode.ZWG, new BigDecimal("5.000"), new BigDecimal("300.00"), 1L}
        ));
        when(salePayments.paymentMix(tenantId, branchId, from, to)).thenReturn(List.of(
                new Object[]{"CASH", CurrencyCode.USD, new BigDecimal("105.00")},
                new Object[]{"ECOCASH", CurrencyCode.USD, new BigDecimal("38.00")}
        ));

        GasOperationsService.SalesAnalytics analytics =
                service.salesAnalytics(tenantId, branchId, from, to);

        assertThat(analytics.soldKg()).isEqualByComparingTo("70.000");
        assertThat(analytics.revenueUsd()).isEqualByComparingTo("143.00");
        assertThat(analytics.revenueZwg()).isEqualByComparingTo("300.00");
        assertThat(analytics.transactions()).isEqualTo(7);
        assertThat(analytics.averageSaleUsd()).isEqualByComparingTo("23.83");
        assertThat(analytics.paymentMix()).hasSize(2);
    }

    @Test
    public void cancellingGasHeldChangeKeepsTheRecordInTheGasBranchAudit() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        HeldChange record = HeldChange.builder()
                .id(40L).tenantId(tenantId).branchId(branchId)
                .gasShiftId(20L).gasSaleId(30L)
                .referenceNumber("GCH-40").customerName("Customer")
                .phone("0770000000").currency(CurrencyCode.USD)
                .amount(new BigDecimal("5.00"))
                .status(HeldChange.Status.OPEN).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(heldChange.lockById(tenantId, record.getId())).thenReturn(Optional.of(record));
        when(heldChange.save(any(HeldChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HeldChange cancelled =
                service.cancelGasHeldChange(tenantId, branchId, userId, record.getId());

        assertThat(cancelled.getStatus()).isEqualTo(HeldChange.Status.CANCELLED);
        assertThat(cancelled.getCancelledBy()).isEqualTo(userId);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        verify(heldChange).save(record);
    }

    @Test
    public void reconciliationUpdatesPhysicalStockAndCreatesVarianceAudit() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .tareWeightKg(new BigDecimal("10.000"))
                .capacityKg(new BigDecimal("100.000"))
                .fullGrossWeightKg(new BigDecimal("110.000"))
                .currentKg(new BigDecimal("50.000"))
                .status(GasTankStatus.ACTIVE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockAdjustments.save(any(GasStockAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GasStockReconciliationRequest request = new GasStockReconciliationRequest();
        request.setBranchId(branchId);
        request.setTankId(tank.getId());
        request.setCountedGrossKg(new BigDecimal("57.500"));
        request.setReason("DIP_VARIANCE");
        request.setNotes("Verified against morning dip reading");

        GasStockAdjustment result = service.reconcileStock(tenantId, userId, request);

        assertThat(tank.getCurrentKg()).isEqualByComparingTo("47.500");
        assertThat(result.getQuantityBeforeKg()).isEqualByComparingTo("50.000");
        assertThat(result.getCountedKg()).isEqualByComparingTo("47.500");
        assertThat(result.getVarianceKg()).isEqualByComparingTo("-2.500");
        assertThat(result.getReason()).isEqualTo("DIP_VARIANCE");
        assertThat(result.getCreatedBy()).isEqualTo(userId);
    }

    @Test
    public void reconciliationRejectsCountsAboveTankCapacity() {
        Long tenantId = 1L;
        Long branchId = 2L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .capacityKg(new BigDecimal("100.000"))
                .currentKg(new BigDecimal("50.000"))
                .status(GasTankStatus.ACTIVE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tanks.lockTank(tenantId, branchId, tank.getId())).thenReturn(Optional.of(tank));

        GasStockReconciliationRequest request = new GasStockReconciliationRequest();
        request.setBranchId(branchId);
        request.setTankId(tank.getId());
        request.setCountedKg(new BigDecimal("100.001"));
        request.setReason("DIP_VARIANCE");

        assertThatThrownBy(() -> service.reconcileStock(tenantId, 3L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");
        assertThat(tank.getCurrentKg()).isEqualByComparingTo("50.000");
        verify(tanks, never()).save(any(GasTank.class));
        verify(stockAdjustments, never()).save(any(GasStockAdjustment.class));
    }

    @Test
    public void saleCanDrawFromMultipleShiftTanksAtomically() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long cashierId = 3L;
        GasTank tankA = GasTank.builder().id(10L).tenantId(tenantId).branchId(branchId)
                .name("Tank A").productName("LPG").currentKg(new BigDecimal("6.000"))
                .tareWeightKg(new BigDecimal("10.000")).fullGrossWeightKg(new BigDecimal("30.000"))
                .capacityKg(new BigDecimal("20.000")).status(GasTankStatus.ACTIVE).build();
        GasTank tankB = GasTank.builder().id(11L).tenantId(tenantId).branchId(branchId)
                .name("Tank B").productName("LPG").currentKg(new BigDecimal("10.000"))
                .tareWeightKg(new BigDecimal("15.000")).fullGrossWeightKg(new BigDecimal("35.000"))
                .capacityKg(new BigDecimal("20.000")).status(GasTankStatus.ACTIVE).build();
        GasShift shift = GasShift.builder().id(20L).tenantId(tenantId).branchId(branchId)
                .cashierId(cashierId).status(GasShiftStatus.OPEN).build();
        GasShiftTank selectedA = GasShiftTank.builder().id(21L).tenantId(tenantId)
                .branchId(branchId).gasShiftId(20L).tankId(10L)
                .expectedClosingNetKg(new BigDecimal("6.000")).status(GasShiftTank.Status.IN_USE).build();
        GasShiftTank selectedB = GasShiftTank.builder().id(22L).tenantId(tenantId)
                .branchId(branchId).gasShiftId(20L).tankId(11L)
                .expectedClosingNetKg(new BigDecimal("10.000")).status(GasShiftTank.Status.IN_USE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(shifts.findByTenantIdAndBranchIdAndCashierIdAndStatus(
                tenantId, branchId, cashierId, GasShiftStatus.OPEN)).thenReturn(Optional.of(shift));
        when(tanks.lockTank(tenantId, branchId, 10L)).thenReturn(Optional.of(tankA));
        when(tanks.lockTank(tenantId, branchId, 11L)).thenReturn(Optional.of(tankB));
        when(prices.findFirstByTenantIdAndBranchIdAndCurrencyAndIsActiveTrueOrderByCreatedAtDesc(
                tenantId, branchId, CurrencyCode.USD))
                .thenReturn(Optional.of(GasPrice.builder().pricePerKg(new BigDecimal("2.0000")).build()));
        when(sales.save(any(GasSale.class))).thenAnswer(invocation -> {
            GasSale saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTanks.lockSelectedTank(tenantId, branchId, 20L, 10L))
                .thenReturn(Optional.of(selectedA));
        when(shiftTanks.lockSelectedTank(tenantId, branchId, 20L, 11L))
                .thenReturn(Optional.of(selectedB));
        when(shiftTanks.save(any(GasShiftTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(saleTankAllocations.save(any(GasSaleTankAllocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(salePayments.save(any(GasSalePayment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shifts.save(any(GasShift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GasSaleTankRequest requestA = new GasSaleTankRequest();
        requestA.setTankId(10L);
        GasSaleTankRequest requestB = new GasSaleTankRequest();
        requestB.setTankId(11L);
        GasSaleRequest request = new GasSaleRequest();
        request.setBranchId(branchId);
        request.setQuantityKg(new BigDecimal("8.000"));
        request.setCurrency(CurrencyCode.USD);
        request.setTanks(List.of(requestA, requestB));

        GasSale sale = service.completeSale(tenantId, cashierId, request);

        assertThat(tankA.getCurrentKg()).isEqualByComparingTo("2.000");
        assertThat(tankB.getCurrentKg()).isEqualByComparingTo("6.000");
        assertThat(tankA.getCurrentGrossWeightKg()).isEqualByComparingTo("12.000");
        assertThat(tankB.getCurrentGrossWeightKg()).isEqualByComparingTo("21.000");
        assertThat(sale.getTankAllocations()).extracting(GasSaleTankAllocation::getQuantityKg)
                .containsExactly(new BigDecimal("4.000"), new BigDecimal("4.000"));
        assertThat(shift.getTotalKgSold()).isEqualByComparingTo("8.000");
    }

    @Test
    public void closeShiftUsesGrossMinusTareAndRecordsPhysicalVariance() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long cashierId = 3L;
        GasShift shift = GasShift.builder().id(20L).tenantId(tenantId).branchId(branchId)
                .cashierId(cashierId).status(GasShiftStatus.OPEN).build();
        GasTank tankA = GasTank.builder().id(10L).tenantId(tenantId).branchId(branchId)
                .name("Tank A").tareWeightKg(new BigDecimal("10.000"))
                .fullGrossWeightKg(new BigDecimal("110.000"))
                .capacityKg(new BigDecimal("100.000")).currentKg(new BigDecimal("40.000")).build();
        GasTank tankB = GasTank.builder().id(11L).tenantId(tenantId).branchId(branchId)
                .name("Tank B").tareWeightKg(new BigDecimal("15.000"))
                .fullGrossWeightKg(new BigDecimal("115.000"))
                .capacityKg(new BigDecimal("100.000")).currentKg(new BigDecimal("50.000")).build();
        GasShiftTank selectedA = GasShiftTank.builder().id(21L).tenantId(tenantId)
                .branchId(branchId).gasShiftId(20L).tankId(10L)
                .expectedClosingNetKg(new BigDecimal("40.000")).status(GasShiftTank.Status.IN_USE).build();
        GasShiftTank selectedB = GasShiftTank.builder().id(22L).tenantId(tenantId)
                .branchId(branchId).gasShiftId(20L).tankId(11L)
                .expectedClosingNetKg(new BigDecimal("50.000")).status(GasShiftTank.Status.IN_USE).build();
        when(branches.findById(branchId)).thenReturn(Optional.of(
                Branch.builder().id(branchId).tenantId(tenantId)
                        .moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(shifts.findById(20L)).thenReturn(Optional.of(shift));
        when(shiftTanks.findByTenantIdAndBranchIdAndGasShiftIdOrderByTankId(
                tenantId, branchId, 20L)).thenReturn(List.of(selectedA, selectedB));
        when(tanks.lockTank(tenantId, branchId, 10L)).thenReturn(Optional.of(tankA));
        when(tanks.lockTank(tenantId, branchId, 11L)).thenReturn(Optional.of(tankB));
        when(tanks.save(any(GasTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTanks.save(any(GasShiftTank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockAdjustments.save(any(GasStockAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(shifts.save(any(GasShift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        GasTankClosingWeightRequest closeA = new GasTankClosingWeightRequest();
        closeA.setTankId(10L);
        closeA.setClosingGrossKg(new BigDecimal("48.000"));
        GasTankClosingWeightRequest closeB = new GasTankClosingWeightRequest();
        closeB.setTankId(11L);
        closeB.setClosingGrossKg(new BigDecimal("64.000"));
        CloseGasShiftRequest request = new CloseGasShiftRequest();
        request.setBranchId(branchId);
        request.setShiftId(20L);
        request.setClosingWeights(List.of(closeA, closeB));

        GasShift result = service.closeShift(tenantId, cashierId, request);

        assertThat(tankA.getCurrentKg()).isEqualByComparingTo("38.000");
        assertThat(tankB.getCurrentKg()).isEqualByComparingTo("49.000");
        assertThat(result.getClosingVarianceKg()).isEqualByComparingTo("-3.000");
        assertThat(selectedA.getStatus()).isEqualTo(GasShiftTank.Status.CLOSED);
        verify(stockAdjustments, times(2)).save(any(GasStockAdjustment.class));
    }
}
