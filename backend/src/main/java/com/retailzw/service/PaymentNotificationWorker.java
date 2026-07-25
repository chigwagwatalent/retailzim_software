package com.retailzw.service;

import com.retailzw.model.PaymentNotificationOutbox;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SmilePayCheckout;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.PaymentNotificationOutboxRepository;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.SmilePayCheckoutRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationWorker {

    private final PaymentNotificationOutboxRepository notifications;
    private final SmilePayCheckoutRepository checkouts;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final TenantSubscriptionRepository subscriptions;
    private final EmailService emailService;

    @Scheduled(
            fixedDelayString = "${billing.notification-delay-ms:10000}",
            initialDelayString = "${billing.notification-initial-delay-ms:15000}")
    public void sendDueNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentNotificationOutbox> due = notifications.findDue(
                PaymentNotificationOutbox.DeliveryStatus.SENT,
                now,
                PageRequest.of(0, 50));
        for (PaymentNotificationOutbox notification : due) {
            if (notifications.claim(
                    notification.getId(),
                    PaymentNotificationOutbox.DeliveryStatus.PROCESSING,
                    PaymentNotificationOutbox.DeliveryStatus.SENT,
                    now,
                    now.plusMinutes(5)) == 0) {
                continue;
            }
            deliver(notification.getId());
        }
    }

    private void deliver(Long notificationId) {
        PaymentNotificationOutbox notification = notifications.findById(notificationId)
                .orElse(null);
        if (notification == null) {
            return;
        }
        try {
            SmilePayCheckout checkout = checkouts.findById(notification.getCheckoutId())
                    .orElseThrow(() -> new IllegalStateException("Checkout no longer exists."));
            Tenant tenant = tenants.findById(checkout.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Tenant no longer exists."));
            SaasPlan plan = plans.findById(checkout.getPlanId())
                    .orElseThrow(() -> new IllegalStateException("Plan no longer exists."));
            TenantSubscription subscription = subscriptions.findById(notification.getSubscriptionId())
                    .orElseThrow(() -> new IllegalStateException("Subscription no longer exists."));

            if (!emailService.sendPaymentConfirmation(tenant, plan, subscription, checkout)) {
                throw new IllegalStateException("Email provider did not accept the confirmation.");
            }

            LocalDateTime sentAt = LocalDateTime.now();
            notifications.markSent(
                    notificationId,
                    PaymentNotificationOutbox.DeliveryStatus.SENT,
                    sentAt);
            checkouts.markInvoiceSent(checkout.getId(), sentAt);
        } catch (RuntimeException ex) {
            int attempts = Math.max(1, notification.getAttempts());
            long delayMinutes = Math.min(360L, 1L << Math.min(attempts, 8));
            String message = ex.getMessage() == null
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            if (message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            notifications.markFailed(
                    notificationId,
                    PaymentNotificationOutbox.DeliveryStatus.FAILED,
                    LocalDateTime.now().plusMinutes(delayMinutes),
                    message);
            log.warn("Payment confirmation deferred checkoutId={} attempt={} reason={}",
                    notification.getCheckoutId(), attempts, message);
        }
    }
}
