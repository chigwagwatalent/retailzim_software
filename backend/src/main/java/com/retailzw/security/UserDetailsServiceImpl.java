package com.retailzw.security;

import com.retailzw.model.User;
import com.retailzw.model.SaasAdmin;
import com.retailzw.repository.SaasAdminRepository;
import com.retailzw.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final SaasAdminRepository saasAdminRepository;

    /**
     * Spring Security calls this during the standard auth filter.
     *
     * Because username is only unique per-tenant (uk_username_tenant), a plain
     * findByUsername() throws NonUniqueResultException when multiple tenants share
     * the same username.  We use the List-based query instead and pick the first
     * active user — correct for single-tenant setups and safe for multi-tenant.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<User> matches = userRepository.findAllByUsernameForMobileLogin(username);
        if (!matches.isEmpty()) {
            // Prefer an active user; fall back to first match if none are active
            User user = matches.stream()
                    .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                    .findFirst()
                    .orElse(matches.get(0));
            return new CustomUserDetails(user);
        }
        return loadSaasAdmin(username);
    }

    private UserDetails loadSaasAdmin(String username) {
        SaasAdmin admin = saasAdminRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return org.springframework.security.core.userdetails.User
                .withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(admin.getIsActive()))
                .authorities(new SimpleGrantedAuthority("ROLE_SAAS_ADMIN"))
                .build();
    }

    public UserDetails loadUserByUsernameAndTenant(String username, Long tenantId) {
        User user = userRepository.findByUsernameAndTenantId(username, tenantId)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username + " in tenant " + tenantId));
        return new CustomUserDetails(user);
    }
}

