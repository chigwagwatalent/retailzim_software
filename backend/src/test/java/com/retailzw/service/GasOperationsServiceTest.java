package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
import com.retailzw.dto.request.GasSaleTankRequest;
import com.retailzw.dto.request.GasTankClosingWeightRequest;
import com.retailzw.dto.request.GasStockReconciliationRequest;
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
        assertThat(restock.getQuantityKg()).isEqualByComparingTo("25.000");
    }

    @Test
    public void reconciliationUpdatesPhysicalStockAndCreatesVarianceAudit() {
        Long tenantId = 1L;
        Long branchId = 2L;
        Long userId = 3L;
        GasTank tank = GasTank.builder()
                .id(10L).tenantId(tenantId).branchId(branchId).name("Main Tank").productName("LPG")
                .capacityKg(new BigDecimal("100.000"))
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
        request.setCountedKg(new BigDecimal("47.500"));
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
                .capacityKg(new BigDecimal("20.000")).status(GasTankStatus.ACTIVE).build();
        GasTank tankB = GasTank.builder().id(11L).tenantId(tenantId).branchId(branchId)
                .name("Tank B").productName("LPG").currentKg(new BigDecimal("10.000"))
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
                .capacityKg(new BigDecimal("100.000")).currentKg(new BigDecimal("40.000")).build();
        GasTank tankB = GasTank.builder().id(11L).tenantId(tenantId).branchId(branchId)
                .name("Tank B").tareWeightKg(new BigDecimal("15.000"))
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
