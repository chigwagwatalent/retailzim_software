package com.retailzw.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class FrontWebsiteCommunityService {
    private static final Set<String> ANSWER_STATUSES = Set.of("answered", "solved", "featured");
    private static final int MAX_ANSWER_LENGTH = 4_000;

    private final RestClient restClient;
    private final String apiKey;

    public FrontWebsiteCommunityService(
            RestClient.Builder restClientBuilder,
            @Value("${retailzim.front-site.base-url:https://retailzw.co.zw/}") String baseUrl,
            @Value("${retailzim.front-site.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(normalizeBaseUrl(baseUrl)).build();
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public CommunityDashboard dashboard(String status, int days) {
        FetchResult<List<CommunityPost>> posts = loadPosts(status);
        FetchResult<VisitStats> stats = loadStats(days);
        return new CommunityDashboard(posts.value(), stats.value(), posts.available() && stats.available());
    }

    public List<CommunityPost> fetchPosts(String status) {
        return loadPosts(status).value();
    }

    private FetchResult<List<CommunityPost>> loadPosts(String status) {
        try {
            String uri = status == null || status.isBlank()
                    ? "/api/community/posts?limit=100"
                    : "/api/community/posts?limit=100&status={status}";
            CommunityPostsResponse response = status == null || status.isBlank()
                    ? restClient.get().uri(uri).retrieve().body(CommunityPostsResponse.class)
                    : restClient.get().uri(uri, status).retrieve().body(CommunityPostsResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.ok())) {
                return new FetchResult<>(List.of(), false);
            }
            return new FetchResult<>(response.posts() == null ? List.of() : response.posts(), true);
        } catch (Exception ex) {
            log.warn("Could not fetch Retail Zim front website community posts: {}", ex.getMessage());
            return new FetchResult<>(List.of(), false);
        }
    }

    public VisitStats fetchStats(int days) {
        return loadStats(days).value();
    }

    private FetchResult<VisitStats> loadStats(int days) {
        try {
            VisitStatsResponse response = restClient.get()
                    .uri("/api/visits/stats?days={days}", Math.max(1, Math.min(days, 365)))
                    .retrieve()
                    .body(VisitStatsResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.ok()) || response.stats() == null) {
                return new FetchResult<>(VisitStats.empty(), false);
            }
            return new FetchResult<>(response.stats(), true);
        } catch (Exception ex) {
            log.warn("Could not fetch Retail Zim front website visit stats: {}", ex.getMessage());
            return new FetchResult<>(VisitStats.empty(), false);
        }
    }

    public boolean answerPost(Long postId, String answer, String responder, String status) {
        String cleanAnswer = answer == null ? "" : answer.trim();
        String cleanStatus = status == null ? "answered" : status.trim().toLowerCase(Locale.ROOT);
        if (postId == null || postId <= 0 || cleanAnswer.isBlank()
                || cleanAnswer.length() > MAX_ANSWER_LENGTH || !ANSWER_STATUSES.contains(cleanStatus)) {
            return false;
        }
        try {
            AnswerResponse response = restClient.post()
                    .uri("/api/community/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-RetailZim-Admin-Key", apiKey)
                    .body(Map.of(
                            "post_id", postId,
                            "answer", cleanAnswer,
                            "responder", responder == null || responder.isBlank() ? "Retail Zim Support" : responder,
                            "status", cleanStatus
                    ))
                    .retrieve()
                    .body(AnswerResponse.class);
            return response != null && Boolean.TRUE.equals(response.ok());
        } catch (Exception ex) {
            log.warn("Could not answer Retail Zim front website community post {}: {}", postId, ex.getMessage());
            return false;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String clean = baseUrl == null || baseUrl.isBlank() ? "https://retailzw.co.zw/" : baseUrl.trim();
        return clean.endsWith("/") ? clean.substring(0, clean.length() - 1) : clean;
    }

    private record FetchResult<T>(T value, boolean available) {
    }

    public record CommunityDashboard(List<CommunityPost> posts, VisitStats stats, boolean online) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommunityPostsResponse(Boolean ok, List<CommunityPost> posts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommunityPost(
            Long id,
            String name,
            String shop,
            String category,
            String message,
            String status,
            Integer likes,
            Integer replies,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt,
            List<CommunityAnswer> answers) {
        public int safeLikes() {
            return likes == null ? 0 : likes;
        }

        public int safeReplies() {
            return replies == null ? 0 : replies;
        }

        public List<CommunityAnswer> safeAnswers() {
            return answers == null ? List.of() : answers;
        }

        public String initial() {
            String clean = name == null || name.isBlank() ? "R" : name.trim();
            return clean.substring(0, 1).toUpperCase();
        }

        public String shopTimeLabel() {
            String time = createdAt == null ? "" : createdAt;
            return shop == null || shop.isBlank() ? time : shop + " - " + time;
        }

        public String safeStatus() {
            if (status == null || status.isBlank()) {
                return "open";
            }
            String normalized = status.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "answered", "solved", "featured" -> normalized;
                default -> "open";
            };
        }

        public boolean isOpen() {
            return "open".equals(safeStatus());
        }

        public String statusLabel() {
            return switch (safeStatus()) {
                case "answered" -> "Answered";
                case "solved" -> "Solved";
                case "featured" -> "Featured";
                default -> "Needs reply";
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommunityAnswer(
            Long id,
            @JsonProperty("post_id") Long postId,
            String responder,
            String answer,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VisitStatsResponse(Boolean ok, VisitStats stats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VisitStats(
            Map<String, Integer> metrics,
            @JsonProperty("by_page") List<Map<String, Object>> byPage,
            List<Map<String, Object>> daily,
            List<Map<String, Object>> recent) {
        public static VisitStats empty() {
            return new VisitStats(
                    Map.of("visits", 0, "unique_visitors", 0, "posts", 0, "engagements", 0),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()
            );
        }

        public int metric(String key) {
            return metrics == null || metrics.get(key) == null ? 0 : metrics.get(key);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnswerResponse(Boolean ok, String message) {
    }
}
