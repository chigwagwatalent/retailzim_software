package com.retailzw.repository;

import com.retailzw.model.SmilePayCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface SmilePayCheckoutRepository extends JpaRepository<SmilePayCheckout, Long> {
    Optional<SmilePayCheckout> findByOrderReference(String orderReference);

    Optional<SmilePayCheckout> findByAccessToken(String accessToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SmilePayCheckout> findLockedByOrderReference(String orderReference);

    Optional<SmilePayCheckout> findFirstByTenantIdAndPlanIdAndCheckoutPurposeAndBillingMonthsAndStatusInOrderByCreatedAtDesc(
            Long tenantId,
            Long planId,
            SmilePayCheckout.CheckoutPurpose checkoutPurpose,
            Integer billingMonths,
            Collection<SmilePayCheckout.CheckoutStatus> statuses);

    @Query("""
            SELECT checkout
            FROM SmilePayCheckout checkout
            WHERE checkout.status IN :statuses
              AND checkout.initiatedAt IS NOT NULL
              AND (checkout.nextCheckAt IS NULL OR checkout.nextCheckAt <= :now)
            ORDER BY checkout.initiatedAt ASC
            """)
    List<SmilePayCheckout> findDueForReconciliation(
            @Param("statuses") Collection<SmilePayCheckout.CheckoutStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SmilePayCheckout checkout
            SET checkout.nextCheckAt = :claimedUntil
            WHERE checkout.id = :id
              AND (checkout.nextCheckAt IS NULL OR checkout.nextCheckAt <= :now)
            """)
    int claimForReconciliation(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("claimedUntil") LocalDateTime claimedUntil);

    @Transactional
    @Modifying
    @Query("""
            UPDATE SmilePayCheckout checkout
            SET checkout.invoiceSentAt = :sentAt
            WHERE checkout.id = :id
              AND checkout.invoiceSentAt IS NULL
            """)
    int markInvoiceSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);
}
