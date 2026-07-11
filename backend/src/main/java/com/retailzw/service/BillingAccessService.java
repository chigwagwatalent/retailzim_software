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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingAccessService {

    private final TenantRepository tenants;
    private final TenantSubscriptionRepository subscriptions;

    @Value("${billing.grace-days:3}")
    private int graceDays;

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
}
