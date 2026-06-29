package com.retailzw.repository;

import com.retailzw.model.GasSale;
import com.retailzw.enums.CurrencyCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GasSaleRepository extends JpaRepository<GasSale, Long> {
    List<GasSale> findByTenantIdAndBranchIdAndGasShiftIdOrderByCreatedAtDesc(Long tenantId, Long branchId, Long gasShiftId);
    List<GasSale> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasSale> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);
    Optional<GasSale> findByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);
}
