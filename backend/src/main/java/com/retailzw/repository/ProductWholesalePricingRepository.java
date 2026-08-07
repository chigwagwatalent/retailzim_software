package com.retailzw.repository;

import com.retailzw.model.ProductWholesalePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductWholesalePricingRepository extends JpaRepository<ProductWholesalePricing, Long> {

    Optional<ProductWholesalePricing> findByTenantIdAndProductId(Long tenantId, Long productId);

    List<ProductWholesalePricing> findByTenantIdAndProductIdIn(Long tenantId, Collection<Long> productIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE product_wholesale_pricing
            SET price_zwg = ROUND(ROUND(price_usd, :priceScale) * :rate, :priceScale),
                price_usd = ROUND(price_usd, :priceScale),
                exchange_rate_id = :exchangeRateId,
                version = version + 1
            WHERE tenant_id = :tenantId
              AND is_enabled = TRUE
              AND (:includeManual = 1 OR pricing_mode <> 'MANUAL')
            """, nativeQuery = true)
    int repriceFromUsd(@Param("tenantId") Long tenantId,
                       @Param("rate") BigDecimal rate,
                       @Param("priceScale") int priceScale,
                       @Param("exchangeRateId") Long exchangeRateId,
                       @Param("includeManual") int includeManual);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE product_wholesale_pricing
            SET price_usd = ROUND(ROUND(price_zwg, :priceScale) / :rate, :priceScale),
                price_zwg = ROUND(price_zwg, :priceScale),
                exchange_rate_id = :exchangeRateId,
                version = version + 1
            WHERE tenant_id = :tenantId
              AND is_enabled = TRUE
              AND (:includeManual = 1 OR pricing_mode <> 'MANUAL')
            """, nativeQuery = true)
    int repriceFromZwg(@Param("tenantId") Long tenantId,
                       @Param("rate") BigDecimal rate,
                       @Param("priceScale") int priceScale,
                       @Param("exchangeRateId") Long exchangeRateId,
                       @Param("includeManual") int includeManual);
}
