package com.retailzw.controller.web;

import com.retailzw.repository.TenantChatMessageRepository;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.LiveSupportEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class LiveSupportController {
    private final LiveSupportEvents events;
    private final CurrentUserService current;
    private final TenantChatMessageRepository messages;
    private final com.retailzw.service.SupportMessageService sending;
    private final com.retailzw.repository.UserRepository users;

    @PostMapping("/admin/support/{tenantId}/messages")
    public java.util.Map<String, Long> sendAdmin(@PathVariable Long tenantId, @RequestParam String message,
                                                @RequestParam String requestId, java.security.Principal principal) {
        return java.util.Map.of("id", sending.send(tenantId, com.retailzw.model.TenantChatMessage.SenderType.PLATFORM,
                principal.getName(), message, requestId).getId());
    }

    @PostMapping("/shop/support/messages")
    public java.util.Map<String, Long> sendShop(@RequestParam String message, @RequestParam String requestId) {
        var user = users.findById(current.userId()).orElseThrow();
        return java.util.Map.of("id", sending.send(current.tenantId(), com.retailzw.model.TenantChatMessage.SenderType.SHOP,
                user.getFirstName() + " " + user.getLastName(), message, requestId).getId());
    }

    @GetMapping(value = "/admin/live/events", produces = "text/event-stream")
    public SseEmitter adminEvents(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Accel-Buffering", "no");
        return events.subscribe(null, null);
    }

    @GetMapping(value = "/shop/live/events", produces = "text/event-stream")
    public SseEmitter shopEvents(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Accel-Buffering", "no");
        return events.subscribe(current.tenantId(), current.userId());
    }

    @GetMapping("/admin/support/inbox-status")
    public java.util.List<java.util.Map<String, Object>> inboxStatus(@RequestParam java.util.List<Long> ids) {
        if (ids.size() > 30) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        if (ids.isEmpty()) return java.util.List.of();
        var unread = new java.util.HashMap<Long, Long>();
        messages.unreadForTenants(ids).forEach(row -> unread.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return messages.latestForTenants(ids).stream().map(message -> java.util.Map.<String,Object>of(
                "tenant", message.getTenantId(), "message", message.getMessage(), "unread", unread.getOrDefault(message.getTenantId(), 0L))).toList();
    }

    @PostMapping("/admin/support/{tenantId}/read")
    public void adminRead(@PathVariable Long tenantId, @RequestParam long through) {
        messages.acknowledgePlatform(tenantId, Math.max(0, through));
    }

    @PostMapping("/shop/support/read")
    public void shopRead(@RequestParam long through) {
        messages.acknowledgeShop(current.tenantId(), Math.max(0, through));
    }
}
