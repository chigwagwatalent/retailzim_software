package com.retailzw.repository;

import com.retailzw.enums.GasShiftStatus;
import com.retailzw.model.GasShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface GasShiftRepository extends JpaRepository<GasShift, Long> {
    Optional<GasShift> findByTenantIdAndBranchIdAndCashierIdAndStatus(Long tenantId, Long branchId, Long cashierId, GasShiftStatus status);
    Page<GasShift> findByTenantIdAndBranchIdOrderByOpenedAtDesc(Long tenantId, Long branchId, Pageable pageable);
    List<GasShift> findByTenantIdAndBranchIdAndStatusOrderByOpenedAtDesc(Long tenantId, Long branchId, GasShiftStatus status);
    long countByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, GasShiftStatus status);
}
