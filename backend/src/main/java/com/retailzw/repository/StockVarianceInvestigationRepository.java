package com.retailzw.repository;

import com.retailzw.model.StockVarianceInvestigation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockVarianceInvestigationRepository extends JpaRepository<StockVarianceInvestigation, Long> {
    List<StockVarianceInvestigation> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    long countByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, StockVarianceInvestigation.Status status);
}
