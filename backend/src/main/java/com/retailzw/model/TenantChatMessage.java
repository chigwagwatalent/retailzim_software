package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SenderType senderType;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "read_by_platform", nullable = false)
    private Boolean readByPlatform;

    @Column(name = "read_by_shop", nullable = false)
    private Boolean readByShop;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (readByPlatform == null) {
            readByPlatform = SenderType.PLATFORM.equals(senderType);
        }
        if (readByShop == null) {
            readByShop = SenderType.SHOP.equals(senderType);
        }
    }

    public enum SenderType {
        PLATFORM, SHOP
    }
}
