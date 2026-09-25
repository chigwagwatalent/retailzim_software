package com.retailzw.controller.web;

import com.retailzw.model.SaasAdmin;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class AdminSettingsController {
    private final SaasAdminRepository admins;
    private final com.retailzw.service.AdminAccountService accounts;

    @GetMapping("/admin/settings")
    public String settings(Principal principal, @RequestParam(defaultValue="0") int page, Model model) {
        SaasAdmin actor = admins.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("profile", actor);
        model.addAttribute("adminPage", admins.findAll(org.springframework.data.domain.PageRequest.of(Math.max(0,page),25,org.springframework.data.domain.Sort.by("id"))));
        return "admin/settings";
    }

    @PostMapping("/admin/settings/profile")
    public String profile(Principal principal, @RequestParam String firstName, @RequestParam String lastName,
                          @RequestParam String email, RedirectAttributes redirect) {
        return handle(redirect, () -> accounts.profile(principal.getName(),firstName,lastName,email),"Profile updated.");
    }

    @PostMapping("/admin/settings/password")
    public String password(Principal principal, @RequestParam String currentPassword, @RequestParam String password,
                           @RequestParam String confirmPassword, RedirectAttributes redirect) {
        return handle(redirect, () -> accounts.password(principal.getName(),currentPassword,password,confirmPassword),"Password updated.");
    }

    @PostMapping("/admin/settings/users")
    public String create(@RequestParam String username, @RequestParam String firstName, @RequestParam String lastName,
                         @RequestParam String email, @RequestParam String password, RedirectAttributes redirect) {
        return handle(redirect, () -> accounts.create(username,firstName,lastName,email,password),"Platform administrator created.");
    }

    @PostMapping("/admin/settings/users/{id}/status")
    public String status(Principal principal, @PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        return handle(redirect, () -> accounts.status(principal.getName(),id,active),"Administrator access updated.");
    }

    private String handle(RedirectAttributes redirect, Runnable action, String success) {
        try { action.run(); redirect.addFlashAttribute("message",success); }
        catch(IllegalArgumentException ex) { redirect.addFlashAttribute("message",ex.getMessage()); }
        catch(org.springframework.dao.DataIntegrityViolationException ex) { redirect.addFlashAttribute("message","Username or email is already in use."); }
        return "redirect:/admin/settings";
    }

    @PostMapping("/admin/settings/users/{id}")
    public String update(Principal principal,@PathVariable Long id,@RequestParam String firstName,
                         @RequestParam String lastName,@RequestParam String email,@RequestParam(required=false) String password,
                         RedirectAttributes redirect) {
        return handle(redirect,() -> accounts.update(principal.getName(),id,firstName,lastName,email,password),"Administrator updated.");
    }
}
