package com.retailzw.repository;

import com.retailzw.model.CashMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findBySessionId(Long sessionId);

    List<CashMovement> findByTenantIdAndSessionId(Long tenantId, Long sessionId);

    @Query("SELECT COALESCE(SUM(cm.amount), 0) FROM CashMovement cm WHERE cm.sessionId = :sessionId AND cm.currency = :currency AND cm.type = 'CASH_IN'")
    BigDecimal sumCashIn(@Param("sessionId") Long sessionId, @Param("currency") String currency);

    @Query("SELECT COALESCE(SUM(cm.amount), 0) FROM CashMovement cm WHERE cm.sessionId = :sessionId AND cm.currency = :currency AND cm.type = 'CASH_OUT'")
    BigDecimal sumCashOut(@Param("sessionId") Long sessionId, @Param("currency") String currency);
}

