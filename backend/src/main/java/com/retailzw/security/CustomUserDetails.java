package com.retailzw.security;

import com.retailzw.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Long tenantId;
    private final Long branchId;
    private final String roleName;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.tenantId = user.getTenantId();
        this.branchId = user.getBranchId();
        this.roleName = user.getRole().getName().name();
        this.active = Boolean.TRUE.equals(user.getIsActive());
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}

