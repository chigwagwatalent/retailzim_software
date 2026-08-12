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

import java.util.List;
import java.util.Locale;

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
        int selectedDays = Math.max(1, Math.min(days, 365));
        String selectedStatus = normalizeStatus(status);
        var dashboard = frontWebsiteCommunityService.dashboard(null, selectedDays);
        List<FrontWebsiteCommunityService.CommunityPost> allPosts = dashboard.posts();
        List<FrontWebsiteCommunityService.CommunityPost> visiblePosts = allPosts.stream()
                .filter(post -> selectedStatus.isBlank()
                        || ("resolved".equals(selectedStatus) && !post.isOpen())
                        || selectedStatus.equals(post.safeStatus()))
                .toList();
        long openQuestionCount = allPosts.stream()
                .filter(FrontWebsiteCommunityService.CommunityPost::isOpen)
                .count();
        long answeredQuestionCount = Math.max(0, allPosts.size() - openQuestionCount);
        int communityHealth = !dashboard.online()
                ? 0
                : allPosts.isEmpty()
                ? 100
                : (int) Math.round((answeredQuestionCount * 100.0) / allPosts.size());

        model.addAttribute("posts", visiblePosts);
        model.addAttribute("siteStats", dashboard.stats());
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedDays", selectedDays);
        model.addAttribute("openQuestionCount", openQuestionCount);
        model.addAttribute("answeredQuestionCount", answeredQuestionCount);
        model.addAttribute("communityHealth", communityHealth);
        model.addAttribute("communityOnline", dashboard.online());
        return "admin/website-community";
    }

    @PostMapping("/admin/website-community/{postId}/answer")
    public String answerWebsiteCommunityPost(@PathVariable Long postId,
                                             @RequestParam String answer,
                                             @RequestParam(defaultValue = "Retail Zim Support") String responder,
                                             @RequestParam(defaultValue = "answered") String status,
                                             @RequestParam(defaultValue = "30") int days,
                                             @RequestParam(defaultValue = "") String viewStatus,
                                             RedirectAttributes redirect) {
        boolean saved = frontWebsiteCommunityService.answerPost(postId, answer, responder, status);
        redirect.addFlashAttribute("message", saved
                ? "Website community answer published."
                : "Website community answer could not be published. Check the front website API settings.");
        redirect.addAttribute("days", Math.max(1, Math.min(days, 365)));
        String selectedStatus = normalizeStatus(viewStatus);
        if (!selectedStatus.isBlank()) {
            redirect.addAttribute("status", selectedStatus);
        }
        return "redirect:/admin/website-community";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "open", "answered", "solved", "featured", "resolved" -> normalized;
            default -> "";
        };
    }

    private void addNavigationModel(Model model) {
        model.addAttribute("supportUnreadCount",
                chatMessages.countByReadByPlatformFalseAndSenderType(TenantChatMessage.SenderType.SHOP));
    }
}
