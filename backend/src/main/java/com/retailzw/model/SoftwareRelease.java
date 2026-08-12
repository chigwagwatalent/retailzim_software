package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "software_releases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareRelease {
    public enum Platform { WINDOWS, ANDROID }
    public enum PackageType { INSTALLER, PORTABLE_ZIP, APK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 30)
    private PackageType packageType;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "minimum_requirements", length = 500)
    private String minimumRequirements;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, unique = true, length = 255)
    private String storedFileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256_checksum", nullable = false, length = 64)
    private String sha256Checksum;

    @Builder.Default
    @Column(nullable = false)
    private Boolean published = false;

    @Builder.Default
    @Column(name = "latest_release", nullable = false)
    private Boolean latest = false;

    @Builder.Default
    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (Boolean.TRUE.equals(published) && publishedAt == null) publishedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
        if (Boolean.TRUE.equals(published) && publishedAt == null) publishedAt = updatedAt;
    }
}
