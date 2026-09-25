package com.retailzw.config;

import com.retailzw.model.SaasAdmin;
import com.retailzw.repository.SaasAdminRepository;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AdminAccountInterceptorTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void passwordChangeInvalidatesAnExistingSession() throws Exception {
        var repo=mock(SaasAdminRepository.class);var guard=new AdminAccountInterceptor(repo);
        var account=new SaasAdmin();account.setIsActive(true);account.setPasswordHash("original");when(repo.findByUsername("admin")).thenReturn(Optional.of(account));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",AuthorityUtils.createAuthorityList("ROLE_SAAS_ADMIN")));
        var request=new MockHttpServletRequest();request.getSession();var response=new MockHttpServletResponse();
        assertThat(guard.preHandle(request,response,new Object())).isTrue();
        account.setPasswordHash("changed");
        assertThat(guard.preHandle(request,response,new Object())).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/admin/login?expired=true");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void inactiveAccountIsRejectedImmediately() throws Exception {
        var repo=mock(SaasAdminRepository.class);var account=new SaasAdmin();account.setIsActive(false);when(repo.findByUsername("admin")).thenReturn(Optional.of(account));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",AuthorityUtils.createAuthorityList("ROLE_SAAS_ADMIN")));
        assertThat(new AdminAccountInterceptor(repo).preHandle(new MockHttpServletRequest(),new MockHttpServletResponse(),new Object())).isFalse();
    }
}
