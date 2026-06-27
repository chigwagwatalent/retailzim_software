package com.retailzw.service;

import com.retailzw.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public Long tenantId() {
        return details().getTenantId();
    }

    public Long branchId() {
        return details().getBranchId();
    }

    public Long userId() {
        return details().getUserId();
    }

    public String roleName() {
        return details().getRoleName();
    }

    public boolean isHq() {
        return branchId() == null;
    }

    private CustomUserDetails details() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            throw new IllegalStateException("No shop user is signed in");
        }
        return user;
    }
}

