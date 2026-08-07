package com.retailzw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Product;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantExchangeRate;
import com.retailzw.repository.ProductRepository;
import com.retailzw.repository.ProductWholesalePricingRepository;
import com.retailzw.repository.TenantExchangeRateRepository;
import com.retailzw.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CurrencyConversionServiceTest {

    private TenantRepository tenants;
    private TenantExchangeRateRepository rates;
    private ProductRepository products;
    private ProductWholesalePricingRepository wholesalePrices;
    private CurrencyConversionService service;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantRepository.class);
        rates = mock(TenantExchangeRateRepository.class);
        products = mock(ProductRepository.class);
        wholesalePrices = mock(ProductWholesalePricingRepository.class);
        service = new CurrencyConversionService(tenants, rates, products, wholesalePrices);
    }

    @Test
    void usdBaseAutomaticallyCalculatesAndRoundsZwgPrices() {
        when(rates.findFirstByTenantIdAndIsActiveTrueOrderByEffectiveFromDescIdDesc(7L))
                .thenReturn(Optional.of(rate(31L, CurrencyCode.USD, "27.345600", 2)));
        Product product = Product.builder().tenantId(7L).build();

        service.applyConfiguredPricing(
                7L, product,
                new BigDecimal("2.135"), new BigDecimal("3.999"),
                new BigDecimal("999"), new BigDecimal("999"));

        assertThat(product.getCostPriceUsd()).isEqualByComparingTo("2.14");
        assertThat(product.getSellingPriceUsd()).isEqualByComparingTo("4.00");
        assertThat(product.getCostPriceZwg()).isEqualByComparingTo("58.52");
        assertThat(product.getSellingPriceZwg()).isEqualByComparingTo("109.38");
        assertThat(product.getPricingMode()).isEqualTo(Product.PricingMode.AUTO_FROM_USD);
        assertThat(product.getExchangeRateId()).isEqualTo(31L);
    }

    @Test
    void zwgBaseAutomaticallyCalculatesUsdPrices() {
        when(rates.findFirstByTenantIdAndIsActiveTrueOrderByEffectiveFromDescIdDesc(7L))
                .thenReturn(Optional.of(rate(32L, CurrencyCode.ZWG, "25.000000", 2)));
        Product product = Product.builder().tenantId(7L).build();

        service.applyConfiguredPricing(
                7L, product,
                new BigDecimal("999"), new BigDecimal("999"),
                new BigDecimal("50"), new BigDecimal("87.50"));

        assertThat(product.getCostPriceUsd()).isEqualByComparingTo("2.00");
        assertThat(product.getSellingPriceUsd()).isEqualByComparingTo("3.50");
        assertThat(product.getCostPriceZwg()).isEqualByComparingTo("50.00");
        assertThat(product.getSellingPriceZwg()).isEqualByComparingTo("87.50");
        assertThat(product.getPricingMode()).isEqualTo(Product.PricingMode.AUTO_FROM_ZWG);
    }

    @Test
    void noActiveRatePreservesBothPricesAndUsesManualMode() {
        when(rates.findFirstByTenantIdAndIsActiveTrueOrderByEffectiveFromDescIdDesc(7L))
                .thenReturn(Optional.empty());
        Product product = Product.builder().tenantId(7L)
                .pricingMode(Product.PricingMode.AUTO_FROM_USD)
                .exchangeRateId(4L)
                .build();

        service.applyConfiguredPricing(
                7L, product,
                new BigDecimal("2.00"), new BigDecimal("3.00"),
                new BigDecimal("51.00"), new BigDecimal("76.50"));

        assertThat(product.getSellingPriceUsd()).isEqualByComparingTo("3.00");
        assertThat(product.getSellingPriceZwg()).isEqualByComparingTo("76.50");
        assertThat(product.getPricingMode()).isEqualTo(Product.PricingMode.MANUAL);
        assertThat(product.getExchangeRateId()).isNull();
    }

    @Test
    void changingRateRepricesAutomaticProductsButLeavesManualProductsAlone() {
        Tenant tenant = Tenant.builder()
                .id(7L)
                .defaultCurrency(CurrencyCode.USD)
                .secondaryCurrency(CurrencyCode.ZWG)
                .build();
        Product automatic = Product.builder()
                .id(1L).tenantId(7L)
                .costPriceUsd(new BigDecimal("2"))
                .sellingPriceUsd(new BigDecimal("3"))
                .costPriceZwg(new BigDecimal("40"))
                .sellingPriceZwg(new BigDecimal("60"))
                .pricingMode(Product.PricingMode.AUTO_FROM_USD)
                .build();
        Product manual = Product.builder()
                .id(2L).tenantId(7L)
                .sellingPriceUsd(new BigDecimal("5"))
                .sellingPriceZwg(new BigDecimal("123"))
                .pricingMode(Product.PricingMode.MANUAL)
                .build();

        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(rates.lockActiveRates(7L)).thenReturn(List.of());
        when(rates.save(any(TenantExchangeRate.class))).thenAnswer(invocation -> {
            TenantExchangeRate saved = invocation.getArgument(0);
            saved.setId(90L);
            return saved;
        });
        when(products.countByTenantIdAndPricingModeNot(7L, Product.PricingMode.MANUAL)).thenReturn(1L);
        when(products.repriceFromUsd(7L, new BigDecimal("30.000000"), 2, 90L, 0)).thenReturn(1);

        CurrencyConversionService.RateUpdateResult result = service.configure(
                7L, 12L, CurrencyCode.USD, new BigDecimal("30"), 2,
                true, false, "Daily rate");

        assertThat(result.repricedProducts()).isEqualTo(1);
        assertThat(manual.getSellingPriceZwg()).isEqualByComparingTo("123");
        verify(products).repriceFromUsd(7L, new BigDecimal("30.000000"), 2, 90L, 0);
    }

    @Test
    void applyingNewRateToExistingCatalogueConvertsManualProducts() {
        Tenant tenant = Tenant.builder().id(7L).build();
        Product manual = Product.builder()
                .id(2L).tenantId(7L)
                .costPriceUsd(new BigDecimal("4"))
                .sellingPriceUsd(new BigDecimal("5"))
                .costPriceZwg(BigDecimal.ZERO)
                .sellingPriceZwg(BigDecimal.ZERO)
                .pricingMode(Product.PricingMode.MANUAL)
                .build();
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(rates.lockActiveRates(7L)).thenReturn(List.of());
        when(rates.save(any(TenantExchangeRate.class))).thenAnswer(invocation -> {
            TenantExchangeRate saved = invocation.getArgument(0);
            saved.setId(91L);
            return saved;
        });
        when(products.repriceFromUsd(7L, new BigDecimal("20.000000"), 2, 91L, 1)).thenReturn(1);

        CurrencyConversionService.RateUpdateResult result = service.configure(
                7L, 12L, CurrencyCode.USD, new BigDecimal("20"), 2,
                true, true, "Initial catalogue conversion");

        assertThat(result.repricedProducts()).isEqualTo(1);
        verify(products).repriceFromUsd(7L, new BigDecimal("20.000000"), 2, 91L, 1);
    }

    @Test
    void firstAutomaticRateMigratesManualCatalogueEvenWithoutExplicitCheckbox() {
        Tenant tenant = Tenant.builder().id(7L).build();
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(rates.lockActiveRates(7L)).thenReturn(List.of());
        when(rates.save(any(TenantExchangeRate.class))).thenAnswer(invocation -> {
            TenantExchangeRate saved = invocation.getArgument(0);
            saved.setId(92L);
            return saved;
        });
        when(products.countByTenantIdAndPricingModeNot(7L, Product.PricingMode.MANUAL)).thenReturn(0L);
        when(products.repriceFromUsd(7L, new BigDecimal("35.000000"), 2, 92L, 1)).thenReturn(302);
        when(wholesalePrices.repriceFromUsd(7L, new BigDecimal("35.000000"), 2, 92L, 1)).thenReturn(4);

        CurrencyConversionService.RateUpdateResult result = service.configure(
                7L, 12L, CurrencyCode.USD, new BigDecimal("35"), 2,
                true, false, "Initial automatic conversion");

        assertThat(result.repricedProducts()).isEqualTo(306);
        verify(products).repriceFromUsd(7L, new BigDecimal("35.000000"), 2, 92L, 1);
        verify(wholesalePrices).repriceFromUsd(7L, new BigDecimal("35.000000"), 2, 92L, 1);
    }

    @Test
    void disablingConversionClosesActiveRateWithoutChangingProducts() {
        Tenant tenant = Tenant.builder().id(7L).build();
        TenantExchangeRate oldRate = rate(3L, CurrencyCode.USD, "30", 2);
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(rates.lockActiveRates(7L)).thenReturn(List.of(oldRate));

        CurrencyConversionService.RateUpdateResult result = service.configure(
                7L, 12L, CurrencyCode.USD, null, 2,
                false, true, "Manual pricing requested");

        assertThat(result.enabled()).isFalse();
        assertThat(oldRate.getIsActive()).isFalse();
        assertThat(oldRate.getEffectiveTo()).isNotNull();
        verify(products, never()).repriceFromUsd(anyLong(), any(), anyInt(), anyLong(), anyInt());
        verify(products, never()).repriceFromZwg(anyLong(), any(), anyInt(), anyLong(), anyInt());
        verify(rates, never()).save(any(TenantExchangeRate.class));
    }

    @Test
    void invalidRateIsRejectedBeforeItCanPriceProducts() {
        Tenant tenant = Tenant.builder().id(7L).build();
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(rates.lockActiveRates(7L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.configure(
                7L, 12L, CurrencyCode.USD, BigDecimal.ZERO, 2,
                true, false, "Invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        verify(products, never()).repriceFromUsd(anyLong(), any(), anyInt(), anyLong(), anyInt());
        verify(products, never()).repriceFromZwg(anyLong(), any(), anyInt(), anyLong(), anyInt());
    }

    @Test
    void internalPricingMetadataIsNotAddedToExistingProductJsonContract() throws Exception {
        Product product = Product.builder()
                .id(1L)
                .tenantId(7L)
                .name("Bread")
                .pricingMode(Product.PricingMode.AUTO_FROM_USD)
                .exchangeRateId(90L)
                .build();

        String json = new ObjectMapper().writeValueAsString(product);

        assertThat(json).contains("\"sellingPriceUsd\"", "\"sellingPriceZwg\"");
        assertThat(json).doesNotContain("pricingMode", "exchangeRateId");
    }

    private TenantExchangeRate rate(Long id, CurrencyCode base, String usdToZwg, int scale) {
        return TenantExchangeRate.builder()
                .id(id)
                .tenantId(7L)
                .baseCurrency(base)
                .quoteCurrency(CurrencyCode.USD.equals(base) ? CurrencyCode.ZWG : CurrencyCode.USD)
                .usdToZwgRate(new BigDecimal(usdToZwg))
                .priceScale(scale)
                .isActive(true)
                .build();
    }
}
