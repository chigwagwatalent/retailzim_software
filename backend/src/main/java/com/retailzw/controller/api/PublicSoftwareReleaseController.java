package com.retailzw.controller.api;

import com.retailzw.model.SoftwareRelease;
import com.retailzw.service.SoftwareReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/releases")
@RequiredArgsConstructor
public class PublicSoftwareReleaseController {
    private final SoftwareReleaseService service;

    @GetMapping
    public Map<String, Object> releases() {
        List<ReleaseView> releases = service.listPublished().stream().map(this::view).toList();
        return Map.of("releases", releases, "count", releases.size());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SoftwareReleaseService.DownloadableRelease downloadable;
        try {
            downloadable = service.openPublishedDownload(id);
        } catch (IllegalArgumentException | IOException ex) {
            return ResponseEntity.notFound().build();
        }
        SoftwareRelease release = downloadable.release();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(release.getOriginalFileName(), StandardCharsets.UTF_8).build();
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(release.getContentType()); }
        catch (Exception ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(release.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(downloadable.resource());
    }

    private ReleaseView view(SoftwareRelease release) {
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/public/releases/{id}/download").buildAndExpand(release.getId()).toUriString();
        return new ReleaseView(release.getId(), release.getPlatform().name(), release.getPackageType().name(),
                release.getVersion(), release.getTitle(), release.getDescription(), release.getReleaseNotes(),
                release.getMinimumRequirements(), release.getOriginalFileName(), release.getFileSize(),
                formatSize(release.getFileSize()), release.getSha256Checksum(), Boolean.TRUE.equals(release.getLatest()),
                release.getPublishedAt() == null ? release.getCreatedAt() : release.getPublishedAt(),
                release.getDownloadCount(), url);
    }

    private String formatSize(long size) {
        if (size >= 1_073_741_824L) return String.format("%.1f GB", size / 1_073_741_824d);
        if (size >= 1_048_576L) return String.format("%.1f MB", size / 1_048_576d);
        if (size >= 1024L) return String.format("%.1f KB", size / 1024d);
        return size + " B";
    }

    public record ReleaseView(Long id, String platform, String packageType, String version, String title,
                              String description, String releaseNotes, String minimumRequirements,
                              String fileName, Long fileSize, String formattedSize, String checksum,
                              boolean latest, LocalDateTime releasedAt, Long downloadCount, String downloadUrl) {}
}
