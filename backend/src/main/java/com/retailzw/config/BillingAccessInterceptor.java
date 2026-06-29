package com.retailzw.config;

import com.retailzw.security.CustomUserDetails;
import com.retailzw.service.BillingAccessService;
import com.retailzw.service.PackageModuleAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

@Component
@RequiredArgsConstructor
public class BillingAccessInterceptor implements HandlerInterceptor {

    private final BillingAccessService billingAccess;
    private final PackageModuleAccessService packageModules;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails shopUser)) {
            return true;
        }
        String path = request.getServletPath();
        if (isBillingAllowed(path)) {
            return true;
        }
        BillingAccessService.BillingAccess access = billingAccess.evaluateAndUpdate(shopUser.getTenantId());
        if (access.locked()) {
            RequestContextUtils.getOutputFlashMap(request).put("message", access.message());
            response.sendRedirect(request.getContextPath() + "/shop/billing");
            return false;
        }
        if (path.startsWith("/api/")) {
            return enforceApiModule(response, shopUser, path);
        }
        if (path.startsWith("/shop/")) {
            return enforceShopModule(request, response, shopUser, path);
        }
        return true;
    }

    private boolean isBillingAllowed(String path) {
        return path.equals("/shop/billing")
                || path.equals("/shop/billing/pay")
                || path.startsWith("/api/auth/");
    }

    private boolean enforceApiModule(HttpServletResponse response,
                                     CustomUserDetails shopUser,
                                     String path) throws Exception {
        if (path.startsWith("/api/gas/")) {
            return packageModules.hasGas(shopUser.getTenantId())
                    || rejectApi(response, "Gas is not included in this subscription package.");
        }
        if (path.equals("/api/me") || path.equals("/api/branches")) {
            packageModules.syncAndGetEnabledModules(shopUser.getTenantId());
            return true;
        }
        return packageModules.hasRetailShop(shopUser.getTenantId())
                || rejectApi(response, "Retail Shop is not included in this subscription package.");
    }

    private boolean enforceShopModule(HttpServletRequest request,
                                      HttpServletResponse response,
                                      CustomUserDetails shopUser,
                                      String path) throws Exception {
        String module = firstShopSegment(path);
        if (module == null || module.isBlank()) {
            packageModules.syncAndGetEnabledModules(shopUser.getTenantId());
            return true;
        }
        if ("dashboard".equals(module)) {
            if (packageModules.hasRetailShop(shopUser.getTenantId())) {
                return true;
            }
            response.sendRedirect(request.getContextPath() + firstAvailableShopPath(shopUser.getTenantId()));
            return false;
        }
        if ("gas".equals(module)) {
            if (packageModules.hasGas(shopUser.getTenantId())) {
                return true;
            }
            RequestContextUtils.getOutputFlashMap(request)
                    .put("message", "Gas is not included in this subscription package.");
            response.sendRedirect(request.getContextPath() + firstAvailableShopPath(shopUser.getTenantId()));
            return false;
        }
        if (packageModules.retailWebModules().contains(module)
                && !packageModules.hasRetailShop(shopUser.getTenantId())) {
            RequestContextUtils.getOutputFlashMap(request)
                    .put("message", "Retail Shop is not included in this subscription package.");
            response.sendRedirect(request.getContextPath() + firstAvailableShopPath(shopUser.getTenantId()));
            return false;
        }
        packageModules.syncAndGetEnabledModules(shopUser.getTenantId());
        return true;
    }

    private boolean rejectApi(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
        return false;
    }

    private String firstShopSegment(String path) {
        String withoutPrefix = path.substring("/shop/".length());
        int slash = withoutPrefix.indexOf('/');
        return slash < 0 ? withoutPrefix : withoutPrefix.substring(0, slash);
    }

    private String firstAvailableShopPath(Long tenantId) {
        if (packageModules.hasGas(tenantId)) {
            return "/shop/gas";
        }
        if (packageModules.hasRetailShop(tenantId)) {
            return "/shop/dashboard";
        }
        return "/shop/billing";
    }
}
