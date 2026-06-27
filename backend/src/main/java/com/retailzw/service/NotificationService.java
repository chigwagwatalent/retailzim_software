package com.retailzw.service;

import com.retailzw.model.*;
import com.retailzw.enums.UserRole;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final BranchRepository branches;
    private final EmailService emailService;

    @Transactional
    public Notification createNotification(Long tenantId, Long userId, String type,
                                           String title, String message,
                                           String refType, Long refId) {
        Notification.NotificationType notifType;
        try {
            notifType = Notification.NotificationType.valueOf(type);
        } catch (IllegalArgumentException e) {
            notifType = Notification.NotificationType.GENERAL;
        }
        Notification n = Notification.builder()
                .tenantId(tenantId)
                .userId(userId)
                .type(notifType)
                .title(title)
                .message(message)
                .referenceType(refType)
                .referenceId(refId)
                .isRead(false)
                .build();
        Notification saved = notifications.save(n);
        users.findById(userId).ifPresent(user -> emailService.sendUserNotification(user, title, message));
        return saved;
    }

    @Transactional
    public void notifyLowStock(Long tenantId, Long branchId, Product product, java.math.BigDecimal currentQty) {
        findBranchManager(tenantId, branchId).ifPresent(manager ->
                createNotification(tenantId, manager.getId(),
                        Notification.NotificationType.LOW_STOCK.name(),
                        "Low Stock Alert: " + product.getName(),
                        "Product [" + product.getSku() + "] " + product.getName()
                                + " is at " + currentQty + " units — below reorder level of "
                                + product.getReorderLevel() + ".",
                        "PRODUCT", product.getId())
        );
    }

    @Transactional
    public void notifyVoid(Long tenantId, Sale sale, Long managerId) {
        createNotification(tenantId, managerId,
                Notification.NotificationType.VOID_SALE.name(),
                "Sale Voided: " + sale.getReceiptNumber(),
                "Sale " + sale.getReceiptNumber() + " (total " + sale.getGrandTotal()
                        + " " + sale.getCurrency() + ") was voided.",
                "SALE", sale.getId());
    }

    @Transactional
    public void notifyCashVariance(Long tenantId, CashSession session) {
        findBranchManager(tenantId, session.getBranchId()).ifPresent(manager ->
                createNotification(tenantId, manager.getId(),
                        Notification.NotificationType.CASH_VARIANCE.name(),
                        "Cash Variance Detected",
                        "Cash session closed with variance USD "
                                + (session.getVarianceUsd() != null ? session.getVarianceUsd() : "0")
                                + " / ZWG "
                                + (session.getVarianceZwg() != null ? session.getVarianceZwg() : "0"),
                        "CASH_SESSION", session.getId())
        );
    }

    @Transactional
    public void notifyPOApprovalNeeded(Long tenantId, PurchaseOrder po) {
        findBranchManager(tenantId, po.getBranchId()).ifPresent(manager ->
                createNotification(tenantId, manager.getId(),
                        Notification.NotificationType.PO_APPROVAL_NEEDED.name(),
                        "Purchase Order Awaiting Approval",
                        "PO " + po.getPoNumber() + " requires your approval. Total: "
                                + po.getTotalUsd() + " USD.",
                        "PURCHASE_ORDER", po.getId())
        );
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notifications.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(Long userId) {
        return notifications.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllReadByUserId(userId);
    }

    @Transactional
    public void markOneRead(Long notificationId, Long userId) {
        notifications.findById(notificationId).ifPresent(n -> {
            if (n.getUserId().equals(userId) && !Boolean.TRUE.equals(n.getIsRead())) {
                n.setIsRead(true);
                n.setReadAt(LocalDateTime.now());
                notifications.save(n);
            }
        });
    }

    private java.util.Optional<User> findBranchManager(Long tenantId, Long branchId) {
        return users.findByTenantId(tenantId).stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive())
                        && u.getBranchId() != null
                        && u.getBranchId().equals(branchId)
                        && u.getRole() != null
                        && UserRole.BRANCH_MANAGER.equals(u.getRole().getName()))
                .findFirst();
    }
}
