package com.retailzw.repository;

import com.retailzw.model.GasRestock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GasRestockRepository extends JpaRepository<GasRestock, Long> {
    List<GasRestock> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
}
