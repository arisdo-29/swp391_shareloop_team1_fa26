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
import java.util.Map;
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
            List<AttributeSuggestion> attributes = attributes(fields, normalizedTitle, postLexicon);
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
        result.add(suggest("Loại", grounded(fields.path("itemType").asText(""), title, lexicon), "…", false));
        result.add(suggest("Thương hiệu", grounded(fields.path("brand").asText(""), title, lexicon), "…", false));
        result.add(suggest("Màu", grounded(fields.path("color").asText(""), title, lexicon), "…", false));
        result.add(suggest("Chất liệu", grounded(fields.path("material").asText(""), title, lexicon), "…", false));
        result.add(suggest(
                "Tình trạng gợi ý", grounded(fields.path("conditionHint").asText(""), title, lexicon), "…", true));
        result.add(new AttributeSuggestion("Kích thước", "… x … x … cm", true));
        result.add(new AttributeSuggestion("Khối lượng", "…", true));
        result.add(new AttributeSuggestion("Dung tích", "…", true));
        result.add(new AttributeSuggestion("Thời gian đã dùng", "…", true));
        result.add(new AttributeSuggestion("Tình trạng hoạt động", "…", true));
        result.add(suggest("Phụ kiện", grounded(fields.path("accessories").asText(""), title, lexicon), "…", false));
        result.add(suggest("Lỗi", grounded(fields.path("defects").asText(""), title, lexicon), "…", false));
        return List.copyOf(result);
    }

    private AttributeSuggestion suggest(String name, String value, String placeholder, boolean alwaysUserInput) {
        boolean missing = value.isBlank();
        return new AttributeSuggestion(name, missing ? placeholder : value, alwaysUserInput || missing);
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
