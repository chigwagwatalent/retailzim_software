package com.retailzw.config;

import com.retailzw.enums.BusinessModule;
import com.retailzw.security.CustomUserDetails;
import com.retailzw.service.BillingAccessService;
import com.retailzw.service.PackageModuleAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingAccessInterceptorTest {

    private static final Long TENANT_ID = 41L;
    private PackageModuleAccessService modules;
    private BillingAccessInterceptor interceptor;

    @BeforeEach
    void setUp() {
        BillingAccessService billing = mock(BillingAccessService.class);
        modules = mock(PackageModuleAccessService.class);
        interceptor = new BillingAccessInterceptor(billing, modules);
        when(billing.evaluateAndUpdate(TENANT_ID))
                .thenReturn(new BillingAccessService.BillingAccess(false, null, 0, null));

        CustomUserDetails principal = mock(CustomUserDetails.class);
        when(principal.getTenantId()).thenReturn(TENANT_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void gasOnlyTenantCanOpenGasAndCommonManagementPages() throws Exception {
        when(modules.hasGas(TENANT_ID)).thenReturn(true);
        when(modules.syncAndGetEnabledModules(TENANT_ID)).thenReturn(List.of(BusinessModule.GAS_MODULE));

        assertThat(preHandle("/shop/gas").allowed()).isTrue();
        assertThat(preHandle("/shop/branches").allowed()).isTrue();
        assertThat(preHandle("/shop/users").allowed()).isTrue();
    }

    @Test
    void gasOnlyTenantIsRedirectedAwayFromRetailPages() throws Exception {
        when(modules.hasRetailShop(TENANT_ID)).thenReturn(false);
        when(modules.hasGas(TENANT_ID)).thenReturn(true);
        when(modules.retailWebModules()).thenReturn(java.util.Set.of("products"));

        Result result = preHandle("/shop/products");

        assertThat(result.allowed()).isFalse();
        assertThat(result.response().getRedirectedUrl()).isEqualTo("/shop/gas");
    }

    @Test
    void gasOnlyTenantGetsForbiddenForRetailApi() throws Exception {
        when(modules.hasRetailShop(TENANT_ID)).thenReturn(false);

        Result result = preHandle("/api/products");

        assertThat(result.allowed()).isFalse();
        assertThat(result.response().getStatus()).isEqualTo(403);
        assertThat(result.response().getContentAsString()).contains("Retail Shop is not included");
    }

    private Result preHandle(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new Result(interceptor.preHandle(request, response, new Object()), response);
    }

    private record Result(boolean allowed, MockHttpServletResponse response) {
    }
}
