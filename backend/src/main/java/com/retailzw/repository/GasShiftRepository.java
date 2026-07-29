package com.retailzw.repository;

import com.retailzw.enums.GasShiftStatus;
import com.retailzw.model.GasShift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface GasShiftRepository extends JpaRepository<GasShift, Long> {
    Optional<GasShift> findByTenantIdAndBranchIdAndCashierIdAndStatus(Long tenantId, Long branchId, Long cashierId, GasShiftStatus status);
    Page<GasShift> findByTenantIdAndBranchIdOrderByOpenedAtDesc(Long tenantId, Long branchId, Pageable pageable);
    @Query("""
            select s from GasShift s
            where s.tenantId = :tenantId
              and s.branchId = :branchId
              and (:status is null or s.status = :status)
              and (:query is null or lower(s.shiftNumber) like lower(concat('%', :query, '%')))
            order by s.openedAt desc
            """)
    Page<GasShift> search(@Param("tenantId") Long tenantId,
                          @Param("branchId") Long branchId,
                          @Param("query") String query,
                          @Param("status") GasShiftStatus status,
                          Pageable pageable);
    List<GasShift> findByTenantIdAndBranchIdAndStatusOrderByOpenedAtDesc(Long tenantId, Long branchId, GasShiftStatus status);
    long countByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, GasShiftStatus status);
}
