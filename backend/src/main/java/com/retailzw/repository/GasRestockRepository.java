package com.retailzw.repository;

import com.retailzw.model.GasRestock;
import com.retailzw.enums.CurrencyCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GasRestockRepository extends JpaRepository<GasRestock, Long> {
    List<GasRestock> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasRestock> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);
}
