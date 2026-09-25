package com.retailzw.service;

import com.retailzw.model.TenantChatMessage.SenderType;
import com.retailzw.repository.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Database-backed snapshots also see writes made on other application instances. */
@Service
@RequiredArgsConstructor
public class LiveSupportEvents {
    private final TenantChatMessageRepository messages;
    private final NotificationRepository notifications;
    private final TenantRepository tenants;
    private final UserRepository users;
    private record Scope(Long tenant, Long user) {}
    private final Map<SseEmitter, Scope> subscribers = new ConcurrentHashMap<>();

    public synchronized SseEmitter subscribe(Long tenant, Long user) {
        Scope scope = new Scope(tenant, user);
        if (subscribers.size() >= 1000 || subscribers.values().stream().filter(scope::equals).count() >= (tenant == null ? 100 : 8))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many live connections");
        SseEmitter emitter = new SseEmitter(45000L);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> { subscribers.remove(emitter); emitter.complete(); });
        emitter.onError(error -> subscribers.remove(emitter));
        subscribers.put(emitter, scope);
        return emitter;
    }

    @Scheduled(fixedDelay = 2500, scheduler = "supportScheduler")
    public void publish() {
        Map<Scope, Map<String, Long>> snapshots = new HashMap<>();
        subscribers.forEach((emitter, scope) -> {
            try {
                Map<String, Long> data = snapshots.computeIfAbsent(scope, key -> {
                    if (key.tenant != null && (!tenants.existsById(key.tenant) || !users.existsById(key.user)))
                        throw new IllegalStateException("Account no longer exists");
                    return Map.of("latest", messages.latestId(key.tenant),
                        "unread", key.tenant == null ? messages.countByReadByPlatformFalseAndSenderType(SenderType.SHOP)
                            : messages.countByTenantIdAndReadByShopFalseAndSenderType(key.tenant, SenderType.PLATFORM),
                        "notifications", key.user == null ? 0L : notifications.countByUserIdAndIsReadFalse(key.user));
                });
                emitter.send(SseEmitter.event().name("support").reconnectTime(5000).data(data));
            } catch (Exception failure) {
                subscribers.remove(emitter);
                emitter.completeWithError(failure);
            }
        });
    }

    @PreDestroy
    public void close() { subscribers.keySet().forEach(SseEmitter::complete); subscribers.clear(); }
}
