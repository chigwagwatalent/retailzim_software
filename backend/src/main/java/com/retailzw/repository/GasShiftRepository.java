package com.retailzw.repository;

import com.retailzw.enums.GasShiftStatus;
import com.retailzw.model.GasShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GasShiftRepository extends JpaRepository<GasShift, Long> {
    Optional<GasShift> findByTenantIdAndBranchIdAndCashierIdAndStatus(Long tenantId, Long branchId, Long cashierId, GasShiftStatus status);
    Page<GasShift> findByTenantIdAndBranchIdOrderByOpenedAtDesc(Long tenantId, Long branchId, Pageable pageable);
}
