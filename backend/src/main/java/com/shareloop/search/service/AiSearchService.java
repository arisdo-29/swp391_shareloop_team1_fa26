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
import com.shareloop.item.service.ItemService;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiSearchService.class);
    private static final int DATABASE_PAGE_SIZE = 100;
    private static final int MODEL_BATCH_SIZE = 100;
    private static final int MAX_RECOMMENDATIONS = 6;
    private static final Pattern SHIRT_REQUEST = Pattern.compile(
            "\\b(?:tim|kiem|can|muon|mua|xin)\\b(?:\\s+(?:cho|minh|toi|em|mot|cai|chiec|vai|bo))*\\s+ao\\b");
    private static final Pattern SHOE_REQUEST = Pattern.compile(
            "\\b(?:tim|kiem|can|muon|mua|xin)\\b(?:\\s+(?:cho|minh|toi|em|mot|doi|cai|chiec|vai|bo))*\\s+giay\\b");
    private static final Pattern SHIRT_TITLE = Pattern.compile(
            "(?<![a-z])(?:ao|so mi|t[ -]?shirt|hoodie|sweater|sweatshirt|cardigan|polo|blouse)(?![a-z])");
    private static final Pattern SHOE_TITLE = Pattern.compile("(?<![a-z])(?:giay|sneaker|loafer|boot|boots)(?![a-z])");
    private static final String FOUND_MESSAGE =
            "Mình đã xem nhu cầu của bạn và tìm được vài món đồ đang có trên ShareLoop có thể phù hợp. Bạn nhấn vào từng bài đăng để xem chi tiết nhé!";
    private static final String EMPTY_MESSAGE =
            "Mình đã xem các bài đăng đang có nhưng chưa tìm được món thật sự phù hợp. Bạn thử mô tả thêm công dụng hoặc điều bạn thích nhé!";
    private static final String OUT_OF_SCOPE_MESSAGE =
            "Mình chỉ có thể giúp bạn tìm đồ đang có trên ShareLoop. Bạn mô tả món đồ hoặc nhu cầu sử dụng, mình sẽ tìm giúp nhé!";
    private static final String UNAVAILABLE_MESSAGE =
            "Mình chưa thể tìm đồ bằng AI lúc này. Bạn vui lòng thử lại sau nhé!";

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

    public AiSearchResponse search(long userId, String query) {
        if (query == null
                || query.isBlank()
                || query.length() > 500
                || !ContactInfoDetector.detect(query).isEmpty()) {
            throw new BusinessException(SearchErrorCode.SEARCH_QUERY_INVALID);
        }
        if (!aiClient.isAvailable()) {
            return empty(userId, UNAVAILABLE_MESSAGE, true, false);
        }
        quotaService.checkAvailable(userId);
        long started = System.nanoTime();
        Duration timeout = Duration.ofSeconds(configService.getInt(ConfigKey.AI_SEARCH_TIMEOUT_SECONDS));
        Map<Long, String> categoryNames = categoryService.listActive().stream()
                .collect(Collectors.toMap(CategoryResponse::id, CategoryResponse::name));
        Map<Long, String> areaNames = areaService.listTree().stream()
                .flatMap(area ->
                        java.util.stream.Stream.concat(java.util.stream.Stream.of(area), area.children().stream()))
                .collect(Collectors.toMap(AreaResponse::id, AreaResponse::name));
        LinkedHashMap<Long, ItemResponse> shortlist = new LinkedHashMap<>();
        String reply = FOUND_MESSAGE;
        List<ItemResponse> pending = new ArrayList<>(MODEL_BATCH_SIZE);
        boolean called = false;
        try {
            for (int databasePage = 0; ; databasePage++) {
                PageResponse<ItemResponse> available =
                        itemService.searchApprovedForAi(null, null, "", null, null, databasePage, DATABASE_PAGE_SIZE);
                for (ItemResponse item : available.items()) {
                    if (item.expireAt() != null && !item.expireAt().isAfter(Instant.now())) {
                        continue;
                    }
                    pending.add(item);
                    if (pending.size() == MODEL_BATCH_SIZE) {
                        called = true;
                        Selection selection = select(query, pending, shortlist, categoryNames, areaNames, timeout);
                        if (!selection.inScope()) {
                            logCall(userId, "OUT_OF_SCOPE", started, false);
                            return empty(userId, OUT_OF_SCOPE_MESSAGE, false, true);
                        }
                        shortlist = selection.items();
                        reply = selection.message();
                        pending.clear();
                    }
                }
                if ((long) (databasePage + 1) * DATABASE_PAGE_SIZE >= available.totalElements()) {
                    break;
                }
            }
            if (!pending.isEmpty() || !called) {
                Selection selection = select(query, pending, shortlist, categoryNames, areaNames, timeout);
                if (!selection.inScope()) {
                    logCall(userId, "OUT_OF_SCOPE", started, false);
                    return empty(userId, OUT_OF_SCOPE_MESSAGE, false, true);
                }
                shortlist = selection.items();
                reply = selection.message();
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn(
                    "AI item recommendation unavailable ({})",
                    exception.getClass().getSimpleName());
            logCall(userId, "FAILED", started, false);
            return empty(userId, UNAVAILABLE_MESSAGE, true, false);
        }

        List<ItemResponse> verified = new ArrayList<>();
        for (Long id : shortlist.keySet()) {
            try {
                ItemResponse current = itemService.getItem(id, null);
                if ((current.expireAt() == null || current.expireAt().isAfter(Instant.now()))
                        && matchesExplicitRequest(query, current)) {
                    verified.add(current);
                }
            } catch (BusinessException exception) {
                // A selected item can be reserved, traded or hidden while Gemini is reading.
            }
        }
        boolean paid = !verified.isEmpty() && quotaService.consume(userId);
        logCall(userId, verified.isEmpty() ? "NO_RESULTS" : "SUCCESS", started, paid);
        return new AiSearchResponse(
                verified.isEmpty() ? EMPTY_MESSAGE : reply,
                new PageResponse<>(verified, 0, verified.size(), verified.size()),
                quotaService.getQuota(userId),
                paid ? configService.getInt(ConfigKey.AI_SEARCH_FEE) : 0,
                false,
                false);
    }

    private Selection select(
            String query,
            List<ItemResponse> batch,
            LinkedHashMap<Long, ItemResponse> previous,
            Map<Long, String> categoryNames,
            Map<Long, String> areaNames,
            Duration timeout)
            throws Exception {
        LinkedHashMap<Long, ItemResponse> offered = new LinkedHashMap<>(previous);
        for (ItemResponse item : batch) {
            offered.put(item.id(), item);
        }
        List<CatalogItem> catalog = offered.values().stream()
                .map(item -> new CatalogItem(item, categoryNames.get(item.categoryId()), areaNames.get(item.areaId())))
                .toList();
        AiResponse response = aiClient.recommend(query.trim(), mapper.writeValueAsString(catalog), timeout);
        JsonNode root = mapper.readTree(response.content());
        if (!root.isObject()
                || !root.path("inScope").isBoolean()
                || !root.path("itemIds").isArray()
                || !root.path("message").isTextual()) {
            throw new IllegalArgumentException("Invalid AI recommendation response");
        }
        if (!root.path("inScope").asBoolean()) {
            return new Selection(false, new LinkedHashMap<>(), OUT_OF_SCOPE_MESSAGE);
        }
        LinkedHashMap<Long, ItemResponse> selected = new LinkedHashMap<>();
        JsonNode ids = root.path("itemIds");
        if (ids.size() > MAX_RECOMMENDATIONS) {
            throw new IllegalArgumentException("Too many AI item recommendations");
        }
        for (JsonNode node : ids) {
            if (!node.isIntegralNumber() || !offered.containsKey(node.longValue())) {
                throw new IllegalArgumentException("AI referenced an unavailable item");
            }
            ItemResponse candidate = offered.get(node.longValue());
            if (matchesExplicitRequest(query, candidate)) {
                selected.putIfAbsent(node.longValue(), candidate);
            }
        }
        String message = root.path("message").asText().trim().replaceAll("\\s+", " ");
        if (message.isBlank()
                || message.length() > 240
                || !ContactInfoDetector.detect(message).isEmpty()) {
            message = FOUND_MESSAGE;
        }
        return new Selection(true, selected, message);
    }

    private AiSearchResponse empty(long userId, String message, boolean fallback, boolean outOfScope) {
        return new AiSearchResponse(
                message,
                new PageResponse<>(List.of(), 0, 0, 0),
                quotaService.getQuota(userId),
                0,
                fallback,
                outOfScope);
    }

    private boolean matchesExplicitRequest(String query, ItemResponse item) {
        String normalizedQuery = ContactInfoDetector.normalize(query).trim();
        boolean shirtOnly = (normalizedQuery.equals("ao")
                        || normalizedQuery.startsWith("ao ")
                        || SHIRT_REQUEST.matcher(normalizedQuery).find())
                && !normalizedQuery.matches(".*\\bquan ao\\b.*")
                && !normalizedQuery.matches(".*\\bgiay\\b.*");
        boolean shoeOnly = (normalizedQuery.equals("giay")
                        || normalizedQuery.startsWith("giay ")
                        || SHOE_REQUEST.matcher(normalizedQuery).find())
                && !normalizedQuery.matches(".*\\bao\\b.*");
        String normalizedTitle = ContactInfoDetector.normalize(item.title());
        Matcher shirt = SHIRT_TITLE.matcher(normalizedTitle);
        Matcher shoe = SHOE_TITLE.matcher(normalizedTitle);
        int shirtPosition = shirt.find() ? shirt.start() : Integer.MAX_VALUE;
        int shoePosition = shoe.find() ? shoe.start() : Integer.MAX_VALUE;
        return (!shirtOnly || shirtPosition < shoePosition) && (!shoeOnly || shoePosition < shirtPosition);
    }

    private void logCall(long userId, String outcome, long started, boolean paid) {
        activityLogService.log(
                "AI_USAGE",
                userId,
                "AI_SEARCH",
                null,
                Map.of(
                        "role",
                        "SEARCH",
                        "model",
                        aiClient.model(),
                        "billing",
                        "SUCCESS".equals(outcome) ? (paid ? "CREDIT" : "FREE") : "NONE",
                        "outcome",
                        outcome,
                        "durationMs",
                        Duration.ofNanos(System.nanoTime() - started).toMillis()),
                null);
    }

    private record Selection(boolean inScope, LinkedHashMap<Long, ItemResponse> items, String message) {}

    private record CatalogItem(
            Long id,
            String title,
            String description,
            String condition,
            String defectNote,
            String brand,
            String offerType,
            Long categoryId,
            String categoryName,
            Long areaId,
            String areaName,
            String operatingStatus) {
        CatalogItem(ItemResponse item, String categoryName, String areaName) {
            this(
                    item.id(),
                    item.title(),
                    item.description(),
                    item.condition() == null ? null : item.condition().name(),
                    item.defectNote(),
                    item.brand(),
                    item.offerType() == null ? null : item.offerType().name(),
                    item.categoryId(),
                    categoryName,
                    item.areaId(),
                    areaName,
                    item.operatingStatus() == null
                            ? null
                            : item.operatingStatus().name());
        }
    }
}
