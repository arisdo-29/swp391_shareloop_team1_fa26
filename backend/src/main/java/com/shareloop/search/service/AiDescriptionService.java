package com.shareloop.search.service;

import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.ai.GeminiDescriptionClient;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.search.SearchErrorCode;
import com.shareloop.search.dto.AiDescriptionResponse;
import com.shareloop.search.dto.AiDescriptionResponse.AttributeSuggestion;
import com.shareloop.search.repository.AiQuotaAccountRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiDescriptionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiDescriptionService.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String FAILURE_MESSAGE = "Chưa gợi ý được, bạn tự viết mô tả nhé.";
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private final GeminiDescriptionClient client;
    private final AiQuotaAccountRepository accountRepository;
    private final ActivityLogQueryService logQueryService;
    private final ActivityLogService logService;
    private final ConfigService configService;
    private final LexiconMatcher lexiconMatcher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AiDescriptionService(
            GeminiDescriptionClient client,
            AiQuotaAccountRepository accountRepository,
            ActivityLogQueryService logQueryService,
            ActivityLogService logService,
            ConfigService configService,
            LexiconMatcher lexiconMatcher,
            ObjectMapper mapper,
            Clock clock) {
        this.client = client;
        this.accountRepository = accountRepository;
        this.logQueryService = logQueryService;
        this.logService = logService;
        this.configService = configService;
        this.lexiconMatcher = lexiconMatcher;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AiDescriptionResponse describe(long userId, String title) {
        if (title == null
                || title.isBlank()
                || title.length() > 150
                || !ContactInfoDetector.detect(title).isEmpty()) {
            throw new BusinessException(SearchErrorCode.AI_DESCRIPTION_TITLE_INVALID);
        }
        String normalizedTitle = title.trim().replaceAll("\\s+", " ");
        List<LexiconEntry> postLexicon = configService.lexicon().stream()
                .filter(entry -> "POST".equalsIgnoreCase(entry.scope()) || "BOTH".equalsIgnoreCase(entry.scope()))
                .toList();
        if (!lexiconMatcher.match(normalizedTitle, postLexicon).isEmpty()) {
            throw new BusinessException(SearchErrorCode.AI_DESCRIPTION_TITLE_INVALID);
        }
        accountRepository
                .lockByUserId(userId)
                .orElseThrow(() -> new BusinessException(SearchErrorCode.SEARCH_ACCOUNT_NOT_FOUND));
        LocalDate today = LocalDate.ofInstant(clock.instant(), VIETNAM_ZONE);
        Instant start = today.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
        int limit = configService.getInt(ConfigKey.AI_DESCRIBE_PER_DAY);
        long used = logQueryService.countAiDescriptions(userId, start, end);
        if (used >= limit) throw new BusinessException(SearchErrorCode.AI_DESCRIPTION_LIMIT_REACHED);
        if (!client.isAvailable()) return failed(userId, limit - (int) used, "UNAVAILABLE", 0);

        long started = System.nanoTime();
        try {
            JsonNode fields = mapper.readTree(client.describe(
                    normalizedTitle, Duration.ofSeconds(configService.getInt(ConfigKey.AI_DESCRIBE_TIMEOUT_SECONDS))));
            if (!fields.isObject()
                    || !List.of("itemType", "brand", "color", "material", "conditionHint", "accessories", "defects")
                            .stream()
                            .allMatch(name -> fields.path(name).isTextual())) {
                throw new IllegalArgumentException("Description must match the expected JSON schema");
            }
            JsonNode suggestions = fields.path("suggestions");
            if (!suggestions.isArray() || suggestions.isEmpty() || suggestions.size() > 4) {
                throw new IllegalArgumentException("Description suggestions must contain one to four items");
            }
            List<AttributeSuggestion> attributes = attributes(fields, normalizedTitle, postLexicon);
            if (attributes.stream().noneMatch(AttributeSuggestion::requiresUserInput)) {
                throw new IllegalArgumentException("Description has no relevant details to confirm");
            }
            StringBuilder description =
                    new StringBuilder("Món đồ: ").append(normalizedTitle).append('.');
            for (AttributeSuggestion attribute : attributes) {
                description.append('\n').append(attribute.name()).append(": ").append(attribute.value());
            }
            log(userId, "AI_DESCRIBE", "SUCCESS", started);
            return new AiDescriptionResponse(
                    description.toString(),
                    attributes,
                    limit - (int) used - 1,
                    "Đây là bản nháp từ tên món đồ. Bạn hãy kiểm tra và sửa lại trước khi đăng nhé.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("AI item description interrupted");
            return failed(userId, limit - (int) used, "FAILED", started);
        } catch (Exception exception) {
            LOGGER.warn(
                    "AI item description unavailable ({})", exception.getClass().getSimpleName());
            return failed(userId, limit - (int) used, "FAILED", started);
        }
    }

    private AiDescriptionResponse failed(long userId, int remaining, String outcome, long started) {
        log(userId, "AI_DESCRIBE_ERROR", outcome, started);
        return new AiDescriptionResponse("", List.of(), remaining, FAILURE_MESSAGE);
    }

    private void log(long userId, String targetType, String outcome, long started) {
        logService.log(
                "AI_USAGE",
                userId,
                targetType,
                null,
                Map.of(
                        "role",
                        "DESCRIPTION",
                        "model",
                        client.model(),
                        "outcome",
                        outcome,
                        "durationMs",
                        started == 0
                                ? 0
                                : Duration.ofNanos(System.nanoTime() - started).toMillis()),
                null);
    }

    private List<AttributeSuggestion> attributes(JsonNode fields, String title, List<LexiconEntry> lexicon) {
        List<AttributeSuggestion> result = new ArrayList<>();
        addIfKnown(result, "Loại", fields.path("itemType").asText(""), title, lexicon);
        addIfKnown(result, "Thương hiệu", fields.path("brand").asText(""), title, lexicon);
        addIfKnown(result, "Màu", fields.path("color").asText(""), title, lexicon);
        addIfKnown(result, "Chất liệu", fields.path("material").asText(""), title, lexicon);
        addIfKnown(result, "Tình trạng theo tên", fields.path("conditionHint").asText(""), title, lexicon);
        addIfKnown(result, "Phụ kiện", fields.path("accessories").asText(""), title, lexicon);
        addIfKnown(result, "Lỗi", fields.path("defects").asText(""), title, lexicon);
        for (JsonNode suggestion : fields.path("suggestions")) {
            if (!suggestion.isObject()) continue;
            String name = safe(suggestion.path("name").asText(""), lexicon, 40);
            String question = safe(suggestion.path("question").asText(""), lexicon, 160);
            if (name.isBlank()
                    || question.isBlank()
                    || !question.endsWith("?")
                    || unrelatedCapacity(title, name + " " + question)
                    || NUMBER.matcher(name + " " + question).results().anyMatch(match -> !title.contains(match.group()))
                    || result.stream().anyMatch(attribute -> attribute.name().equalsIgnoreCase(name))) continue;
            result.add(new AttributeSuggestion(name, "…", true, question));
        }
        return List.copyOf(result);
    }

    private void addIfKnown(
            List<AttributeSuggestion> result, String name, String value, String title, List<LexiconEntry> lexicon) {
        String known = grounded(value, title, lexicon);
        if (!known.isBlank()) result.add(new AttributeSuggestion(name, known, false, null));
    }

    private boolean unrelatedCapacity(String title, String suggestion) {
        String subject = title.toLowerCase(Locale.ROOT);
        String prompt = suggestion.toLowerCase(Locale.ROOT);
        return prompt.contains("dung tích")
                && (subject.contains("đồng hồ") || subject.contains("trang sức") || subject.contains("nhẫn"));
    }

    private String grounded(String value, String title, List<LexiconEntry> lexicon) {
        String cleaned = safe(value, lexicon, 80);
        return cleaned.isBlank()
                        || !ContactInfoDetector.normalize(title).contains(ContactInfoDetector.normalize(cleaned))
                ? ""
                : cleaned;
    }

    private String safe(String value, List<LexiconEntry> lexicon, int maxLength) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > maxLength
                || !ContactInfoDetector.detect(cleaned).isEmpty()
                || !lexiconMatcher.match(cleaned, lexicon).isEmpty()) return "";
        return cleaned;
    }
}
