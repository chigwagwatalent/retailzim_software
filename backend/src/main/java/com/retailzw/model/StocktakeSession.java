package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stocktake_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "session_number", unique = true, nullable = false, length = 50)
    private String sessionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StocktakeStatus status = StocktakeStatus.OPEN;

    @Column(name = "started_by", nullable = false)
    private Long startedBy;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "stocktakeSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StocktakeItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
    }

    public enum StocktakeStatus {
        OPEN, COUNTING, SUBMITTED, APPROVED, CANCELLED
    }
}

