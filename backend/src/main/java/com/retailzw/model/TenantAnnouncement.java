package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_announcements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private AudienceMode audienceMode;

    @Column(length = 500)
    private String audienceSummary;

    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Column(name = "email_enabled")
    @Builder.Default
    private Boolean emailEnabled = false;

    @Column(name = "email_sent_count")
    @Builder.Default
    private Integer emailSentCount = 0;

    @Column(name = "notification_sent_count")
    @Builder.Default
    private Integer notificationSentCount = 0;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AudienceMode {
        ALL, SELECTED, PACKAGE, STATUS
    }
}
