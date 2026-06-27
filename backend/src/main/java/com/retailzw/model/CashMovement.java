package com.retailzw.model;


import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String reason;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum MovementType {
        CASH_IN, CASH_OUT, SAFE_DROP, FLOAT_ADD, FLOAT_REMOVE
    }
}

