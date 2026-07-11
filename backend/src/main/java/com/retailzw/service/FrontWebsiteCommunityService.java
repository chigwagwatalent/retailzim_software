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
import java.util.Map;

@Service
@Slf4j
public class FrontWebsiteCommunityService {
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
        return new CommunityDashboard(fetchPosts(status), fetchStats(days));
    }

    public List<CommunityPost> fetchPosts(String status) {
        try {
            String uri = status == null || status.isBlank()
                    ? "/api/community/posts?limit=100"
                    : "/api/community/posts?limit=100&status={status}";
            CommunityPostsResponse response = status == null || status.isBlank()
                    ? restClient.get().uri(uri).retrieve().body(CommunityPostsResponse.class)
                    : restClient.get().uri(uri, status).retrieve().body(CommunityPostsResponse.class);
            return response == null || response.posts() == null ? List.of() : response.posts();
        } catch (Exception ex) {
            log.warn("Could not fetch Retail Zim front website community posts: {}", ex.getMessage());
            return List.of();
        }
    }

    public VisitStats fetchStats(int days) {
        try {
            VisitStatsResponse response = restClient.get()
                    .uri("/api/visits/stats?days={days}", Math.max(1, Math.min(days, 365)))
                    .retrieve()
                    .body(VisitStatsResponse.class);
            return response == null || response.stats() == null ? VisitStats.empty() : response.stats();
        } catch (Exception ex) {
            log.warn("Could not fetch Retail Zim front website visit stats: {}", ex.getMessage());
            return VisitStats.empty();
        }
    }

    public boolean answerPost(Long postId, String answer, String responder, String status) {
        if (postId == null || postId <= 0 || answer == null || answer.isBlank()) {
            return false;
        }
        try {
            AnswerResponse response = restClient.post()
                    .uri("/api/community/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-RetailZim-Admin-Key", apiKey)
                    .body(Map.of(
                            "post_id", postId,
                            "answer", answer,
                            "responder", responder == null || responder.isBlank() ? "Retail Zim Support" : responder,
                            "status", status == null || status.isBlank() ? "answered" : status
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

    public record CommunityDashboard(List<CommunityPost> posts, VisitStats stats) {
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
