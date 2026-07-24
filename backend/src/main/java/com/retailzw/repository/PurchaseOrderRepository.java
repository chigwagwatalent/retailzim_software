package com.retailzw.repository;

import com.retailzw.model.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);

    List<PurchaseOrder> findByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, PurchaseOrder.PoStatus status);

    Page<PurchaseOrder> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    List<PurchaseOrder> findAllByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);

    List<PurchaseOrder> findByTenantIdAndSupplierId(Long tenantId, Long supplierId);

    boolean existsByPoNumber(String poNumber);
}

