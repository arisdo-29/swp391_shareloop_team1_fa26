package com.shareloop.search.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.ai.AiResponse;
import com.shareloop.integration.ai.AiSearchClient;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiSearchFilters;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSearchService.class);
    private static final Set<String> FILTER_KEYS = Set.of("keywords", "categoryId", "areaId", "condition", "offerType");

    private final AiSearchClient aiClient;
    private final CategoryService categoryService;
    private final AreaService areaService;
    private final ItemService itemService;
    private final ConfigService configService;
    private final AiQuotaService quotaService;
    private final ActivityLogService activityLogService;
    private final ObjectMapper mapper;

    public AiSearchService(
            AiSearchClient aiClient,
            CategoryService categoryService,
            AreaService areaService,
            ItemService itemService,
            ConfigService configService,
            AiQuotaService quotaService,
            ActivityLogService activityLogService,
            ObjectMapper mapper) {
        this.aiClient = aiClient;
        this.categoryService = categoryService;
        this.areaService = areaService;
        this.itemService = itemService;
        this.configService = configService;
        this.quotaService = quotaService;
        this.activityLogService = activityLogService;
        this.mapper = mapper;
    }

    public AiSearchResponse search(long userId, String query, int page, int size) {
        if (query == null
                || query.isBlank()
                || query.length() > 500
                || !ContactInfoDetector.detect(query).isEmpty()
                || page < 0
                || size < 1
                || size > 100) {
            throw new BusinessException(SearchErrorCode.SEARCH_QUERY_INVALID);
        }
        if (!aiClient.isAvailable()) {
            LOGGER.warn("AI search fallback: provider API key is unavailable");
            return fallback(userId, query, page, size);
        }

        List<CategoryResponse> categories = categoryService.listActive();
        List<AreaResponse> districts = areaService.listTree().stream()
                .flatMap(province -> province.children().stream())
                .toList();
        String allowedFilters = mapper.writeValueAsString(Map.of(
                "categories",
                        categories.stream()
                                .map(category -> Map.of("id", category.id(), "name", category.name()))
                                .toList(),
                "districts",
                        districts.stream()
                                .map(area -> Map.of("id", area.id(), "name", area.name()))
                                .toList()));
        Set<Long> categoryIds = categories.stream().map(CategoryResponse::id).collect(Collectors.toSet());
        Set<Long> districtIds = districts.stream().map(AreaResponse::id).collect(Collectors.toSet());

        Duration timeout = Duration.ofSeconds(configService.getInt(ConfigKey.AI_SEARCH_TIMEOUT_SECONDS));
        quotaService.checkAvailable(userId);
        long started = System.nanoTime();
        AiResponse response;
        try {
            response = aiClient.extract(query.trim(), allowedFilters, timeout);
        } catch (HttpTimeoutException exception) {
            LOGGER.warn("AI search fallback: provider request timed out");
            logCall(userId, aiClient.model(), "TIMEOUT", started, false);
            return fallback(userId, query, page, size);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("AI search fallback: provider request was interrupted");
            logCall(userId, aiClient.model(), "INTERRUPTED", started, false);
            return fallback(userId, query, page, size);
        } catch (Exception exception) {
            LOGGER.warn("AI search fallback: provider call failed ({})", safeFailureDetail(exception));
            logCall(userId, aiClient.model(), "FAILED", started, false);
            return fallback(userId, query, page, size);
        }

        AiSearchFilters filters;
        try {
            filters = validate(response.content(), categoryIds, districtIds);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "AI search fallback: provider response failed validation ({})",
                    exception.getClass().getSimpleName());
            logCall(userId, response.model(), "INVALID_RESPONSE", started, false);
            return fallback(userId, query, page, size);
        }
        PageResponse<ItemResponse> results = itemService.searchApprovedForAi(
                filters.categoryId(),
                filters.areaId(),
                filters.keywords(),
                filters.condition(),
                filters.offerType(),
                page,
                size);
        boolean broadened = false;
        if (results.totalElements() == 0
                && filters.keywords() != null
                && (filters.categoryId() != null
                        || filters.areaId() != null
                        || filters.condition() != null
                        || filters.offerType() != null)) {
            PageResponse<ItemResponse> keywordResults =
                    itemService.searchApprovedForAi(null, null, filters.keywords(), null, null, page, size);
            if (keywordResults.totalElements() > 0) {
                results = keywordResults;
                broadened = true;
            }
        }
        boolean hasResults = !results.items().isEmpty();
        boolean paid = hasResults && quotaService.consume(userId);
        logCall(userId, response.model(), hasResults ? "SUCCESS" : "NO_RESULTS", started, paid);
        return new AiSearchResponse(
                filters,
                results,
                quotaService.getQuota(userId),
                paid ? configService.getInt(ConfigKey.AI_SEARCH_FEE) : 0,
                false,
                broadened);
    }

    private AiSearchResponse fallback(long userId, String query, int page, int size) {
        String keywords = query.trim();
        return new AiSearchResponse(
                new AiSearchFilters(keywords, null, null, null, null),
                itemService.searchApprovedForAi(null, null, keywords, null, null, page, size),
                quotaService.getQuota(userId),
                0,
                true,
                false);
    }

    private static String safeFailureDetail(Exception exception) {
        String message = exception.getMessage();
        if (message != null
                && (message.matches("OpenRouter API returned HTTP [1-5][0-9]{2}")
                        || message.equals("OpenRouter API returned no single tool call")
                        || message.equals("OpenRouter API returned invalid tool arguments")
                        || message.equals("OpenRouter API returned no JSON object"))) {
            return message;
        }
        if (exception.getCause() != null) {
            return exception.getClass().getSimpleName()
                    + " caused by "
                    + exception.getCause().getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName();
    }

    private AiSearchFilters validate(String content, Set<Long> categoryIds, Set<Long> districtIds) {
        JsonNode node = mapper.readTree(content);
        if (!node.isObject()
                || node.size() != FILTER_KEYS.size()
                || !FILTER_KEYS.stream().allMatch(node::has)) {
            throw new IllegalArgumentException("Unexpected AI search response fields");
        }
        String keywords = nullableText(node.path("keywords"));
        if (keywords != null && keywords.length() > 120) {
            throw new IllegalArgumentException("Search keywords are too long");
        }
        Long categoryId = nullableId(node.path("categoryId"), categoryIds);
        Long areaId = nullableId(node.path("areaId"), districtIds);
        ItemCondition condition = nullableEnum(node.path("condition"), ItemCondition.class);
        OfferType offerType = nullableEnum(node.path("offerType"), OfferType.class);
        return new AiSearchFilters(keywords, categoryId, areaId, condition, offerType);
    }

    private static String nullableText(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Expected text filter");
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static Long nullableId(JsonNode node, Set<Long> allowedIds) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || node.asLong() <= 0 || !allowedIds.contains(node.asLong())) {
            throw new IllegalArgumentException("Unknown filter ID");
        }
        return node.asLong();
    }

    private static <T extends Enum<T>> T nullableEnum(JsonNode node, Class<T> type) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Expected enum filter");
        }
        return Enum.valueOf(type, node.asText());
    }

    private void logCall(long userId, String model, String outcome, long started, boolean paid) {
        activityLogService.log(
                "AI_USAGE",
                userId,
                "AI_SEARCH",
                null,
                Map.of(
                        "role",
                        "SEARCH",
                        "model",
                        model,
                        "billing",
                        "SUCCESS".equals(outcome) ? (paid ? "CREDIT" : "FREE") : "NONE",
                        "outcome",
                        outcome,
                        "durationMs",
                        Duration.ofNanos(System.nanoTime() - started).toMillis()),
                null);
    }
}
