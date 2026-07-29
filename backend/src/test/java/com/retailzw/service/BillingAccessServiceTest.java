package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAccessServiceTest {

    @Mock
    private TenantRepository tenants;

    @Mock
    private TenantSubscriptionRepository subscriptions;

    @InjectMocks
    private BillingAccessService service;

    private final LocalDate today = LocalDate.of(2026, 7, 29);

    @BeforeEach
    void configureWindows() {
        ReflectionTestUtils.setField(service, "graceDays", 3);
        ReflectionTestUtils.setField(service, "inAppReminderDays", 14);
    }

    @Test
    void createsAnUpcomingNoticeInsideTheConfiguredWindow() {
        stubCurrentSubscription(today.plusDays(7).atTime(23, 59));

        BillingAccessService.BillingRenewalNotice notice =
                service.renewalNotice(9L, today).orElseThrow();

        assertEquals("warning", notice.severity());
        assertEquals(7, notice.daysRemaining());
        assertEquals("Subscription renewal due soon", notice.title());
        assertTrue(notice.message().contains("ends in 7 days"));
        assertTrue(notice.message().contains("05 Aug 2026"));
    }

    @Test
    void escalatesTodayAndGracePeriodNotices() {
        stubCurrentSubscription(today.atTime(23, 59));
        BillingAccessService.BillingRenewalNotice todayNotice =
                service.renewalNotice(9L, today).orElseThrow();
        assertEquals("urgent", todayNotice.severity());
        assertEquals("Your subscription expires today", todayNotice.title());

        stubCurrentSubscription(today.minusDays(2).atTime(23, 59));
        BillingAccessService.BillingRenewalNotice overdueNotice =
                service.renewalNotice(9L, today).orElseThrow();
        assertEquals("overdue", overdueNotice.severity());
        assertTrue(overdueNotice.message().contains("2 days ago"));
        assertTrue(overdueNotice.message().contains("grace period"));
    }

    @Test
    void suppressesNoticesOutsideTheReminderAndGraceWindows() {
        stubCurrentSubscription(today.plusDays(15).atTime(23, 59));
        assertTrue(service.renewalNotice(9L, today).isEmpty());

        stubCurrentSubscription(today.minusDays(4).atTime(23, 59));
        assertTrue(service.renewalNotice(9L, today).isEmpty());
    }

    private void stubCurrentSubscription(LocalDateTime endsAt) {
        TenantSubscription subscription = TenantSubscription.builder()
                .id(44L)
                .tenantId(9L)
                .planId(3L)
                .status(TenantSubscription.SubscriptionStatus.ACTIVE)
                .startsAt(today.minusMonths(1).atStartOfDay())
                .endsAt(endsAt)
                .currency(CurrencyCode.USD)
                .build();
        when(subscriptions
                .findFirstByTenantIdAndStatusInAndEndsAtIsNotNullOrderByEndsAtDesc(
                        eq(9L), any(Collection.class)))
                .thenReturn(Optional.of(subscription));
    }
}
