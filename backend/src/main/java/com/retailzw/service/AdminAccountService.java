package com.retailzw.service;

import com.retailzw.model.SaasAdmin;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('SAAS_ADMIN')")
public class AdminAccountService {
    private final SaasAdminRepository admins;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Transactional
    public void profile(String actor, String first, String last, String email) {
        SaasAdmin account=admins.findByUsername(actor).orElseThrow();
        names(account,first,last,email);
        if(admins.findByEmailIgnoreCase(account.getEmail()).filter(a -> !a.getId().equals(account.getId())).isPresent())
            throw new IllegalArgumentException("Email is already in use.");
        admins.save(account);
    }

    @Transactional
    public void password(String actor,String old,String password,String confirmation) {
        SaasAdmin account=admins.findByUsername(actor).orElseThrow();
        if(!encoder.matches(old,account.getPasswordHash())) throw new IllegalArgumentException("Current password is incorrect.");
        validatePassword(password);
        if(!password.equals(confirmation)) throw new IllegalArgumentException("New passwords do not match.");
        account.setPasswordHash(encoder.encode(password)); admins.save(account);
    }

    @Transactional
    public void create(String username,String first,String last,String email,String password) {
        if(!username.matches("[A-Za-z0-9._-]{3,50}")) throw new IllegalArgumentException("Use a username of 3–50 letters, numbers, dots, underscores or hyphens.");
        validatePassword(password);
        if(admins.findByUsernameIgnoreCase(username).isPresent() || !users.findAllByUsernameForMobileLogin(username).isEmpty())
            throw new IllegalArgumentException("Username is already in use. Choose a unique platform username.");
        if(admins.findByEmailIgnoreCase(email.strip()).isPresent()) throw new IllegalArgumentException("Email is already in use.");
        SaasAdmin account=new SaasAdmin(); names(account,first,last,email);
        account.setUsername(username); account.setPasswordHash(encoder.encode(password)); account.setIsActive(true); admins.save(account);
    }

    @Transactional
    public void status(String actor,Long id,boolean active) {
        // Serialize administrator status changes so two admins cannot disable each other concurrently.
        admins.lockAccounts();
        if (admins.findByUsername(actor).filter(a -> Boolean.TRUE.equals(a.getIsActive())).isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("Your administrator account is inactive.");
        SaasAdmin account=admins.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrator not found."));
        if(account.getUsername().equals(actor)) throw new IllegalArgumentException("You cannot deactivate your own account.");
        if(!active && admins.countByIsActiveTrue() <= 1) throw new IllegalArgumentException("At least one active administrator is required.");
        account.setIsActive(active); admins.save(account);
    }

    @Transactional
    public void update(String actor,Long id,String first,String last,String email,String password) {
        admins.lockAccounts();
        if(admins.findByUsername(actor).filter(a -> Boolean.TRUE.equals(a.getIsActive())).isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("Your administrator account is inactive.");
        SaasAdmin account=admins.findById(id).orElseThrow(() -> new IllegalArgumentException("Administrator not found."));
        if(account.getUsername().equals(actor)) throw new IllegalArgumentException("Use My profile and Change my password to update your own account.");
        names(account,first,last,email);
        if(admins.findByEmailIgnoreCase(account.getEmail()).filter(a -> !a.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("Email is already in use.");
        if(password!=null && !password.isEmpty()) { validatePassword(password); account.setPasswordHash(encoder.encode(password)); }
        admins.save(account);
    }

    private void names(SaasAdmin account,String first,String last,String email) {
        if(first.isBlank() || last.isBlank() || first.length()>100 || last.length()>100) throw new IllegalArgumentException("First and last names are required (maximum 100 characters each).");
        if(email.length()>150 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email address.");
        account.setFirstName(first.strip());account.setLastName(last.strip());account.setEmail(email.strip());
    }
    private void validatePassword(String password) {
        if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Use a password of at least 12 characters and at most 72 UTF-8 bytes.");
    }
}
