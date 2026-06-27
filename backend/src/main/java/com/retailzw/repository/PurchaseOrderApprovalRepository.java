package com.retailzw.repository;

import com.retailzw.model.PurchaseOrderApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseOrderApprovalRepository extends JpaRepository<PurchaseOrderApproval, Long> {
    List<PurchaseOrderApproval> findByTenantIdAndPurchaseOrderIdOrderByActedAtAsc(Long tenantId, Long purchaseOrderId);
}
