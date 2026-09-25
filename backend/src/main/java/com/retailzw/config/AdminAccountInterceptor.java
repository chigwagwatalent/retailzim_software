package com.retailzw.config;

import com.retailzw.repository.SaasAdminRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rechecks active access for existing sessions, including notification/realtime requests. */
@Component
@RequiredArgsConstructor
public class AdminAccountInterceptor implements HandlerInterceptor {
    private final SaasAdminRepository admins;
    private static final String VERSION="retailzw.admin.passwordVersion";
    @Override
    public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_SAAS_ADMIN"))) return true;
        var account=admins.findByUsername(auth.getName()).orElse(null);
        var session=request.getSession(false);
        Object version=session==null ? null : session.getAttribute(VERSION);
        if(account==null || !Boolean.TRUE.equals(account.getIsActive()) || (version!=null && !version.equals(account.getPasswordHash()))) {
            if(session!=null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath()+"/auth/admin/login?expired=true");
            return false;
        }
        if(session!=null) session.setAttribute(VERSION,account.getPasswordHash());
        return true;
    }
}
