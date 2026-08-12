package com.retailzw.service;

import com.retailzw.model.SoftwareRelease;
import com.retailzw.repository.SoftwareReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SoftwareReleaseServiceTest {
    @TempDir Path tempDir;
    private SoftwareReleaseRepository repository;
    private SoftwareReleaseService service;

    @BeforeEach
    void setUp() {
        repository = mock(SoftwareReleaseRepository.class);
        service = new SoftwareReleaseService(repository);
        ReflectionTestUtils.setField(service, "releaseUploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "maximumFileSize", 10_000_000L);
        when(repository.saveAndFlush(any(SoftwareRelease.class))).thenAnswer(invocation -> {
            SoftwareRelease release = invocation.getArgument(0);
            release.setId(42L);
            return release;
        });
    }

    @Test
    void uploadsValidatedApkAndCreatesChecksum() throws Exception {
        byte[] apk = {'P', 'K', 3, 4, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "RetailZim-1.2.0.apk",
                "application/vnd.android.package-archive", apk);

        SoftwareRelease release = service.upload(file, SoftwareRelease.Platform.ANDROID,
                SoftwareRelease.PackageType.APK, "1.2.0", "Retail Zim Mobile POS",
                "Mobile checkout update.", "Faster checkout", "Android 8+", true, true, "platform");

        assertThat(release.getId()).isEqualTo(42L);
        assertThat(release.getSha256Checksum()).hasSize(64);
        assertThat(tempDir.resolve(release.getStoredFileName())).exists();
        verify(repository).clearLatest(SoftwareRelease.Platform.ANDROID, SoftwareRelease.PackageType.APK, 42L);
    }

    @Test
    void rejectsPackageThatDoesNotMatchPlatform() {
        MockMultipartFile file = new MockMultipartFile("file", "release.apk", "application/octet-stream",
                new byte[]{'P', 'K', 3, 4});

        assertThatThrownBy(() -> service.upload(file, SoftwareRelease.Platform.WINDOWS,
                SoftwareRelease.PackageType.INSTALLER, "1.0", "POS", "Description", null,
                null, false, false, "platform"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRenamedFileWithInvalidSignature() {
        MockMultipartFile file = new MockMultipartFile("file", "release.apk", "application/octet-stream",
                "not an apk".getBytes());

        assertThatThrownBy(() -> service.upload(file, SoftwareRelease.Platform.ANDROID,
                SoftwareRelease.PackageType.APK, "1.0", "Mobile POS", "Description", null,
                null, false, false, "platform"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    void streamsOnlyPublishedStoredRelease() throws Exception {
        Path file = tempDir.resolve("release.apk");
        java.nio.file.Files.write(file, new byte[]{'P', 'K', 3, 4});
        SoftwareRelease release = SoftwareRelease.builder().id(7L).platform(SoftwareRelease.Platform.ANDROID)
                .packageType(SoftwareRelease.PackageType.APK).version("1.0").title("Mobile")
                .description("Description").originalFileName("RetailZim.apk").storedFileName("release.apk")
                .contentType("application/vnd.android.package-archive").fileSize(4L).sha256Checksum("a".repeat(64))
                .published(true).build();
        when(repository.findByIdAndPublishedTrue(7L)).thenReturn(Optional.of(release));

        SoftwareReleaseService.DownloadableRelease result = service.openPublishedDownload(7L);

        assertThat(result.resource().exists()).isTrue();
        verify(repository).incrementDownloadCount(7L);
    }
}
