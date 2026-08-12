package com.retailzw.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrontWebsiteCommunityServiceTest {

    @Test
    void communityPostNormalizesStatusesForTheAdminInbox() {
        var open = post(null);
        var answered = post("ANSWERED");
        var solved = post("solved");
        var unknown = post("unexpected");

        assertThat(open.safeStatus()).isEqualTo("open");
        assertThat(open.statusLabel()).isEqualTo("Needs reply");
        assertThat(open.isOpen()).isTrue();
        assertThat(answered.safeStatus()).isEqualTo("answered");
        assertThat(answered.statusLabel()).isEqualTo("Answered");
        assertThat(solved.statusLabel()).isEqualTo("Solved");
        assertThat(unknown.safeStatus()).isEqualTo("open");
    }

    private FrontWebsiteCommunityService.CommunityPost post(String status) {
        return new FrontWebsiteCommunityService.CommunityPost(
                1L, "Customer", "Shop", "Question", "How do I start?", status,
                0, 0, "2026-08-11 09:00:00", "2026-08-11 09:00:00", List.of());
    }
}
