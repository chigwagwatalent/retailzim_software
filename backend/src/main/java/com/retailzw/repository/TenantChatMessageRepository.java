package com.retailzw.repository;

import com.retailzw.model.TenantChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TenantChatMessageRepository extends JpaRepository<TenantChatMessage, Long> {
    java.util.Optional<TenantChatMessage> findByTenantIdAndSenderTypeAndClientMessageId(Long tenantId, TenantChatMessage.SenderType senderType, String clientMessageId);
    @org.springframework.data.jpa.repository.Query("select coalesce(max(m.id), 0) from TenantChatMessage m where (:tenantId is null or m.tenantId = :tenantId)")
    long latestId(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);

    @org.springframework.data.jpa.repository.Query("select m from TenantChatMessage m where m.tenantId in :ids and m.id = (select max(n.id) from TenantChatMessage n where n.tenantId = m.tenantId)")
    List<TenantChatMessage> latestForTenants(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    @org.springframework.data.jpa.repository.Query("select m.tenantId, count(m) from TenantChatMessage m where m.tenantId in :ids and m.readByPlatform = false and m.senderType = 'SHOP' group by m.tenantId")
    List<Object[]> unreadForTenants(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update TenantChatMessage m set m.readByPlatform = true where m.tenantId = :tenantId and m.id <= :through and m.senderType = 'SHOP' and m.readByPlatform = false")
    int acknowledgePlatform(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, @org.springframework.data.repository.query.Param("through") long through);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update TenantChatMessage m set m.readByShop = true where m.tenantId = :tenantId and m.id <= :through and m.senderType = 'PLATFORM' and m.readByShop = false")
    int acknowledgeShop(@org.springframework.data.repository.query.Param("tenantId") Long tenantId, @org.springframework.data.repository.query.Param("through") long through);
    List<TenantChatMessage> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    List<TenantChatMessage> findByTenantIdAndCreatedAtAfterOrderByCreatedAtAsc(Long tenantId, LocalDateTime createdAt);

    List<TenantChatMessage> findByTenantIdAndReadByPlatformFalseAndSenderTypeOrderByCreatedAtAsc(Long tenantId, TenantChatMessage.SenderType senderType);

    List<TenantChatMessage> findByTenantIdAndReadByShopFalseAndSenderTypeOrderByCreatedAtAsc(Long tenantId, TenantChatMessage.SenderType senderType);

    long countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType senderType);

    long countByTenantIdAndReadByShopFalseAndSenderType(Long tenantId, TenantChatMessage.SenderType senderType);
}
