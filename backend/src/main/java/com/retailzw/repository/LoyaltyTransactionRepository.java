package com.retailzw.repository;

import com.retailzw.model.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(Long tenantId, Long customerId);

    Page<LoyaltyTransaction> findByTenantIdAndCustomerId(Long tenantId, Long customerId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(lt.points), 0) FROM LoyaltyTransaction lt " +
           "WHERE lt.tenantId = :tenantId AND lt.customerId = :customerId " +
           "AND lt.type IN ('EARN', 'BONUS', 'ADJUST')")
    Integer sumEarnedPoints(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId);

    List<LoyaltyTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}

