package com.retailzw.repository;

import com.retailzw.model.PaymentNotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentNotificationOutboxRepository
        extends JpaRepository<PaymentNotificationOutbox, Long> {

    boolean existsByCheckoutId(Long checkoutId);

    @Query("""
            SELECT notification
            FROM PaymentNotificationOutbox notification
            WHERE notification.status <> :sentStatus
              AND notification.nextAttemptAt <= :now
              AND (notification.claimedUntil IS NULL OR notification.claimedUntil <= :now)
            ORDER BY notification.nextAttemptAt ASC
            """)
    List<PaymentNotificationOutbox> findDue(
            @Param("sentStatus") PaymentNotificationOutbox.DeliveryStatus sentStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            UPDATE PaymentNotificationOutbox notification
            SET notification.status = :processingStatus,
                notification.claimedUntil = :claimedUntil,
                notification.attempts = notification.attempts + 1
            WHERE notification.id = :id
              AND notification.status <> :sentStatus
              AND (notification.claimedUntil IS NULL OR notification.claimedUntil <= :now)
            """)
    int claim(
            @Param("id") Long id,
            @Param("processingStatus") PaymentNotificationOutbox.DeliveryStatus processingStatus,
            @Param("sentStatus") PaymentNotificationOutbox.DeliveryStatus sentStatus,
            @Param("now") LocalDateTime now,
            @Param("claimedUntil") LocalDateTime claimedUntil);

    @Transactional
    @Modifying
    @Query("""
            UPDATE PaymentNotificationOutbox notification
            SET notification.status = :sentStatus,
                notification.sentAt = :sentAt,
                notification.claimedUntil = NULL,
                notification.lastError = NULL
            WHERE notification.id = :id
            """)
    int markSent(
            @Param("id") Long id,
            @Param("sentStatus") PaymentNotificationOutbox.DeliveryStatus sentStatus,
            @Param("sentAt") LocalDateTime sentAt);

    @Transactional
    @Modifying
    @Query("""
            UPDATE PaymentNotificationOutbox notification
            SET notification.status = :failedStatus,
                notification.nextAttemptAt = :nextAttemptAt,
                notification.claimedUntil = NULL,
                notification.lastError = :lastError
            WHERE notification.id = :id
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("failedStatus") PaymentNotificationOutbox.DeliveryStatus failedStatus,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError);
}
