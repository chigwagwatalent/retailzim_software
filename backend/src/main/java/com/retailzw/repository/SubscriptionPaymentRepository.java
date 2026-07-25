package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SubscriptionPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    boolean existsByCheckoutId(Long checkoutId);

    Page<SubscriptionPayment> findByTenantIdOrderByConfirmedAtDesc(Long tenantId, Pageable pageable);

    @Query("""
            SELECT payment
            FROM SubscriptionPayment payment
            WHERE payment.confirmedAt >= :from AND payment.confirmedAt < :to
              AND (:currency IS NULL OR payment.currency = :currency)
              AND (:search IS NULL
                   OR LOWER(payment.orderReference) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(payment.providerReference, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR EXISTS (
                       SELECT tenant.id FROM Tenant tenant
                       WHERE tenant.id = payment.tenantId
                         AND (LOWER(tenant.companyName) LIKE LOWER(CONCAT('%', :search, '%'))
                              OR LOWER(tenant.tenantCode) LIKE LOWER(CONCAT('%', :search, '%')))
                   ))
            ORDER BY payment.confirmedAt DESC, payment.id DESC
            """)
    Page<SubscriptionPayment> searchConfirmed(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to,
                                              @Param("currency") CurrencyCode currency,
                                              @Param("search") String search,
                                              Pageable pageable);

    @Query("""
            SELECT payment.currency, COALESCE(SUM(payment.totalAmount), 0), COUNT(payment)
            FROM SubscriptionPayment payment
            WHERE payment.confirmedAt >= :from AND payment.confirmedAt < :to
            GROUP BY payment.currency
            """)
    List<Object[]> summarizeConfirmed(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT FUNCTION('DATE_FORMAT', payment.confirmedAt, '%Y-%m'),
                   payment.currency, COALESCE(SUM(payment.totalAmount), 0)
            FROM SubscriptionPayment payment
            WHERE payment.confirmedAt >= :from AND payment.confirmedAt < :to
            GROUP BY FUNCTION('DATE_FORMAT', payment.confirmedAt, '%Y-%m'), payment.currency
            ORDER BY FUNCTION('DATE_FORMAT', payment.confirmedAt, '%Y-%m')
            """)
    List<Object[]> monthlyTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
