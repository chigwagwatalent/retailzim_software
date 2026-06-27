package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "billing_reminder_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_billing_reminder_delivery",
                columnNames = {"subscription_id", "reminder_key"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingReminderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "reminder_key", nullable = false, length = 40)
    private String reminderKey;

    @Column(nullable = false, length = 150)
    private String recipient;

    @Column(name = "checkout_reference", length = 80)
    private String checkoutReference;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
