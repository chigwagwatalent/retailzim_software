package com.retailzw.repository;

import com.retailzw.model.GasSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GasSaleRepository extends JpaRepository<GasSale, Long> {
    List<GasSale> findByTenantIdAndBranchIdAndGasShiftIdOrderByCreatedAtDesc(Long tenantId, Long branchId, Long gasShiftId);
    Optional<GasSale> findByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);
}
