package com.retailzw.repository;

import com.retailzw.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByTenantIdAndBarcode(Long tenantId, String barcode);

    boolean existsByTenantIdAndSku(Long tenantId, String sku);
}

