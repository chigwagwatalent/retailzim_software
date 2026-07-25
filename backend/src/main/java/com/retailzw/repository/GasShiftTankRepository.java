package com.retailzw.repository;

import com.retailzw.model.GasShiftTank;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GasShiftTankRepository extends JpaRepository<GasShiftTank, Long> {
    List<GasShiftTank> findByTenantIdAndBranchIdAndGasShiftIdOrderByTankId(
            Long tenantId, Long branchId, Long gasShiftId);

    List<GasShiftTank> findByTenantIdAndBranchIdAndStatusOrderBySelectedAtAsc(
            Long tenantId, Long branchId, GasShiftTank.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select selected from GasShiftTank selected
            where selected.tenantId = :tenantId
              and selected.branchId = :branchId
              and selected.gasShiftId = :shiftId
              and selected.tankId = :tankId
            """)
    Optional<GasShiftTank> lockSelectedTank(@Param("tenantId") Long tenantId,
                                            @Param("branchId") Long branchId,
                                            @Param("shiftId") Long shiftId,
                                            @Param("tankId") Long tankId);

    boolean existsByTenantIdAndBranchIdAndTankIdAndStatus(
            Long tenantId, Long branchId, Long tankId, GasShiftTank.Status status);
}
