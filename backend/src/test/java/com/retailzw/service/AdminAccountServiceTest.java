package com.retailzw.service;

import com.retailzw.model.SaasAdmin;
import com.retailzw.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {
    @Mock SaasAdminRepository admins;
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @InjectMocks AdminAccountService service;
    SaasAdmin admin(long id,String name) { var a=new SaasAdmin();a.setId(id);a.setUsername(name);a.setIsActive(true);return a; }
    @Test void disabledActorCannotDeactivateAnotherAdministrator() {
        var actor=admin(1,"actor");actor.setIsActive(false);
        when(admins.findByUsername("actor")).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.status("actor",2L,false)).isInstanceOf(AccessDeniedException.class);
        verify(admins).lockAccounts();verify(admins,never()).save(any());
    }
    @Test void ownAccountCannotBeDisabled() {
        var actor=admin(1,"actor");when(admins.findByUsername("actor")).thenReturn(Optional.of(actor));when(admins.findById(1L)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.status("actor",1L,false)).isInstanceOf(IllegalArgumentException.class);
        verify(admins,never()).save(any());
    }
    @Test void rejectsIncorrectCurrentPassword() {
        var actor=admin(1,"actor");actor.setPasswordHash("hash");when(admins.findByUsername("actor")).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service.password("actor","incorrect","a-long-password","a-long-password")).isInstanceOf(IllegalArgumentException.class);
        verify(admins,never()).save(any());
    }
    @Test void hashesNewAdministratorPassword() {
        when(encoder.encode("a-long-password")).thenReturn("encoded");
        service.create("newadmin","First","Last","first@example.test","a-long-password");
        ArgumentCaptor<SaasAdmin> saved=ArgumentCaptor.forClass(SaasAdmin.class);verify(admins).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");assertThat(saved.getValue().getIsActive()).isTrue();
    }
}
