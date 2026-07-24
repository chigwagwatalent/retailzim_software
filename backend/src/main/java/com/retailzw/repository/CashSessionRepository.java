package com.retailzw.repository;

import com.retailzw.model.CashSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    @Query("SELECT cs FROM CashSession cs WHERE cs.drawerId = :drawerId AND cs.cashierId = :cashierId AND cs.status = 'OPEN'")
    Optional<CashSession> findOpenSessionByDrawerAndCashier(@Param("drawerId") Long drawerId, @Param("cashierId") Long cashierId);

    Optional<CashSession> findByBranchIdAndCashierIdAndStatus(Long branchId, Long cashierId, CashSession.SessionStatus status);

    Optional<CashSession> findByBranchIdAndStatus(Long branchId, CashSession.SessionStatus status);

    Optional<CashSession> findByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, CashSession.SessionStatus status);

    Optional<CashSession> findFirstByTenantIdAndBranchIdAndStatusOrderByOpenedAtDesc(Long tenantId, Long branchId, CashSession.SessionStatus status);

    @Query("SELECT cs FROM CashSession cs WHERE cs.tenantId = :tenantId AND cs.branchId = :branchId AND cs.cashierId = :cashierId AND cs.status = 'OPEN'")
    Optional<CashSession> findActiveSession(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId, @Param("cashierId") Long cashierId);

    Page<CashSession> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    List<CashSession> findAllByTenantIdAndBranchIdOrderByOpenedAtDesc(Long tenantId, Long branchId);

    List<CashSession> findAllByTenantIdAndBranchIdAndStatus(Long tenantId, Long branchId, CashSession.SessionStatus status);

    @Query("SELECT cs FROM CashSession cs WHERE cs.tenantId = :tenantId AND cs.branchId = :branchId AND cs.status = 'CLOSED' AND (cs.cashCollected = false OR cs.cashCollected IS NULL) ORDER BY cs.closedAt DESC, cs.openedAt DESC")
    List<CashSession> findUncollectedClosedSessions(@Param("tenantId") Long tenantId, @Param("branchId") Long branchId);

    List<CashSession> findByDrawerIdAndStatus(Long drawerId, CashSession.SessionStatus status);
}

