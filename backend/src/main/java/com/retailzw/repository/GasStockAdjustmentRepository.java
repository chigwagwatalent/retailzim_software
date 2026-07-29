package com.retailzw.repository;

import com.retailzw.model.GasStockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GasStockAdjustmentRepository extends JpaRepository<GasStockAdjustment, Long> {
    List<GasStockAdjustment> findTop50ByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    Page<GasStockAdjustment> findByTenantIdAndBranchIdOrderByCreatedAtDesc(
            Long tenantId, Long branchId, Pageable pageable);
}
