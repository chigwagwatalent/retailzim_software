package com.retailzw.repository;

import com.retailzw.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByBranchIdAndProductId(Long branchId, Long productId);

    Optional<Inventory> findByTenantIdAndBranchIdAndProductId(Long tenantId, Long branchId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.tenantId = :tenantId and i.branchId = :branchId and i.productId = :productId")
    Optional<Inventory> lockStock(@Param("tenantId") Long tenantId,
                                  @Param("branchId") Long branchId,
                                  @Param("productId") Long productId);

    List<Inventory> findByTenantIdAndBranchId(Long tenantId, Long branchId);

    @Query("SELECT i FROM Inventory i JOIN Product p ON p.id = i.productId " +
           "WHERE i.tenantId = :tenantId AND i.branchId = :branchId " +
           "AND p.tenantId = :tenantId AND p.isActive = true " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(p.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY p.name ASC")
    List<Inventory> findBranchProductStock(@Param("tenantId") Long tenantId,
                                           @Param("branchId") Long branchId,
                                           @Param("search") String search,
                                           @Param("categoryId") Long categoryId);

    List<Inventory> findByTenantId(Long tenantId);

    @Query("SELECT i FROM Inventory i " +
           "JOIN Product p ON p.id = i.productId " +
           "WHERE i.tenantId = :tenantId AND i.branchId = :branchId " +
           "AND p.isService = false AND p.isActive = true " +
           "AND i.quantityOnHand <= p.reorderLevel AND p.reorderLevel > 0")
    List<Inventory> findLowStockItems(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId);

    @Query("SELECT i FROM Inventory i " +
           "JOIN Product p ON p.id = i.productId " +
           "WHERE i.tenantId = :tenantId " +
           "AND p.isService = false AND p.isActive = true " +
           "AND i.quantityOnHand <= p.reorderLevel AND p.reorderLevel > 0")
    List<Inventory> findLowStockItemsByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT SUM(i.quantityOnHand * i.averageCostUsd) FROM Inventory i " +
           "WHERE i.tenantId = :tenantId AND i.branchId = :branchId")
    Double getTotalValuationUsd(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId);

    @Query("SELECT SUM(i.quantityOnHand * i.averageCostZwg) FROM Inventory i " +
           "WHERE i.tenantId = :tenantId AND i.branchId = :branchId")
    Double getTotalValuationZwg(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId);

    List<Inventory> findByProductId(Long productId);
}

