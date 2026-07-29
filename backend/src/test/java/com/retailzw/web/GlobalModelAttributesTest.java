package com.retailzw.web;

import com.retailzw.controller.web.GlobalModelAttributes;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.NotificationRepository;
import com.retailzw.repository.SaasAdminRepository;
import com.retailzw.repository.UserRepository;
import com.retailzw.security.CustomUserDetails;
import com.retailzw.service.BillingAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalModelAttributesTest {

    private final UserRepository users = mock(UserRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final SaasAdminRepository admins = mock(SaasAdminRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final BillingAccessService billingAccess = mock(BillingAccessService.class);
    private final GlobalModelAttributes advice =
            new GlobalModelAttributes(users, branches, admins, notifications, billingAccess);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void showsTheRenewalToastOncePerShopAdminSession() {
        CustomUserDetails principal = signedInShopUser("SUPER_ADMIN");
        BillingAccessService.BillingRenewalNotice notice =
                new BillingAccessService.BillingRenewalNotice(
                        "Subscription renewal due soon",
                        "Your current billing period ends in 7 days.",
                        "warning",
                        7,
                        LocalDateTime.of(2026, 8, 5, 23, 59),
                        "44-2026-08-05");
        when(billingAccess.renewalNotice(9L)).thenReturn(Optional.of(notice));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/dashboard");
        ExtendedModelMap firstModel = new ExtendedModelMap();
        advice.addCurrentUser(firstModel, request);

        assertSame(notice, firstModel.get("billingRenewalNotice"));

        ExtendedModelMap nextModel = new ExtendedModelMap();
        advice.addCurrentUser(nextModel, request);
        assertFalse(nextModel.containsAttribute("billingRenewalNotice"));
        verify(billingAccess).renewalNotice(9L);
    }

    @Test
    void suppressesTheToastForAccountantsAndOnBillingPages() {
        signedInShopUser("ACCOUNTANT");
        MockHttpServletRequest accountantRequest =
                new MockHttpServletRequest("GET", "/shop/dashboard");
        advice.addCurrentUser(new ExtendedModelMap(), accountantRequest);
        verify(billingAccess, never()).renewalNotice(9L);

        clearInvocations(billingAccess);
        signedInShopUser("SUPER_ADMIN");
        MockHttpServletRequest billingRequest =
                new MockHttpServletRequest("GET", "/shop/billing");
        ExtendedModelMap billingModel = new ExtendedModelMap();
        advice.addCurrentUser(billingModel, billingRequest);

        assertTrue(!billingModel.containsAttribute("billingRenewalNotice"));
        verify(billingAccess, never()).renewalNotice(9L);
    }

    private CustomUserDetails signedInShopUser(String roleName) {
        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(7L);
        when(principal.getTenantId()).thenReturn(9L);
        when(principal.getRoleName()).thenReturn(roleName);

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
        return principal;
    }
}
