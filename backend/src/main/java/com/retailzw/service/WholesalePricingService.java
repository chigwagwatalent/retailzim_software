package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Product;
import com.retailzw.model.ProductWholesalePricing;
import com.retailzw.model.TenantExchangeRate;
import com.retailzw.repository.ProductWholesalePricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WholesalePricingService {

    public static final int PRICING_PROTOCOL_VERSION = 2;

    private final ProductWholesalePricingRepository wholesalePrices;
    private final CurrencyConversionService currencyConversion;

    @Transactional(readOnly = true)
    public Optional<ProductWholesalePricing> configuration(Long tenantId, Long productId) {
        return wholesalePrices.findByTenantIdAndProductId(tenantId, productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductWholesalePricing> configurations(Long tenantId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wholesalePrices.findByTenantIdAndProductIdIn(tenantId, productIds).stream()
                .collect(Collectors.toMap(ProductWholesalePricing::getProductId, Function.identity()));
    }

    @Transactional
    public ProductWholesalePricing configure(Long tenantId,
                                             Product product,
                                             Boolean enabled,
                                             BigDecimal minimumQuantity,
                                             BigDecimal priceUsd,
                                             BigDecimal priceZwg,
                                             Long updatedBy,
                                             boolean preserveWhenUnspecified) {
        if (enabled == null && preserveWhenUnspecified) {
            return configuration(tenantId, product.getId()).orElse(null);
        }
        boolean activate = Boolean.TRUE.equals(enabled);
        ProductWholesalePricing pricing = wholesalePrices.findByTenantIdAndProductId(tenantId, product.getId())
                .orElseGet(() -> ProductWholesalePricing.builder()
                        .tenantId(tenantId)
                        .productId(product.getId())
                        .isEnabled(false)
                        .build());
        pricing.setUpdatedBy(updatedBy);
        if (!activate) {
            pricing.setIsEnabled(false);
            pricing.setMinimumQuantity(null);
            pricing.setPriceUsd(null);
            pricing.setPriceZwg(null);
            pricing.setPricingMode(Product.PricingMode.MANUAL);
            pricing.setExchangeRateId(null);
            return wholesalePrices.save(pricing);
        }

        BigDecimal normalizedMinimum = positiveMinimum(minimumQuantity);
        Optional<TenantExchangeRate> activeRate = currencyConversion.activeRate(tenantId);
        BigDecimal normalizedUsd;
        BigDecimal normalizedZwg;
        Product.PricingMode mode;
        Long exchangeRateId;
        if (activeRate.isPresent()) {
            TenantExchangeRate rate = activeRate.get();
            int scale = rate.getPriceScale() == null ? 2 : rate.getPriceScale();
            BigDecimal usdToZwg = rate.getUsdToZwgRate();
            if (CurrencyCode.ZWG.equals(rate.getBaseCurrency())) {
                normalizedZwg = positivePrice(priceZwg, "Wholesale price ZWG").setScale(scale, RoundingMode.HALF_UP);
                normalizedUsd = normalizedZwg.divide(usdToZwg, scale, RoundingMode.HALF_UP);
                mode = Product.PricingMode.AUTO_FROM_ZWG;
            } else {
                normalizedUsd = positivePrice(priceUsd, "Wholesale price USD").setScale(scale, RoundingMode.HALF_UP);
                normalizedZwg = normalizedUsd.multiply(usdToZwg).setScale(scale, RoundingMode.HALF_UP);
                mode = Product.PricingMode.AUTO_FROM_USD;
            }
            exchangeRateId = rate.getId();
        } else {
            normalizedUsd = positivePrice(priceUsd, "Wholesale price USD");
            normalizedZwg = positivePrice(priceZwg, "Wholesale price ZWG");
            mode = Product.PricingMode.MANUAL;
            exchangeRateId = null;
        }
        validateNotAboveRetail(product, normalizedUsd, normalizedZwg);
        pricing.setIsEnabled(true);
        pricing.setMinimumQuantity(normalizedMinimum);
        pricing.setPriceUsd(normalizedUsd);
        pricing.setPriceZwg(normalizedZwg);
        pricing.setPricingMode(mode);
        pricing.setExchangeRateId(exchangeRateId);
        return wholesalePrices.save(pricing);
    }

    @Transactional(readOnly = true)
    public PriceQuote resolve(Long tenantId, Product product, BigDecimal totalQuantity, CurrencyCode currency) {
        BigDecimal retailPrice = price(currency, product.getSellingPriceUsd(), product.getSellingPriceZwg());
        Optional<ProductWholesalePricing> configured = configuration(tenantId, product.getId())
                .filter(row -> Boolean.TRUE.equals(row.getIsEnabled()))
                .filter(row -> row.getMinimumQuantity() != null)
                .filter(row -> totalQuantity != null && totalQuantity.compareTo(row.getMinimumQuantity()) >= 0);
        if (configured.isEmpty()) {
            return new PriceQuote(retailPrice, retailPrice, PricingTier.RETAIL, null, null, null);
        }
        ProductWholesalePricing wholesale = configured.get();
        BigDecimal wholesalePrice = price(currency, wholesale.getPriceUsd(), wholesale.getPriceZwg());
        return new PriceQuote(wholesalePrice, retailPrice, PricingTier.WHOLESALE,
                wholesale.getMinimumQuantity(), wholesale.getVersion(), wholesale.getExchangeRateId());
    }

    private BigDecimal price(CurrencyCode currency, BigDecimal usd, BigDecimal zwg) {
        return CurrencyCode.ZWG.equals(currency) ? zero(zwg) : zero(usd);
    }

    private BigDecimal positiveMinimum(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("Wholesale minimum quantity must be greater than 1.");
        }
        return value;
    }

    private BigDecimal positivePrice(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return value;
    }

    private void validateNotAboveRetail(Product product, BigDecimal usd, BigDecimal zwg) {
        if (positive(product.getSellingPriceUsd()) && usd.compareTo(product.getSellingPriceUsd()) > 0) {
            throw new IllegalArgumentException("Wholesale USD price cannot be greater than the retail USD price.");
        }
        if (positive(product.getSellingPriceZwg()) && zwg.compareTo(product.getSellingPriceZwg()) > 0) {
            throw new IllegalArgumentException("Wholesale ZWG price cannot be greater than the retail ZWG price.");
        }
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public enum PricingTier {
        RETAIL, WHOLESALE
    }

    public record PriceQuote(BigDecimal unitPrice,
                             BigDecimal retailUnitPrice,
                             PricingTier tier,
                             BigDecimal minimumQuantity,
                             Long pricingVersion,
                             Long exchangeRateId) {
    }
}
