package com.retailzw.repository;

import com.retailzw.model.BillingReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingReminderLogRepository extends JpaRepository<BillingReminderLog, Long> {
    boolean existsBySubscriptionIdAndReminderKey(Long subscriptionId, String reminderKey);
}
