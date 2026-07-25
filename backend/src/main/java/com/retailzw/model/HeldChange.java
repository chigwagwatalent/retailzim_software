package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "held_change")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeldChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "cash_session_id")
    private Long cashSessionId;
    @Column(name = "sale_id")
    private Long saleId;
    @Column(name = "gas_sale_id")
    private Long gasSaleId;
    @Column(name = "gas_shift_id")
    private Long gasShiftId;
    @Column(name = "reference_number", nullable = false, length = 60)
    private String referenceNumber;
    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;
    @Column(nullable = false, length = 40)
    private String phone;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;
    @Column(name = "offline_reference", length = 100)
    private String offlineReference;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "collected_by")
    private Long collectedBy;
    @Column(name = "collected_cash_session_id")
    private Long collectedCashSessionId;
    @Column(name = "collected_at")
    private LocalDateTime collectedAt;
    @Column(name = "cancelled_by")
    private Long cancelledBy;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Status {
        OPEN, COLLECTED, CANCELLED
    }
}
