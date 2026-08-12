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

    @Query("""
            select po.id as id,
                   po.poNumber as poNumber,
                   po.supplierId as supplierId,
                   po.status as status,
                   count(item.id) as itemCount
            from PurchaseOrder po
            left join po.items item
            where po.tenantId = :tenantId
              and po.branchId = :branchId
              and po.status in :statuses
            group by po.id, po.poNumber, po.supplierId, po.status, po.createdAt
            order by po.createdAt desc
            """)
    List<SupervisorReadyOrderView> findSupervisorReadyOrders(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("statuses") List<PurchaseOrder.PoStatus> statuses,
            Pageable pageable);

    List<PurchaseOrder> findByTenantIdAndSupplierId(Long tenantId, Long supplierId);

    boolean existsByPoNumber(String poNumber);

    interface SupervisorReadyOrderView {
        Long getId();
        String getPoNumber();
        Long getSupplierId();
        PurchaseOrder.PoStatus getStatus();
        Long getItemCount();
    }
}

