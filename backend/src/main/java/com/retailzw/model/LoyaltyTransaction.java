package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoyaltyTransactionType type;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "points_balance_after")
    private Integer pointsBalanceAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(length = 255)
    private String description;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum LoyaltyTransactionType {
        EARN, REDEEM, ADJUST, EXPIRE, BONUS, REVERSAL
    }
}

