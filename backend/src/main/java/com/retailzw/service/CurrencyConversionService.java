package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Product;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantExchangeRate;
import com.retailzw.repository.ProductRepository;
import com.retailzw.repository.ProductWholesalePricingRepository;
import com.retailzw.repository.TenantExchangeRateRepository;
import com.retailzw.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyConversionService {

    private static final BigDecimal MAX_RATE = new BigDecimal("1000000");

    private final TenantRepository tenants;
    private final TenantExchangeRateRepository rates;
    private final ProductRepository products;
    private final ProductWholesalePricingRepository wholesalePrices;

    @Transactional(readOnly = true)
    public Optional<TenantExchangeRate> activeRate(Long tenantId) {
        return rates.findFirstByTenantIdAndIsActiveTrueOrderByEffectiveFromDescIdDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<TenantExchangeRate> recentRates(Long tenantId) {
        return rates.findByTenantIdOrderByEffectiveFromDescIdDesc(tenantId, PageRequest.of(0, 10));
    }

    @Transactional
    public RateUpdateResult configure(Long tenantId,
                                      Long userId,
                                      CurrencyCode baseCurrency,
                                      BigDecimal usdToZwgRate,
                                      Integer priceScale,
                                      boolean automaticConversionEnabled,
                                      boolean applyToExistingProducts,
                                      String changeReason) {
        Tenant tenant = tenants.findLockedById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Shop was not found."));
        int normalizedScale = normalizeScale(priceScale);
        LocalDateTime now = LocalDateTime.now();
        List<TenantExchangeRate> activeRates = rates.lockActiveRates(tenantId);
        activeRates.forEach(active -> {
            active.setIsActive(false);
            active.setEffectiveTo(now);
        });
        rates.saveAll(activeRates);

        CurrencyCode normalizedBase = baseCurrency == null ? CurrencyCode.USD : baseCurrency;
        tenant.setDefaultCurrency(normalizedBase);
        tenant.setSecondaryCurrency(otherCurrency(normalizedBase));
        tenants.save(tenant);

        if (!automaticConversionEnabled) {
            log.info("Automatic currency conversion disabled tenant={} user={}", tenantId, userId);
            return new RateUpdateResult(null, 0, false);
        }

        BigDecimal normalizedRate = validateRate(usdToZwgRate);
        TenantExchangeRate savedRate = rates.save(TenantExchangeRate.builder()
                .tenantId(tenantId)
                .baseCurrency(normalizedBase)
                .quoteCurrency(otherCurrency(normalizedBase))
                .usdToZwgRate(normalizedRate)
                .priceScale(normalizedScale)
                .isActive(true)
                .effectiveFrom(now)
                .changeReason(cleanReason(changeReason))
                .createdBy(userId)
                .build());

        boolean hasAutomaticallyPricedProducts = products.countByTenantIdAndPricingModeNot(
                tenantId, Product.PricingMode.MANUAL) > 0;
        boolean includeManualProducts = applyToExistingProducts || !hasAutomaticallyPricedProducts;
        int repriced = repriceProducts(tenantId, savedRate, includeManualProducts);
        log.info("Exchange rate configured tenant={} user={} base={} usdToZwg={} scale={} repriced={} includeManual={} requestedIncludeManual={}",
                tenantId, userId, normalizedBase, normalizedRate, normalizedScale, repriced,
                includeManualProducts, applyToExistingProducts);
        return new RateUpdateResult(savedRate, repriced, true);
    }

    @Transactional(readOnly = true)
    public void applyConfiguredPricing(Long tenantId, Product product,
                                       BigDecimal costUsd, BigDecimal sellingUsd,
                                       BigDecimal costZwg, BigDecimal sellingZwg) {
        applyConfiguredPricing(product, costUsd, sellingUsd, costZwg, sellingZwg, activeRate(tenantId));
    }

    public void applyConfiguredPricing(Product product,
                                       BigDecimal costUsd, BigDecimal sellingUsd,
                                       BigDecimal costZwg, BigDecimal sellingZwg,
                                       Optional<TenantExchangeRate> active) {
        if (active.isEmpty()) {
            product.setCostPriceUsd(nonNegative(costUsd, "Cost price USD"));
            product.setSellingPriceUsd(nonNegative(sellingUsd, "Selling price USD"));
            product.setCostPriceZwg(nonNegative(costZwg, "Cost price ZWG"));
            product.setSellingPriceZwg(nonNegative(sellingZwg, "Selling price ZWG"));
            product.setPricingMode(Product.PricingMode.MANUAL);
            product.setExchangeRateId(null);
            return;
        }

        TenantExchangeRate rate = active.get();
        applyRate(product, rate, rate.getBaseCurrency(), costUsd, sellingUsd, costZwg, sellingZwg);
    }

    private int repriceProducts(Long tenantId, TenantExchangeRate rate, boolean includeManual) {
        int includeManualFlag = includeManual ? 1 : 0;
        if (CurrencyCode.ZWG.equals(rate.getBaseCurrency())) {
            int retail = products.repriceFromZwg(
                    tenantId,
                    rate.getUsdToZwgRate(),
                    rate.getPriceScale(),
                    rate.getId(),
                    includeManualFlag);
            int wholesale = wholesalePrices.repriceFromZwg(
                    tenantId, rate.getUsdToZwgRate(), rate.getPriceScale(), rate.getId(), includeManualFlag);
            return retail + wholesale;
        }
        int retail = products.repriceFromUsd(
                tenantId,
                rate.getUsdToZwgRate(),
                rate.getPriceScale(),
                rate.getId(),
                includeManualFlag);
        int wholesale = wholesalePrices.repriceFromUsd(
                tenantId, rate.getUsdToZwgRate(), rate.getPriceScale(), rate.getId(), includeManualFlag);
        return retail + wholesale;
    }

    private void applyRate(Product product,
                           TenantExchangeRate rate,
                           CurrencyCode sourceCurrency,
                           BigDecimal costUsd,
                           BigDecimal sellingUsd,
                           BigDecimal costZwg,
                           BigDecimal sellingZwg) {
        int scale = normalizeScale(rate.getPriceScale());
        BigDecimal usdToZwg = validateRate(rate.getUsdToZwgRate());
        if (CurrencyCode.ZWG.equals(sourceCurrency)) {
            BigDecimal normalizedCostZwg = nonNegative(costZwg, "Cost price ZWG");
            BigDecimal normalizedSellingZwg = nonNegative(sellingZwg, "Selling price ZWG");
            BigDecimal roundedCostZwg = normalizedCostZwg.setScale(scale, RoundingMode.HALF_UP);
            BigDecimal roundedSellingZwg = normalizedSellingZwg.setScale(scale, RoundingMode.HALF_UP);
            product.setCostPriceZwg(roundedCostZwg);
            product.setSellingPriceZwg(roundedSellingZwg);
            product.setCostPriceUsd(roundedCostZwg.divide(usdToZwg, scale, RoundingMode.HALF_UP));
            product.setSellingPriceUsd(roundedSellingZwg.divide(usdToZwg, scale, RoundingMode.HALF_UP));
            product.setPricingMode(Product.PricingMode.AUTO_FROM_ZWG);
        } else {
            BigDecimal normalizedCostUsd = nonNegative(costUsd, "Cost price USD");
            BigDecimal normalizedSellingUsd = nonNegative(sellingUsd, "Selling price USD");
            BigDecimal roundedCostUsd = normalizedCostUsd.setScale(scale, RoundingMode.HALF_UP);
            BigDecimal roundedSellingUsd = normalizedSellingUsd.setScale(scale, RoundingMode.HALF_UP);
            product.setCostPriceUsd(roundedCostUsd);
            product.setSellingPriceUsd(roundedSellingUsd);
            product.setCostPriceZwg(roundedCostUsd.multiply(usdToZwg).setScale(scale, RoundingMode.HALF_UP));
            product.setSellingPriceZwg(roundedSellingUsd.multiply(usdToZwg).setScale(scale, RoundingMode.HALF_UP));
            product.setPricingMode(Product.PricingMode.AUTO_FROM_USD);
        }
        product.setExchangeRateId(rate.getId());
    }

    private BigDecimal validateRate(BigDecimal rate) {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("USD to ZWG rate must be greater than zero and no more than 1,000,000.");
        }
        return rate.setScale(6, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return normalized;
    }

    private int normalizeScale(Integer scale) {
        int normalized = scale == null ? 2 : scale;
        if (normalized < 0 || normalized > 4) {
            throw new IllegalArgumentException("Price decimal places must be between 0 and 4.");
        }
        return normalized;
    }

    private CurrencyCode otherCurrency(CurrencyCode currency) {
        return CurrencyCode.ZWG.equals(currency) ? CurrencyCode.USD : CurrencyCode.ZWG;
    }

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Rate updated in Store Configuration";
        }
        String clean = reason.trim();
        return clean.length() > 500 ? clean.substring(0, 500) : clean;
    }

    public record RateUpdateResult(TenantExchangeRate rate, int repricedProducts, boolean enabled) {
    }
}
