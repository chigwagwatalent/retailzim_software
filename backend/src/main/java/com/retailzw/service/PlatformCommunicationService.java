package com.retailzw.service;

import com.retailzw.model.Notification;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantAnnouncement;
import com.retailzw.model.User;
import com.retailzw.repository.NotificationRepository;
import com.retailzw.repository.TenantAnnouncementRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformCommunicationService {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final TenantAnnouncementRepository announcements;
    private final EmailService emailService;

    @Transactional
    public TenantAnnouncement sendAnnouncement(TenantAnnouncement.AudienceMode audienceMode,
                                               List<Long> tenantIds,
                                               List<Long> planIds,
                                               List<Tenant.TenantStatus> statuses,
                                               String subject,
                                               String message,
                                               boolean emailEnabled,
                                               String createdBy) {
        List<Tenant> recipients = resolveRecipients(audienceMode, tenantIds, planIds, statuses);
        int notificationCount = 0;
        int emailCount = 0;
        for (Tenant tenant : recipients) {
            List<User> tenantUsers = users.findByTenantIdAndIsActive(tenant.getId(), true);
            for (User user : tenantUsers) {
                notifications.save(Notification.builder()
                        .tenantId(tenant.getId())
                        .userId(user.getId())
                        .type(Notification.NotificationType.SYSTEM)
                        .title(subject)
                        .message(message)
                        .referenceType("ANNOUNCEMENT")
                        .isRead(false)
                        .build());
                notificationCount++;
            }
            if (emailEnabled && emailService.sendNotification(tenant.getEmail(), subject,
                    "<p>" + message.replace("\n", "<br>") + "</p>")) {
                emailCount++;
            }
        }
        return announcements.save(TenantAnnouncement.builder()
                .subject(subject)
                .message(message)
                .audienceMode(audienceMode)
                .audienceSummary(audienceSummary(audienceMode, recipients, planIds, statuses))
                .recipientCount(recipients.size())
                .emailEnabled(emailEnabled)
                .emailSentCount(emailCount)
                .notificationSentCount(notificationCount)
                .createdBy(createdBy)
                .build());
    }

    private List<Tenant> resolveRecipients(TenantAnnouncement.AudienceMode mode,
                                           List<Long> tenantIds,
                                           List<Long> planIds,
                                           List<Tenant.TenantStatus> statuses) {
        return switch (mode) {
            case SELECTED -> tenants.findAllById(safeLongs(tenantIds));
            case PACKAGE -> tenants.findAll().stream()
                    .filter(t -> t.getPlanId() != null && safeLongs(planIds).contains(t.getPlanId()))
                    .toList();
            case STATUS -> tenants.findAll().stream()
                    .filter(t -> safeStatuses(statuses).contains(t.getStatus()))
                    .toList();
            case ALL -> tenants.findAll();
        };
    }

    private Set<Long> safeLongs(List<Long> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private Set<Tenant.TenantStatus> safeStatuses(List<Tenant.TenantStatus> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private String audienceSummary(TenantAnnouncement.AudienceMode mode,
                                   List<Tenant> recipients,
                                   List<Long> planIds,
                                   List<Tenant.TenantStatus> statuses) {
        return switch (mode) {
            case SELECTED -> recipients.stream().map(Tenant::getCompanyName).reduce((a, b) -> a + ", " + b).orElse("No shops");
            case PACKAGE -> "Packages " + safeLongs(planIds);
            case STATUS -> "Statuses " + safeStatuses(statuses);
            case ALL -> "All tenant shops";
        };
    }
}
