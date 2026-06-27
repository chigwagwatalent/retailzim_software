package com.retailzw.service;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GasOperationsServiceTest {
    private BranchRepository branches;
    private GasTankRepository tanks;
    private GasPriceRepository prices;
    private GasShiftRepository shifts;
    private GasSaleRepository sales;
    private GasRestockRepository restocks;
    private TenantSubscriptionRepository subscriptions;
    private SaasPlanRepository plans;
    private GasOperationsService service;

    @BeforeEach
    void setUp() {
        branches = mock(BranchRepository.class);
        tanks = mock(GasTankRepository.class);
        prices = mock(GasPriceRepository.class);
        shifts = mock(GasShiftRepository.class);
        sales = mock(GasSaleRepository.class);
        restocks = mock(GasRestockRepository.class);
        subscriptions = mock(TenantSubscriptionRepository.class);
        plans = mock(SaasPlanRepository.class);
        service = new GasOperationsService(branches, tanks, prices, shifts, sales, restocks, subscriptions, plans);
    }

    @Test
    void gasShiftSaleAndCloseUpdatesTankStockAndTotals() {
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
        assertThat(savedSales).hasSize(1);
    }

    @Test
    void restockIncreasesTankStock() {
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
}
