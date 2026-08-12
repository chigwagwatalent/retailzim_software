package com.retailzw.repository;

import com.retailzw.model.SoftwareRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SoftwareReleaseRepository extends JpaRepository<SoftwareRelease, Long> {
    List<SoftwareRelease> findAllByOrderByCreatedAtDesc();
    List<SoftwareRelease> findByPublishedTrueOrderByPlatformAscLatestDescCreatedAtDesc();
    Optional<SoftwareRelease> findByIdAndPublishedTrue(Long id);
    long countByPublishedTrue();
    long countByPlatform(SoftwareRelease.Platform platform);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SoftwareRelease r set r.latest = false where r.platform = :platform and r.packageType = :packageType and r.id <> :exceptId")
    int clearLatest(@Param("platform") SoftwareRelease.Platform platform,
                    @Param("packageType") SoftwareRelease.PackageType packageType,
                    @Param("exceptId") Long exceptId);

    @Modifying
    @Query("update SoftwareRelease r set r.downloadCount = r.downloadCount + 1 where r.id = :id")
    int incrementDownloadCount(@Param("id") Long id);
}
