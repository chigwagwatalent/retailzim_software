package com.retailzw.repository;

import com.retailzw.model.InventoryAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long> {

    Page<InventoryAdjustment> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    List<InventoryAdjustment> findByTenantIdAndBranchIdAndProductId(Long tenantId, Long branchId, Long productId);
}

