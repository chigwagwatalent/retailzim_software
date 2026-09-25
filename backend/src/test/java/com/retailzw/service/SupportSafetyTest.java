package com.retailzw.service;

import com.retailzw.model.*;
import com.retailzw.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportSafetyTest {
    TenantRepository tenants = mock(TenantRepository.class);
    TenantChatMessageRepository messages = mock(TenantChatMessageRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test void invalidMessagesNeverTouchDatabase() {
        var service = new SupportMessageService(tenants, messages);
        for (String text : List.of(" ", "x".repeat(4001)))
            assertThatThrownBy(() -> service.send(7L, TenantChatMessage.SenderType.SHOP, "User", text, UUID.randomUUID().toString()))
                    .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(tenants, messages);
    }

    @Test void retryReturnsExistingMessageWithoutInserting() {
        var service = new SupportMessageService(tenants, messages);
        String request = UUID.randomUUID().toString();
        TenantChatMessage saved = TenantChatMessage.builder().id(90L).build();
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(new Tenant()));
        when(messages.findByTenantIdAndSenderTypeAndClientMessageId(7L, TenantChatMessage.SenderType.SHOP, request)).thenReturn(Optional.of(saved));
        assertThat(service.send(7L, TenantChatMessage.SenderType.SHOP, "User", "hello", request)).isSameAs(saved);
        verify(messages, never()).save(any());
    }

    @Test void deletionRejectsActiveShopAndWrongConfirmation() {
        Tenant tenant = new Tenant(); tenant.setStatus(Tenant.TenantStatus.ACTIVE); tenant.setTenantCode("SHOP-7");
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        var service = new TenantDeletionService(tenants, jdbc);
        assertThatThrownBy(() -> service.delete(7L,"DELETE SHOP-7",true,"admin")).isInstanceOf(IllegalArgumentException.class);
        tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
        assertThatThrownBy(() -> service.delete(7L,"DELETE SHOP-8",true,"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delete(7L,"DELETE SHOP-7",false,"admin")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void approvedDeletionBindsOnlyRequestedTenantAndDeletesTenantLast() {
        Tenant tenant = new Tenant(); tenant.setStatus(Tenant.TenantStatus.SUSPENDED); tenant.setTenantCode("SHOP-7");
        when(tenants.findLockedById(7L)).thenReturn(Optional.of(tenant));
        when(jdbc.update("DELETE FROM tenants WHERE id = ?", 7L)).thenReturn(1);
        new TenantDeletionService(tenants,jdbc).delete(7L,"DELETE SHOP-7",true,"admin");
        var calls = mockingDetails(jdbc).getInvocations();
        // Ten child-table purges from the retail/LPG platform plus three
        // fuel-domain child purges, followed by every tenant-owned table and
        // finally the tenant row itself.
        assertThat(calls).hasSize(TenantDeletionService.OWNED_TABLES.size()+13);
        calls.forEach(call -> {
            assertThat(call.getArgument(0).toString()).contains("?").doesNotContain("FOREIGN_KEY_CHECKS");
            assertThat((Object)call.getArgument(1)).isEqualTo(7L);
        });
        assertThat(calls.stream().reduce((a,b)->b).orElseThrow().getArgument(0).toString()).isEqualTo("DELETE FROM tenants WHERE id = ?");
    }

    @Test void everyTenantOwnedMigrationTableHasExplicitDeletionCoverage() throws Exception {
        Pattern create = Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?(\\w+)\\s*\\((.*?)(?:ENGINE=|;)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path path : files.toList()) {
                var matcher = create.matcher(Files.readString(path));
                while (matcher.find()) if (Pattern.compile("\\btenant_id\\b").matcher(matcher.group(2)).find())
                    assertThat(TenantDeletionService.OWNED_TABLES).as(path + ": " + matcher.group(1)).contains(matcher.group(1));
            }
        }
    }
}
