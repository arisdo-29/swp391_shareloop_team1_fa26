package com.shareloop.moderation.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.integration.ai.AiClient;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.AiScreeningContent;
import com.shareloop.moderation.event.ItemScreeningRequestedEvent;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Second-layer screening only annotates a pending item; it never owns item status. */
@Service
public class ItemScreeningService {
    private final AiClient aiClient;
    private final CategoryService categoryService;
    private final ConfigService configService;
    private final ActivityLogService activityLogService;
    private final JdbcClient jdbcClient;
    private final ObjectMapper mapper;

    public ItemScreeningService(
            AiClient aiClient,
            CategoryService categoryService,
            ConfigService configService,
            ActivityLogService activityLogService,
            JdbcClient jdbcClient,
            ObjectMapper mapper) {
        this.aiClient = aiClient;
        this.categoryService = categoryService;
        this.configService = configService;
        this.activityLogService = activityLogService;
        this.jdbcClient = jdbcClient;
        this.mapper = mapper;
    }

    public void screen(ItemScreeningRequestedEvent event) {
        if (!aiClient.isAvailable()) {
            return;
        }
        String category;
        try {
            category = categoryService.getById(event.categoryId()).name();
        } catch (RuntimeException exception) {
            return; // Keep UNCHECKED if the category can no longer be resolved.
        }
        AiScreeningContent content = new AiScreeningContent(
                event.title(),
                event.description(),
                event.desiredItem(),
                category,
                event.condition(),
                event.offerType());
        int timeoutSeconds = configService.getInt(ConfigKey.ITEM_AI_TIMEOUT_SECONDS);
        int retries = Math.max(0, configService.getInt(ConfigKey.ITEM_AI_RETRIES));
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();

        for (int attempt = 1; attempt <= retries + 1; attempt++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            long started = System.nanoTime();
            try {
                AiResponse response = aiClient.screen(content, Duration.ofNanos(remaining));
                Map<String, Object> result = validate(response.content());
                result.put("model", response.model());
                result.put("checkedAt", Instant.now().toString());
                String risk = ((String) result.get("risk")).toUpperCase(java.util.Locale.ROOT);
                if (needsReview(result)) {
                    result.put("flag", "REVIEW");
                    result.put("reason", result.get("reasonVi"));
                }
                store(event, risk, mapper.writeValueAsString(result));
                logCall(event, attempt, response.model(), "SUCCESS", started);
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                logCall(event, attempt, null, "INTERRUPTED", started);
                return;
            } catch (Exception exception) {
                logCall(event, attempt, null, exception.getClass().getSimpleName(), started);
            }
        }
        // ai_risk remains UNCHECKED and the item remains in the human review queue.
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validate(String json) {
        Map<String, Object> result = mapper.readValue(json, Map.class);
        if (result == null
                || !result.keySet()
                        .equals(java.util.Set.of(
                                "contactInfo",
                                "prohibited",
                                "categoryMismatch",
                                "swapIntentInGivePost",
                                "qualityIssue",
                                "risk",
                                "reasonVi"))
                || !validFinding(result.get("contactInfo"), false)
                || !validFinding(result.get("prohibited"), true)
                || !(result.get("categoryMismatch") instanceof Boolean)
                || !(result.get("swapIntentInGivePost") instanceof Boolean)
                || !(result.get("qualityIssue") == null || result.get("qualityIssue") instanceof String)
                || !(result.get("reasonVi") instanceof String reason && !reason.isBlank())
                || !(result.get("risk") instanceof String risk
                        && (risk.equals("low") || risk.equals("medium") || risk.equals("high")))) {
            throw new IllegalArgumentException("AI screening response does not match the schema");
        }
        return new HashMap<>(result);
    }

    private static boolean validFinding(Object value, boolean hasCategory) {
        if (!(value instanceof Map<?, ?> finding)
                || !finding.keySet()
                        .equals(
                                hasCategory
                                        ? java.util.Set.of("found", "category", "evidence")
                                        : java.util.Set.of("found", "evidence"))
                || !(finding.get("found") instanceof Boolean)
                || !finding.containsKey("evidence")
                || !(finding.get("evidence") == null || finding.get("evidence") instanceof String)) {
            return false;
        }
        return !hasCategory
                || (finding.containsKey("category")
                        && (finding.get("category") == null || finding.get("category") instanceof String));
    }

    private static boolean needsReview(Map<String, Object> result) {
        return !"low".equals(result.get("risk"))
                || Boolean.TRUE.equals(((Map<?, ?>) result.get("contactInfo")).get("found"))
                || Boolean.TRUE.equals(((Map<?, ?>) result.get("prohibited")).get("found"))
                || Boolean.TRUE.equals(result.get("categoryMismatch"))
                || Boolean.TRUE.equals(result.get("swapIntentInGivePost"))
                || (result.get("qualityIssue") instanceof String issue && !issue.isBlank());
    }

    private void store(ItemScreeningRequestedEvent event, String risk, String result) {
        jdbcClient
                .sql("""
                        UPDATE items SET ai_risk = :risk,
                            ai_result = COALESCE(ai_result, '{}'::jsonb) || CAST(:result AS jsonb)
                                || CASE WHEN ai_result ->> 'flag' = 'REVIEW'
                                    THEN jsonb_build_object('flag', 'REVIEW', 'reason', ai_result ->> 'reason')
                                    ELSE '{}'::jsonb END
                        WHERE id = :itemId AND donor_id = :donorId AND category_id = :categoryId
                            AND offer_type = :offerType AND title = :title AND description = :description
                            AND desired_item IS NOT DISTINCT FROM :desiredItem
                            AND condition = :condition AND status = 'PENDING_REVIEW' AND is_deleted = FALSE
                            """)
                .param("risk", risk)
                .param("result", result)
                .param("itemId", event.itemId())
                .param("donorId", event.donorId())
                .param("categoryId", event.categoryId())
                .param("offerType", event.offerType())
                .param("title", event.title())
                .param("description", event.description())
                .param("desiredItem", event.desiredItem())
                .param("condition", event.condition())
                .update();
    }

    private void logCall(ItemScreeningRequestedEvent event, int attempt, String model, String outcome, long started) {
        Map<String, Object> data = new HashMap<>();
        data.put("attempt", attempt);
        data.put("outcome", outcome);
        data.put("durationMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
        if (model != null) {
            data.put("model", model);
        }
        activityLogService.log("AiUsage", event.donorId(), "ITEM", event.itemId(), data, null);
    }
}
