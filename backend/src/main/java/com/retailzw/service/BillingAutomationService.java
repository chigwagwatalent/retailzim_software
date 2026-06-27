package com.retailzw.service;

import com.retailzw.model.BillingReminderLog;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SmilePayCheckout;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.BillingReminderLogRepository;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingAutomationService {

    private final TenantSubscriptionRepository subscriptions;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final BillingReminderLogRepository reminderLogs;
    private final SmilePayCheckoutService checkoutService;
    private final EmailService emailService;

    @Value("${billing.reminder-days:14,7,3,1,0,-1,-3}")
    private List<Integer> reminderDays;

    @Value("${billing.grace-days:3}")
    private int graceDays;

    @Scheduled(cron = "${billing.reminder-cron:0 0 8 * * *}", zone = "${app.default-timezone:Africa/Harare}")
    @Transactional
    public void processBillingCycle() {
        LocalDate today = LocalDate.now();
        for (TenantSubscription subscription : subscriptions.findByStatusAndEndsAtIsNotNull(
                TenantSubscription.SubscriptionStatus.ACTIVE)) {
            try {
                long daysRemaining = ChronoUnit.DAYS.between(today, subscription.getEndsAt().toLocalDate());
                if (reminderDays.contains((int) daysRemaining)) {
                    sendScheduledReminder(subscription, daysRemaining);
                }
                if (daysRemaining < -Math.max(0, graceDays)) {
                    expireAndSuspend(subscription);
                }
            } catch (Exception ex) {
                log.warn("Billing automation failed subscription={} cause={}",
                        subscription.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public boolean sendManualReminder(Long tenantId) {
        TenantSubscription subscription = subscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tenant has no active subscription."));
        long daysRemaining = subscription.getEndsAt() == null
                ? 0
                : ChronoUnit.DAYS.between(LocalDate.now(), subscription.getEndsAt().toLocalDate());
        return sendReminder(subscription, daysRemaining, "MANUAL-" + UUID.randomUUID());
    }

    private void sendScheduledReminder(TenantSubscription subscription, long daysRemaining) {
        String key = daysRemaining >= 0 ? "DUE-" + daysRemaining : "OVERDUE-" + Math.abs(daysRemaining);
        if (reminderLogs.existsBySubscriptionIdAndReminderKey(subscription.getId(), key)) {
            return;
        }
        sendReminder(subscription, daysRemaining, key);
    }

    private boolean sendReminder(TenantSubscription subscription, long daysRemaining, String key) {
        Tenant tenant = tenants.findById(subscription.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(subscription.getPlanId()).orElseThrow();
        SmilePayCheckout checkout = checkoutService.createCheckout(tenant.getId());
        boolean sent = emailService.sendPaymentReminder(
                tenant,
                plan,
                subscription,
                checkoutService.checkoutUrl(checkout),
                daysRemaining);
        if (sent) {
            reminderLogs.save(BillingReminderLog.builder()
                    .tenantId(tenant.getId())
                    .subscriptionId(subscription.getId())
                    .reminderKey(key)
                    .recipient(tenant.getEmail())
                    .checkoutReference(checkout.getOrderReference())
                    .build());
        }
        return sent;
    }

    private void expireAndSuspend(TenantSubscription subscription) {
        subscription.setStatus(TenantSubscription.SubscriptionStatus.EXPIRED);
        subscriptions.save(subscription);
        Tenant tenant = tenants.findById(subscription.getTenantId()).orElseThrow();
        if (!Tenant.TenantStatus.CANCELLED.equals(tenant.getStatus())) {
            tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
            tenants.save(tenant);
        }
        log.info("Subscription expired tenant={} subscription={} endedAt={}",
                tenant.getId(), subscription.getId(), subscription.getEndsAt());
    }
}
