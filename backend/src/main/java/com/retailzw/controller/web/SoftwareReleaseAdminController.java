package com.retailzw.controller.web;

import com.retailzw.model.SoftwareRelease;
import com.retailzw.repository.SoftwareReleaseRepository;
import com.retailzw.service.SoftwareReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/releases")
@RequiredArgsConstructor
public class SoftwareReleaseAdminController {
    private final SoftwareReleaseService service;
    private final SoftwareReleaseRepository repository;

    @GetMapping
    public String releases(Model model) {
        model.addAttribute("releases", service.listAll());
        model.addAttribute("publishedCount", repository.countByPublishedTrue());
        model.addAttribute("windowsCount", repository.countByPlatform(SoftwareRelease.Platform.WINDOWS));
        model.addAttribute("androidCount", repository.countByPlatform(SoftwareRelease.Platform.ANDROID));
        return "admin/releases";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam MultipartFile file,
                         @RequestParam SoftwareRelease.Platform platform,
                         @RequestParam SoftwareRelease.PackageType packageType,
                         @RequestParam String version, @RequestParam String title,
                         @RequestParam String description,
                         @RequestParam(required = false) String releaseNotes,
                         @RequestParam(required = false) String minimumRequirements,
                         @RequestParam(defaultValue = "false") boolean published,
                         @RequestParam(defaultValue = "false") boolean latest,
                         Authentication authentication, RedirectAttributes redirect) {
        try {
            SoftwareRelease release = service.upload(file, platform, packageType, version, title, description,
                    releaseNotes, minimumRequirements, published, latest, authentication.getName());
            redirect.addFlashAttribute("message", "Version " + release.getVersion() + " uploaded successfully.");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", ex.getMessage() == null ? "The release could not be uploaded." : ex.getMessage());
        }
        return "redirect:/admin/releases";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, @RequestParam boolean published, RedirectAttributes redirect) {
        return run(() -> service.setPublished(id, published), published ? "Release published." : "Release hidden from the public website.", redirect);
    }

    @PostMapping("/{id}/latest")
    public String latest(@PathVariable Long id, RedirectAttributes redirect) {
        return run(() -> service.markLatest(id), "Release marked as latest.", redirect);
    }

    @PostMapping("/{id}/metadata")
    public String metadata(@PathVariable Long id, @RequestParam String version, @RequestParam String title,
                           @RequestParam String description, @RequestParam(required = false) String releaseNotes,
                           @RequestParam(required = false) String minimumRequirements, RedirectAttributes redirect) {
        return run(() -> service.updateMetadata(id, version, title, description, releaseNotes, minimumRequirements),
                "Release details updated.", redirect);
    }

    private String run(Runnable action, String message, RedirectAttributes redirect) {
        try { action.run(); redirect.addFlashAttribute("message", message); }
        catch (RuntimeException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/releases";
    }
}
