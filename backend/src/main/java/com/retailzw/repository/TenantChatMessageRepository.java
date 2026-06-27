package com.retailzw.repository;

import com.retailzw.model.TenantChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TenantChatMessageRepository extends JpaRepository<TenantChatMessage, Long> {
    List<TenantChatMessage> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    List<TenantChatMessage> findByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(Long tenantId, LocalDateTime createdAt);

    List<TenantChatMessage> findByTenantIdAndReadByPlatformFalseAndSenderTypeOrderByCreatedAtAsc(Long tenantId, TenantChatMessage.SenderType senderType);

    List<TenantChatMessage> findByTenantIdAndReadByShopFalseAndSenderTypeOrderByCreatedAtAsc(Long tenantId, TenantChatMessage.SenderType senderType);

    long countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType senderType);

    long countByTenantIdAndReadByShopFalseAndSenderType(Long tenantId, TenantChatMessage.SenderType senderType);
}
