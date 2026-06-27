package com.retailzw.repository;

import com.retailzw.model.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByTenantIdAndBranchIdAndProductId(Long tenantId, Long branchId, Long productId);

    Page<InventoryTransaction> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    @Query("SELECT it FROM InventoryTransaction it WHERE it.tenantId = :tenantId " +
           "AND it.branchId = :branchId AND it.productId = :productId " +
           "AND it.createdAt BETWEEN :from AND :to ORDER BY it.createdAt DESC")
    List<InventoryTransaction> findMovements(@Param("tenantId") Long tenantId,
                                             @Param("branchId") Long branchId,
                                             @Param("productId") Long productId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    List<InventoryTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}

