package com.retailzw.service;

import com.retailzw.model.TenantChatMessage;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportMessageService {
    private final TenantRepository tenants;
    private final TenantChatMessageRepository messages;

    @Transactional
    public TenantChatMessage send(Long tenantId, TenantChatMessage.SenderType sender, String name, String text, String requestId) {
        if (text == null || text.isBlank() || text.length() > 4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1–4000 characters");
        try { UUID.fromString(requestId); }
        catch (Exception invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message reference"); }
        // Serializes duplicate retries with deletion and other sends for this shop only.
        tenants.findLockedById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        return messages.findByTenantIdAndSenderTypeAndClientMessageId(tenantId, sender, requestId)
                .orElseGet(() -> messages.save(TenantChatMessage.builder().tenantId(tenantId).senderType(sender)
                    .senderName(name.length() > 120 ? name.substring(0,120) : name).message(text.strip()).clientMessageId(requestId).build()));
    }
}
