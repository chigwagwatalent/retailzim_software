package com.retailzw.service;

import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BillingAccessService {

    private final TenantRepository tenants;
    private final TenantSubscriptionRepository subscriptions;

    @Value("${billing.grace-days:3}")
    private int graceDays;

    @Value("${billing.in-app-reminder-days:14}")
    private int inAppReminderDays;

    @Transactional
    public BillingAccess evaluateAndUpdate(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        List<TenantSubscription> tenantSubscriptions = subscriptions.findByTenantId(tenantId);
        TenantSubscription current = tenantSubscriptions.stream()
                .filter(this::isCurrentStatus)
                .max(Comparator.comparing(TenantSubscription::getEndsAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        BillingAccess access = evaluate(tenant, current);
        if (access.locked() && current != null
                && TenantSubscription.SubscriptionStatus.ACTIVE.equals(current.getStatus())) {
            current.setStatus(TenantSubscription.SubscriptionStatus.EXPIRED);
            subscriptions.save(current);
        }
        if (access.locked() && !Tenant.TenantStatus.CANCELLED.equals(tenant.getStatus())
                && !Tenant.TenantStatus.SUSPENDED.equals(tenant.getStatus())) {
            tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
            tenants.save(tenant);
        }
        return access;
    }

    @Transactional(readOnly = true)
    public BillingAccess evaluate(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        TenantSubscription current = subscriptions.findByTenantId(tenantId).stream()
                .filter(this::isCurrentStatus)
                .max(Comparator.comparing(TenantSubscription::getEndsAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        return evaluate(tenant, current);
    }

    @Transactional(readOnly = true)
    public Optional<BillingRenewalNotice> renewalNotice(Long tenantId) {
        return renewalNotice(tenantId, LocalDate.now());
    }

    Optional<BillingRenewalNotice> renewalNotice(Long tenantId, LocalDate today) {
        return subscriptions
                .findFirstByTenantIdAndStatusInAndEndsAtIsNotNullOrderByEndsAtDesc(
                        tenantId,
                        List.of(TenantSubscription.SubscriptionStatus.ACTIVE,
                                TenantSubscription.SubscriptionStatus.TRIAL))
                .flatMap(subscription -> buildRenewalNotice(subscription, today));
    }

    private Optional<BillingRenewalNotice> buildRenewalNotice(
            TenantSubscription subscription,
            LocalDate today) {
        LocalDateTime endsAt = subscription.getEndsAt();
        if (endsAt == null) {
            return Optional.empty();
        }

        long daysRemaining = ChronoUnit.DAYS.between(today, endsAt.toLocalDate());
        int reminderWindow = Math.max(0, inAppReminderDays);
        int activeGraceDays = Math.max(0, graceDays);
        if (daysRemaining > reminderWindow || daysRemaining < -activeGraceDays) {
            return Optional.empty();
        }

        String formattedEnd = endsAt.toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
        String title;
        String message;
        String severity;
        if (daysRemaining < 0) {
            long overdueDays = Math.abs(daysRemaining);
            title = "Subscription payment overdue";
            message = "Your billing period ended " + overdueDays + " day"
                    + (overdueDays == 1 ? "" : "s")
                    + " ago on " + formattedEnd
                    + ". Renew now during the grace period to avoid losing access.";
            severity = "overdue";
        } else if (daysRemaining == 0) {
            title = "Your subscription expires today";
            message = "Your current billing period ends today, " + formattedEnd
                    + ". Renew now to keep every module available.";
            severity = "urgent";
        } else if (daysRemaining == 1) {
            title = "Your subscription expires tomorrow";
            message = "Your current billing period ends tomorrow, " + formattedEnd
                    + ". Renew now to avoid any interruption.";
            severity = "urgent";
        } else {
            title = "Subscription renewal due soon";
            message = "Your current billing period ends in " + daysRemaining
                    + " days on " + formattedEnd + ".";
            severity = daysRemaining <= 3 ? "urgent" : "warning";
        }

        String subscriptionKey = subscription.getId() == null
                ? String.valueOf(subscription.getTenantId())
                : String.valueOf(subscription.getId());
        return Optional.of(new BillingRenewalNotice(
                title,
                message,
                severity,
                daysRemaining,
                endsAt,
                subscriptionKey + "-" + endsAt.toLocalDate()));
    }

    private BillingAccess evaluate(Tenant tenant, TenantSubscription subscription) {
        if (Tenant.TenantStatus.CANCELLED.equals(tenant.getStatus())) {
            return new BillingAccess(true, "This account is cancelled. Contact support to reactivate it.", 0, null);
        }
        if (Tenant.TenantStatus.SUSPENDED.equals(tenant.getStatus())) {
            return new BillingAccess(true, "This account is suspended. Pay or renew to unlock all modules.", 0,
                    subscription == null ? null : subscription.getEndsAt());
        }
        if (subscription == null) {
            return new BillingAccess(true, "Your account needs an active subscription before modules can open.", 0, null);
        }
        LocalDateTime endsAt = subscription.getEndsAt();
        if (endsAt == null) {
            return new BillingAccess(false, null, 0, null);
        }
        LocalDate dueDate = endsAt.toLocalDate();
        LocalDate lockDate = dueDate.plusDays(Math.max(0, graceDays));
        LocalDate today = LocalDate.now();
        if (today.isAfter(lockDate)) {
            long overdueDays = ChronoUnit.DAYS.between(dueDate, today);
            return new BillingAccess(
                    true,
                    "Your subscription expired " + overdueDays + " day" + (overdueDays == 1 ? "" : "s")
                            + " ago. Pay or renew to unlock all modules.",
                    overdueDays,
                    endsAt);
        }
        return new BillingAccess(false, null, Math.max(0, ChronoUnit.DAYS.between(dueDate, today)), endsAt);
    }

    private boolean isCurrentStatus(TenantSubscription subscription) {
        return TenantSubscription.SubscriptionStatus.ACTIVE.equals(subscription.getStatus())
                || TenantSubscription.SubscriptionStatus.TRIAL.equals(subscription.getStatus());
    }

    public record BillingAccess(boolean locked, String message, long overdueDays, LocalDateTime endsAt) {
    }

    public record BillingRenewalNotice(
            String title,
            String message,
            String severity,
            long daysRemaining,
            LocalDateTime endsAt,
            String reminderKey) {
    }
}
