package com.retailzw.service;

import com.retailzw.model.SoftwareRelease;
import com.retailzw.repository.SoftwareReleaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SoftwareReleaseService {
    private final SoftwareReleaseRepository repository;

    @Value("${app.release-upload-dir:uploads/releases}")
    private String releaseUploadDir;

    @Value("${app.release-max-file-size-bytes:314572800}")
    private long maximumFileSize;

    public List<SoftwareRelease> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<SoftwareRelease> listPublished() {
        return repository.findByPublishedTrueOrderByPlatformAscLatestDescCreatedAtDesc();
    }

    @Transactional
    public SoftwareRelease upload(MultipartFile file, SoftwareRelease.Platform platform,
                                  SoftwareRelease.PackageType packageType, String version, String title,
                                  String description, String releaseNotes, String minimumRequirements,
                                  boolean published, boolean latest, String uploadedBy) throws IOException {
        validateMetadata(file, platform, packageType, version, title, description, published, latest);
        String submittedName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "release" : file.getOriginalFilename())
                .replace('\\', '/');
        if (submittedName.contains("..")) throw new IllegalArgumentException("Invalid release file name.");
        String originalName = submittedName.substring(submittedName.lastIndexOf('/') + 1);
        validatePackage(file, platform, packageType, originalName);

        Path root = storageRoot();
        Files.createDirectories(root);
        String extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        String storedName = UUID.randomUUID() + extension;
        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid release file name.");

        String checksum;
        try (InputStream input = file.getInputStream()) {
            checksum = copyAndHash(input, target);
        }

        try {
            SoftwareRelease release = SoftwareRelease.builder()
                    .platform(platform).packageType(packageType)
                    .version(clean(version, 50)).title(clean(title, 160))
                    .description(description.trim())
                    .releaseNotes(blankToNull(releaseNotes))
                    .minimumRequirements(cleanNullable(minimumRequirements, 500))
                    .originalFileName(originalName).storedFileName(storedName)
                    .contentType(resolveContentType(file.getContentType(), extension))
                    .fileSize(file.getSize()).sha256Checksum(checksum)
                    .published(published).latest(latest).uploadedBy(cleanNullable(uploadedBy, 120))
                    .build();
            release = repository.saveAndFlush(release);
            if (latest) repository.clearLatest(platform, packageType, release.getId());
            return release;
        } catch (RuntimeException ex) {
            Files.deleteIfExists(target);
            throw ex;
        }
    }

    @Transactional
    public void setPublished(Long id, boolean published) {
        SoftwareRelease release = required(id);
        release.setPublished(published);
        if (published && release.getPublishedAt() == null) release.setPublishedAt(LocalDateTime.now());
        if (!published) release.setLatest(false);
        repository.save(release);
    }

    @Transactional
    public void markLatest(Long id) {
        SoftwareRelease release = required(id);
        if (!Boolean.TRUE.equals(release.getPublished())) {
            throw new IllegalArgumentException("Publish this release before marking it as latest.");
        }
        repository.clearLatest(release.getPlatform(), release.getPackageType(), release.getId());
        release.setLatest(true);
        repository.save(release);
    }

    @Transactional
    public void updateMetadata(Long id, String version, String title, String description,
                               String releaseNotes, String minimumRequirements) {
        if (version == null || version.isBlank() || title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Version, title and description are required.");
        }
        SoftwareRelease release = required(id);
        release.setVersion(clean(version, 50));
        release.setTitle(clean(title, 160));
        release.setDescription(description.trim());
        release.setReleaseNotes(blankToNull(releaseNotes));
        release.setMinimumRequirements(cleanNullable(minimumRequirements, 500));
        repository.save(release);
    }

    @Transactional
    public DownloadableRelease openPublishedDownload(Long id) throws IOException {
        SoftwareRelease release = repository.findByIdAndPublishedTrue(id)
                .orElseThrow(() -> new IllegalArgumentException("This release is not available for download."));
        Path path = storageRoot().resolve(release.getStoredFileName()).normalize();
        if (!path.startsWith(storageRoot()) || !Files.isRegularFile(path)) {
            throw new NoSuchFileException("Release package is unavailable.");
        }
        Resource resource = new UrlResource(path.toUri());
        repository.incrementDownloadCount(id);
        return new DownloadableRelease(release, resource);
    }

    private SoftwareRelease required(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Release not found."));
    }

    private Path storageRoot() {
        return Paths.get(releaseUploadDir).toAbsolutePath().normalize();
    }

    private void validateMetadata(MultipartFile file, SoftwareRelease.Platform platform,
                                  SoftwareRelease.PackageType packageType, String version,
                                  String title, String description, boolean published, boolean latest) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a release file to upload.");
        if (file.getSize() > maximumFileSize) throw new IllegalArgumentException("The release file is larger than the 300 MB limit.");
        if (platform == null || packageType == null) throw new IllegalArgumentException("Choose a platform and package type.");
        if (latest && !published) throw new IllegalArgumentException("Publish the release before marking it as latest.");
        if (version == null || version.isBlank() || title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Version, title and description are required.");
        }
    }

    private void validatePackage(MultipartFile file, SoftwareRelease.Platform platform,
                                 SoftwareRelease.PackageType packageType, String fileName) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        boolean pairingValid = platform == SoftwareRelease.Platform.ANDROID
                ? packageType == SoftwareRelease.PackageType.APK && lower.endsWith(".apk")
                : (packageType == SoftwareRelease.PackageType.PORTABLE_ZIP && lower.endsWith(".zip"))
                  || (packageType == SoftwareRelease.PackageType.INSTALLER && (lower.endsWith(".exe") || lower.endsWith(".msi")));
        if (!pairingValid) throw new IllegalArgumentException("The selected file does not match its platform and package type.");

        byte[] header = new byte[8];
        int read;
        try (InputStream input = file.getInputStream()) { read = input.read(header); }
        boolean zip = read >= 2 && header[0] == 'P' && header[1] == 'K';
        boolean exe = read >= 2 && header[0] == 'M' && header[1] == 'Z';
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        boolean msi = read == 8 && java.util.Arrays.equals(header, ole);
        if ((packageType == SoftwareRelease.PackageType.APK || packageType == SoftwareRelease.PackageType.PORTABLE_ZIP) && !zip) {
            throw new IllegalArgumentException("This file is not a valid ZIP/APK package.");
        }
        if (packageType == SoftwareRelease.PackageType.INSTALLER && !(exe || msi)) {
            throw new IllegalArgumentException("This file is not a valid Windows EXE or MSI installer.");
        }
    }

    private String copyAndHash(InputStream input, Path target) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private String resolveContentType(String supplied, String extension) {
        if (".apk".equals(extension)) return "application/vnd.android.package-archive";
        if (".zip".equals(extension)) return "application/zip";
        if (".msi".equals(extension)) return "application/x-msi";
        if (".exe".equals(extension)) return "application/vnd.microsoft.portable-executable";
        return supplied == null || supplied.isBlank() ? "application/octet-stream" : supplied;
    }

    private String clean(String value, int max) {
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private String cleanNullable(String value, int max) {
        return value == null || value.isBlank() ? null : clean(value, max);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DownloadableRelease(SoftwareRelease release, Resource resource) {}
}
