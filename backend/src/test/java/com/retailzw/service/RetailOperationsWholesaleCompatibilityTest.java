package com.retailzw.service;

import com.retailzw.dto.request.SaleItemRequest;
import com.retailzw.dto.request.SalePaymentRequest;
import com.retailzw.dto.request.SaleRequest;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetailOperationsWholesaleCompatibilityTest {

    private ProductRepository products;
    private InventoryRepository inventory;
    private SaleRepository sales;
    private SalePaymentRepository salePayments;
    private CashSessionRepository sessions;
    private WholesalePricingService wholesalePricing;
    private RetailOperationsService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        inventory = mock(InventoryRepository.class);
        sales = mock(SaleRepository.class);
        salePayments = mock(SalePaymentRepository.class);
        sessions = mock(CashSessionRepository.class);
        wholesalePricing = mock(WholesalePricingService.class);
        service = new RetailOperationsService(
                products,
                mock(ProductCategoryRepository.class),
                mock(UnitOfMeasureRepository.class),
                inventory,
                mock(InventoryTransactionRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(BranchRepository.class),
                mock(TenantEnabledModuleRepository.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(RoleRepository.class),
                mock(UserRepository.class),
                sales,
                salePayments,
                mock(CashDrawerRepository.class),
                sessions,
                mock(PasswordEncoder.class),
                mock(CreditAndChangeService.class),
                mock(CurrencyConversionService.class),
                wholesalePricing);
        when(products.findById(11L)).thenReturn(Optional.of(product()));
        when(inventory.lockStock(7L, 3L, 11L)).thenReturn(Optional.of(stock()));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sales.save(any())).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            sale.setId(91L);
            return sale;
        });
        when(salePayments.sumCashBySaleAndCurrency(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        when(sessions.findById(22L)).thenReturn(Optional.of(session()));
    }

    @Test
    void legacyClientKeepsItsExistingRetailPriceEvenWhenWholesaleExists() {
        SaleRequest request = request(12, null, false, "RETAIL", "2.00");

        Sale sale = service.completeSale(7L, 3L, 5L, request);

        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("2.00");
        assertThat(sale.getItems().get(0).getPricingTier())
                .isEqualTo(SaleItem.WholesalePricingTier.RETAIL);
        assertThat(sale.getItems().get(0).getPricingSource()).isEqualTo("LEGACY_RETAIL");
    }

    @Test
    void versionTwoClientUsesServerVerifiedWholesalePrice() {
        when(wholesalePricing.resolve(eq(7L), any(Product.class), eq(new BigDecimal("12")), eq(CurrencyCode.USD)))
                .thenReturn(new WholesalePricingService.PriceQuote(
                        new BigDecimal("1.70"),
                        new BigDecimal("2.00"),
                        WholesalePricingService.PricingTier.WHOLESALE,
                        new BigDecimal("12"),
                        4L,
                        31L));
        SaleRequest request = request(12, 2, false, "WHOLESALE", "1.70");

        Sale sale = service.completeSale(7L, 3L, 5L, request);

        SaleItem item = sale.getItems().get(0);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("1.70");
        assertThat(item.getPricingTier()).isEqualTo(SaleItem.WholesalePricingTier.WHOLESALE);
        assertThat(item.getPricingSource()).isEqualTo("SERVER_VERIFIED");
        assertThat(item.getPricingVersion()).isEqualTo(4L);
    }

    @Test
    void offlineVersionTwoSalePreservesThePriceCollectedByTheCashier() {
        when(wholesalePricing.resolve(anyLong(), any(), any(), any()))
                .thenReturn(new WholesalePricingService.PriceQuote(
                        new BigDecimal("1.60"),
                        new BigDecimal("2.00"),
                        WholesalePricingService.PricingTier.WHOLESALE,
                        new BigDecimal("12"),
                        5L,
                        32L));
        SaleRequest request = request(12, 2, true, "WHOLESALE", "1.70");
        request.setOfflineReceiptNumber("offline-wholesale-1");
        request.getItems().get(0).setPricingVersion(4L);

        Sale sale = service.completeSale(7L, 3L, 5L, request);

        SaleItem item = sale.getItems().get(0);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("1.70");
        assertThat(item.getPricingTier()).isEqualTo(SaleItem.WholesalePricingTier.WHOLESALE);
        assertThat(item.getPricingSource()).isEqualTo("OFFLINE_CACHED");
        assertThat(item.getPricingVersion()).isEqualTo(4L);
    }

    private SaleRequest request(int quantity,
                                Integer protocol,
                                boolean offlineLocked,
                                String tier,
                                String unitPrice) {
        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(11L);
        item.setQuantity(new BigDecimal(quantity));
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setDiscountAmount(BigDecimal.ZERO);
        item.setPricingTier(tier);

        SalePaymentRequest payment = new SalePaymentRequest();
        payment.setMethod(SalePayment.PaymentMethod.CASH);
        payment.setCurrency(CurrencyCode.USD);
        payment.setAmount(new BigDecimal("100"));
        payment.setExchangeRate(BigDecimal.ONE);

        SaleRequest request = new SaleRequest();
        request.setCashSessionId(22L);
        request.setBranchId(3L);
        request.setCurrency(CurrencyCode.USD);
        request.setItems(List.of(item));
        request.setPayments(List.of(payment));
        request.setPricingProtocolVersion(protocol);
        request.setOfflinePricingLocked(offlineLocked);
        return request;
    }

    private Product product() {
        return Product.builder()
                .id(11L)
                .tenantId(7L)
                .name("Bread")
                .sku("BRD")
                .sellingPriceUsd(new BigDecimal("2.00"))
                .sellingPriceZwg(new BigDecimal("60.00"))
                .costPriceUsd(new BigDecimal("1.00"))
                .costPriceZwg(new BigDecimal("30.00"))
                .taxRate(BigDecimal.ZERO)
                .isTaxable(false)
                .isService(false)
                .isActive(true)
                .build();
    }

    private Inventory stock() {
        return Inventory.builder()
                .tenantId(7L)
                .branchId(3L)
                .productId(11L)
                .quantityOnHand(new BigDecimal("100"))
                .quantityReserved(BigDecimal.ZERO)
                .build();
    }

    private CashSession session() {
        return CashSession.builder()
                .id(22L)
                .tenantId(7L)
                .branchId(3L)
                .cashierId(5L)
                .status(CashSession.SessionStatus.OPEN)
                .totalTransactions(0)
                .totalSalesUsd(BigDecimal.ZERO)
                .totalSalesZwg(BigDecimal.ZERO)
                .expectedCashUsd(BigDecimal.ZERO)
                .expectedCashZwg(BigDecimal.ZERO)
                .build();
    }
}
