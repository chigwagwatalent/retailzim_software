package com.retailzw.controller.web;

import com.retailzw.model.TenantChatMessage;
import com.retailzw.repository.TenantChatMessageRepository;
import com.retailzw.service.FrontWebsiteCommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class WebsiteCommunityAdminController {
    private final FrontWebsiteCommunityService frontWebsiteCommunityService;
    private final TenantChatMessageRepository chatMessages;

    @GetMapping("/admin/website-community")
    public String websiteCommunity(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "30") int days,
                                   Model model) {
        addNavigationModel(model);
        var dashboard = frontWebsiteCommunityService.dashboard(status, days);
        model.addAttribute("posts", dashboard.posts());
        model.addAttribute("siteStats", dashboard.stats());
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("selectedDays", Math.max(1, Math.min(days, 365)));
        return "admin/website-community";
    }

    @PostMapping("/admin/website-community/{postId}/answer")
    public String answerWebsiteCommunityPost(@PathVariable Long postId,
                                             @RequestParam String answer,
                                             @RequestParam(defaultValue = "Retail Zim Support") String responder,
                                             @RequestParam(defaultValue = "answered") String status,
                                             RedirectAttributes redirect) {
        boolean saved = frontWebsiteCommunityService.answerPost(postId, answer, responder, status);
        redirect.addFlashAttribute("message", saved
                ? "Website community answer published."
                : "Website community answer could not be published. Check the front website API settings.");
        return "redirect:/admin/website-community";
    }

    private void addNavigationModel(Model model) {
        model.addAttribute("supportUnreadCount",
                chatMessages.countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType.SHOP));
    }
}
