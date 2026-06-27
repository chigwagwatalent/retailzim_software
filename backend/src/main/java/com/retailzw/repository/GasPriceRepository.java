package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.GasPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GasPriceRepository extends JpaRepository<GasPrice, Long> {
    List<GasPrice> findByTenantIdAndBranchIdAndIsActiveTrue(Long tenantId, Long branchId);
    List<GasPrice> findByTenantIdAndBranchIdAndCurrencyAndIsActiveTrue(Long tenantId, Long branchId, CurrencyCode currency);
    Optional<GasPrice> findFirstByTenantIdAndBranchIdAndCurrencyAndIsActiveTrueOrderByCreatedAtDesc(Long tenantId, Long branchId, CurrencyCode currency);
}
