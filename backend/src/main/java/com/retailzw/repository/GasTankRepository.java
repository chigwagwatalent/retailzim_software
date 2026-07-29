package com.retailzw.repository;

import com.retailzw.enums.GasTankStatus;
import com.retailzw.model.GasTank;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface GasTankRepository extends JpaRepository<GasTank, Long> {
    List<GasTank> findByTenantIdAndBranchIdOrderByNameAsc(Long tenantId, Long branchId);
    Page<GasTank> findByTenantIdAndBranchIdOrderByNameAsc(Long tenantId, Long branchId, Pageable pageable);
    List<GasTank> findByTenantIdAndBranchIdAndStatusOrderByNameAsc(Long tenantId, Long branchId, GasTankStatus status);
    long countByTenantId(Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GasTank t where t.tenantId = :tenantId and t.branchId = :branchId and t.id = :tankId")
    Optional<GasTank> lockTank(@Param("tenantId") Long tenantId,
                               @Param("branchId") Long branchId,
                               @Param("tankId") Long tankId);
}
