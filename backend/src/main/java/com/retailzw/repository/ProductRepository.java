package com.retailzw.repository;

import com.retailzw.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByTenantIdAndBarcode(Long tenantId, String barcode);

    Optional<Product> findByTenantIdAndSku(Long tenantId, String sku);

    @EntityGraph(attributePaths = {"category", "unitOfMeasure"})
    Page<Product> findByTenantIdAndIsActiveTrue(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "unitOfMeasure"})
    List<Product> findByTenantIdAndIsActiveTrue(Long tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.category.id = :categoryId AND p.isActive = true")
    @EntityGraph(attributePaths = {"category", "unitOfMeasure"})
    Page<Product> findByTenantIdAndCategoryId(@Param("tenantId") Long tenantId, @Param("categoryId") Long categoryId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%')))")
    @EntityGraph(attributePaths = {"category", "unitOfMeasure"})
    Page<Product> searchProducts(@Param("tenantId") Long tenantId, @Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true AND " +
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%')))")
    @EntityGraph(attributePaths = {"category", "unitOfMeasure"})
    Page<Product> findProducts(@Param("tenantId") Long tenantId,
                               @Param("search") String search,
                               @Param("categoryId") Long categoryId,
                               Pageable pageable);

    long countByTenantIdAndIsActiveTrue(Long tenantId);

    long countByTenantIdAndPricingModeNot(Long tenantId, Product.PricingMode pricingMode);

    boolean existsByTenantIdAndBarcode(Long tenantId, String barcode);

    boolean existsByTenantIdAndSku(Long tenantId, String sku);

    @Query("SELECT p.tenantId, COUNT(p) FROM Product p WHERE p.tenantId IN :tenantIds AND p.isActive = true GROUP BY p.tenantId")
    List<Object[]> countActiveByTenantIds(@Param("tenantIds") List<Long> tenantIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET cost_price_zwg = ROUND(ROUND(cost_price_usd, :priceScale) * :rate, :priceScale),
                selling_price_zwg = ROUND(ROUND(selling_price_usd, :priceScale) * :rate, :priceScale),
                cost_price_usd = ROUND(cost_price_usd, :priceScale),
                selling_price_usd = ROUND(selling_price_usd, :priceScale),
                pricing_mode = 'AUTO_FROM_USD',
                exchange_rate_id = :exchangeRateId
            WHERE tenant_id = :tenantId
              AND (:includeManual = 1 OR pricing_mode <> 'MANUAL')
            """, nativeQuery = true)
    int repriceFromUsd(@Param("tenantId") Long tenantId,
                       @Param("rate") java.math.BigDecimal rate,
                       @Param("priceScale") int priceScale,
                       @Param("exchangeRateId") Long exchangeRateId,
                       @Param("includeManual") int includeManual);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE products
            SET cost_price_usd = ROUND(ROUND(cost_price_zwg, :priceScale) / :rate, :priceScale),
                selling_price_usd = ROUND(ROUND(selling_price_zwg, :priceScale) / :rate, :priceScale),
                cost_price_zwg = ROUND(cost_price_zwg, :priceScale),
                selling_price_zwg = ROUND(selling_price_zwg, :priceScale),
                pricing_mode = 'AUTO_FROM_ZWG',
                exchange_rate_id = :exchangeRateId
            WHERE tenant_id = :tenantId
              AND (:includeManual = 1 OR pricing_mode <> 'MANUAL')
            """, nativeQuery = true)
    int repriceFromZwg(@Param("tenantId") Long tenantId,
                       @Param("rate") java.math.BigDecimal rate,
                       @Param("priceScale") int priceScale,
                       @Param("exchangeRateId") Long exchangeRateId,
                       @Param("includeManual") int includeManual);
}

