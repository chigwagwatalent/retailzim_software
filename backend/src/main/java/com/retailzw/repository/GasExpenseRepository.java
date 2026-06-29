package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.GasExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GasExpenseRepository extends JpaRepository<GasExpense, Long> {
    List<GasExpense> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasExpense> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);
}
