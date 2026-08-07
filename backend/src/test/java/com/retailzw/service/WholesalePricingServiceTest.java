package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Product;
import com.retailzw.model.ProductWholesalePricing;
import com.retailzw.model.TenantExchangeRate;
import com.retailzw.repository.ProductWholesalePricingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WholesalePricingServiceTest {

    private ProductWholesalePricingRepository prices;
    private CurrencyConversionService currencyConversion;
    private WholesalePricingService service;

    @BeforeEach
    void setUp() {
        prices = mock(ProductWholesalePricingRepository.class);
        currencyConversion = mock(CurrencyConversionService.class);
        service = new WholesalePricingService(prices, currencyConversion);
        when(prices.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void productsRemainRetailWhenWholesaleIsNotConfigured() {
        Product product = product();
        when(prices.findByTenantIdAndProductId(7L, 11L)).thenReturn(Optional.empty());

        WholesalePricingService.PriceQuote quote =
                service.resolve(7L, product, new BigDecimal("100"), CurrencyCode.USD);

        assertThat(quote.tier()).isEqualTo(WholesalePricingService.PricingTier.RETAIL);
        assertThat(quote.unitPrice()).isEqualByComparingTo("2.00");
        assertThat(quote.minimumQuantity()).isNull();
    }

    @Test
    void wholesaleActivatesAtTheConfiguredQuantityForTheEntireLine() {
        Product product = product();
        when(prices.findByTenantIdAndProductId(7L, 11L)).thenReturn(Optional.of(
                ProductWholesalePricing.builder()
                        .tenantId(7L)
                        .productId(11L)
                        .isEnabled(true)
                        .minimumQuantity(new BigDecimal("12"))
                        .priceUsd(new BigDecimal("1.70"))
                        .priceZwg(new BigDecimal("51.00"))
                        .version(4L)
                        .build()));

        WholesalePricingService.PriceQuote below =
                service.resolve(7L, product, new BigDecimal("11"), CurrencyCode.USD);
        WholesalePricingService.PriceQuote threshold =
                service.resolve(7L, product, new BigDecimal("12"), CurrencyCode.USD);
        WholesalePricingService.PriceQuote zwg =
                service.resolve(7L, product, new BigDecimal("20"), CurrencyCode.ZWG);

        assertThat(below.tier()).isEqualTo(WholesalePricingService.PricingTier.RETAIL);
        assertThat(below.unitPrice()).isEqualByComparingTo("2.00");
        assertThat(threshold.tier()).isEqualTo(WholesalePricingService.PricingTier.WHOLESALE);
        assertThat(threshold.unitPrice()).isEqualByComparingTo("1.70");
        assertThat(threshold.pricingVersion()).isEqualTo(4L);
        assertThat(zwg.unitPrice()).isEqualByComparingTo("51.00");
    }

    @Test
    void usdBaseConfigurationCalculatesTheZwgEquivalent() {
        Product product = product();
        when(prices.findByTenantIdAndProductId(7L, 11L)).thenReturn(Optional.empty());
        when(currencyConversion.activeRate(7L)).thenReturn(Optional.of(
                TenantExchangeRate.builder()
                        .id(31L)
                        .tenantId(7L)
                        .baseCurrency(CurrencyCode.USD)
                        .quoteCurrency(CurrencyCode.ZWG)
                        .usdToZwgRate(new BigDecimal("30"))
                        .priceScale(2)
                        .isActive(true)
                        .build()));

        ProductWholesalePricing saved = service.configure(
                7L, product, true, new BigDecimal("12"),
                new BigDecimal("1.70"), null, 9L, false);

        assertThat(saved.getIsEnabled()).isTrue();
        assertThat(saved.getPriceUsd()).isEqualByComparingTo("1.70");
        assertThat(saved.getPriceZwg()).isEqualByComparingTo("51.00");
        assertThat(saved.getPricingMode()).isEqualTo(Product.PricingMode.AUTO_FROM_USD);
        assertThat(saved.getExchangeRateId()).isEqualTo(31L);
    }

    @Test
    void disablingWholesaleClearsTheRuleAndKeepsRetailAsTheDefault() {
        ProductWholesalePricing existing = ProductWholesalePricing.builder()
                .tenantId(7L)
                .productId(11L)
                .isEnabled(true)
                .minimumQuantity(new BigDecimal("12"))
                .priceUsd(new BigDecimal("1.70"))
                .priceZwg(new BigDecimal("51"))
                .build();
        when(prices.findByTenantIdAndProductId(7L, 11L)).thenReturn(Optional.of(existing));

        ProductWholesalePricing saved =
                service.configure(7L, product(), false, null, null, null, 9L, false);

        assertThat(saved.getIsEnabled()).isFalse();
        assertThat(saved.getMinimumQuantity()).isNull();
        assertThat(saved.getPriceUsd()).isNull();
        assertThat(saved.getPriceZwg()).isNull();
    }

    @Test
    void invalidOrMoreExpensiveWholesaleConfigurationIsRejected() {
        when(prices.findByTenantIdAndProductId(7L, 11L)).thenReturn(Optional.empty());
        when(currencyConversion.activeRate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.configure(
                7L, product(), true, BigDecimal.ONE,
                new BigDecimal("1.50"), new BigDecimal("45"), 9L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than 1");

        assertThatThrownBy(() -> service.configure(
                7L, product(), true, new BigDecimal("12"),
                new BigDecimal("2.50"), new BigDecimal("75"), 9L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be greater");
    }

    private Product product() {
        return Product.builder()
                .id(11L)
                .tenantId(7L)
                .name("Bread")
                .sellingPriceUsd(new BigDecimal("2.00"))
                .sellingPriceZwg(new BigDecimal("60.00"))
                .build();
    }
}
