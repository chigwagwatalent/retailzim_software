package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order_approvals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;
    @Column(columnDefinition = "TEXT")
    private String comments;
    @Column(name = "acted_by", nullable = false)
    private Long actedBy;
    @Column(name = "acted_at", updatable = false)
    private LocalDateTime actedAt;
    @PrePersist void create() { actedAt = LocalDateTime.now(); }
    public enum Action { SUBMITTED, APPROVED, REJECTED, CANCELLED }
}
