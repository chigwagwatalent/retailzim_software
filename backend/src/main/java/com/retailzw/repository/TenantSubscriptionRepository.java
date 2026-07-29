package com.retailzw.repository;

import com.retailzw.model.TenantSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, Long> {

    List<TenantSubscription> findByTenantId(Long tenantId);

    Optional<TenantSubscription> findByTenantIdAndStatus(Long tenantId, TenantSubscription.SubscriptionStatus status);

    Optional<TenantSubscription> findFirstByTenantIdAndStatusInAndEndsAtIsNotNullOrderByEndsAtDesc(
            Long tenantId,
            Collection<TenantSubscription.SubscriptionStatus> statuses);

    List<TenantSubscription> findByStatusAndEndsAtIsNotNull(TenantSubscription.SubscriptionStatus status);

    List<TenantSubscription> findByStatusAndEndsAtBetween(
            TenantSubscription.SubscriptionStatus status,
            LocalDateTime from,
            LocalDateTime to);

    Page<TenantSubscription> findAll(Pageable pageable);

    @Query("SELECT COALESCE(SUM(ts.amountPaid), 0) FROM TenantSubscription ts WHERE ts.status = 'ACTIVE' AND ts.currency = 'USD'")
    BigDecimal calculateMrrUsd();

    @Query("SELECT ts.planId, COUNT(ts) FROM TenantSubscription ts WHERE ts.status = 'ACTIVE' GROUP BY ts.planId")
    List<Object[]> countByPlan();

    @Query("SELECT COUNT(ts) FROM TenantSubscription ts WHERE ts.status = :status AND ts.currency = :currency")
    long countByStatusAndCurrency(@Param("status") TenantSubscription.SubscriptionStatus status,
                                   @Param("currency") String currency);
}

