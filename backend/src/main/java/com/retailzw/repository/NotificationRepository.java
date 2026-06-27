package com.retailzw.repository;

import com.retailzw.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    void markAllReadByUserId(@Param("userId") Long userId);

    List<Notification> findByTenantIdAndUserIdOrderByCreatedAtDesc(Long tenantId, Long userId, Pageable pageable);
}

