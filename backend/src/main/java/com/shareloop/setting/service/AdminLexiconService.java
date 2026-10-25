package com.shareloop.setting.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminLexiconEntry;
import com.shareloop.setting.dto.AdminLexiconRequest;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminLexiconService {

    private static final String ATTR_GROUP = "BANNED_KEYWORD";
    private static final List<String> LEVELS = List.of("BLOCK", "REVIEW");
    private static final List<String> MATCH_MODES = List.of("WORD", "PHRASE", "REGEX");
    private static final List<String> SCOPES = List.of("POST", "CHAT", "MESSAGE", "BOTH");

    private static final String LOG_TARGET_TYPE = "LEXICON";

    private final WebsiteAttributeRepository repository;
    private final ActivityLogService activityLogService;

    public AdminLexiconService(WebsiteAttributeRepository repository, ActivityLogService activityLogService) {
        this.repository = repository;
        this.activityLogService = activityLogService;
    }

    public List<AdminLexiconEntry> listAll() {
        return repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                .map(AdminLexiconService::toResponse)
                .toList();
    }

    /**
     * Thêm từ khoá cấm vào cuối danh sách và ghi nhật ký LEXICON_CREATE (FR-115).
     *
     * @throws BusinessException SETTING_LEXICON_INVALID_VALUE, SETTING_LEXICON_DUPLICATE
     */
    @Transactional
    public AdminLexiconEntry create(AdminLexiconRequest request, Long adminId) {
        NormalizedRule rule = normalizeAndValidate(request);
        ensureUnique(rule, null);
        int sortOrder = repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                        .mapToInt(WebsiteAttribute::getSortOrder)
                        .max()
                        .orElse(0)
                + 10;
        WebsiteAttribute saved = saveUnique(
                new WebsiteAttribute(ATTR_GROUP, uniqueAttrKey(rule), rule.keyword(), toExtra(rule), sortOrder));
        logAction(ActivityType.LEXICON_CREATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Sửa từ khoá cấm và ghi nhật ký LEXICON_UPDATE (FR-115).
     *
     * @throws BusinessException SETTING_LEXICON_NOT_FOUND, SETTING_LEXICON_INVALID_VALUE, SETTING_LEXICON_DUPLICATE
     */
    @Transactional
    public AdminLexiconEntry update(long id, AdminLexiconRequest request, Long adminId) {
        WebsiteAttribute attribute = requireEntry(id);
        NormalizedRule rule = normalizeAndValidate(request);
        ensureUnique(rule, id);
        attribute.updateLexicon(uniqueAttrKey(rule), rule.keyword(), toExtra(rule));
        WebsiteAttribute saved = saveUnique(attribute);
        logAction(ActivityType.LEXICON_UPDATE, adminId, saved);
        return toResponse(saved);
    }

    /**
     * Bật/tắt từ khoá cấm (không xoá cứng) và ghi nhật ký LEXICON_TOGGLE (FR-115).
     *
     * @throws BusinessException SETTING_LEXICON_NOT_FOUND, SETTING_LEXICON_DUPLICATE khi bật lại bị trùng
     */
    @Transactional
    public AdminLexiconEntry setActive(long id, boolean active, Long adminId) {
        WebsiteAttribute attribute = requireEntry(id);
        if (active) {
            NormalizedRule currentRule = toRule(attribute);
            ensureMatchable(currentRule.keyword(), currentRule.matchMode());
            ensureUnique(currentRule, id);
            attribute.activate();
        } else {
            attribute.deactivate();
        }
        WebsiteAttribute saved = repository.saveAndFlush(attribute);
        logAction(ActivityType.LEXICON_TOGGLE, adminId, saved);
        return toResponse(saved);
    }

    private void logAction(ActivityType type, Long adminId, WebsiteAttribute attribute) {
        NormalizedRule rule = toRule(attribute);
        Map<String, Object> data = new HashMap<>();
        data.put("term", rule.keyword());
        data.put("severity", rule.level());
        data.put("active", attribute.isActive());
        activityLogService.log(type.name(), adminId, LOG_TARGET_TYPE, attribute.getId(), data, null);
    }

    private WebsiteAttribute requireEntry(long id) {
        return repository
                .findById(id)
                .filter(attribute -> ATTR_GROUP.equals(attribute.getAttrGroup()))
                .orElseThrow(() -> new BusinessException(SettingErrorCode.SETTING_LEXICON_NOT_FOUND));
    }

    private void ensureUnique(NormalizedRule rule, Long ignoredId) {
        boolean duplicate = repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                .filter(attribute -> !Objects.equals(attribute.getId(), ignoredId))
                .map(AdminLexiconService::toRule)
                .anyMatch(existing -> duplicateKey(existing).equals(duplicateKey(rule)));
        if (duplicate) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_LEXICON_DUPLICATE,
                    "Keyword đã tồn tại trong group '%s' và scope '%s'.".formatted(rule.group(), rule.scope()));
        }
    }

    private static NormalizedRule normalizeAndValidate(AdminLexiconRequest request) {
        if (request == null) {
            throw invalid("Request không được để trống.");
        }
        String keyword = requiredText(request.keyword(), "keyword");
        String group = requiredText(request.group(), "group").toUpperCase(Locale.ROOT);
        String level = requiredText(request.level(), "level").toUpperCase(Locale.ROOT);
        String matchMode = request.matchMode() == null || request.matchMode().isBlank()
                ? "WORD"
                : request.matchMode().trim().toUpperCase(Locale.ROOT);
        String scope = requiredText(request.scope(), "scope").toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw invalid("level chỉ nhận BLOCK hoặc REVIEW.");
        }
        if (!MATCH_MODES.contains(matchMode)) {
            throw invalid("matchMode chỉ nhận WORD, PHRASE hoặc REGEX.");
        }
        if (!SCOPES.contains(scope)) {
            throw invalid("scope chỉ nhận POST, CHAT, MESSAGE hoặc BOTH.");
        }
        ensureMatchable(keyword, matchMode);
        List<String> exceptions = request.exceptions() == null
                ? List.of()
                : request.exceptions().stream()
                        .map(exception -> requiredText(exception, "exception"))
                        .distinct()
                        .toList();
        return new NormalizedRule(keyword, group, level, matchMode, exceptions, scope);
    }

    // Luật không biên dịch được sẽ làm hỏng mọi lần quét bài và chat, nên chặn ngay khi lưu hoặc bật lại.
    private static void ensureMatchable(String keyword, String matchMode) {
        try {
            LexiconMatcher.validateRule(keyword, matchMode);
        } catch (IllegalArgumentException exception) {
            throw invalid(
                    "REGEX".equals(matchMode)
                            ? "keyword không phải biểu thức REGEX hợp lệ."
                            : "keyword không dùng được để so khớp.");
        }
    }

    private WebsiteAttribute saveUnique(WebsiteAttribute attribute) {
        try {
            return repository.saveAndFlush(attribute);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_LEXICON_DUPLICATE, "Keyword đã tồn tại trong group và scope này.");
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " không được để trống.");
        }
        return value.trim();
    }

    private static Map<String, Object> toExtra(NormalizedRule rule) {
        return Map.of(
                "category", rule.group(),
                "severity", rule.level(),
                "matchMode", rule.matchMode(),
                "exceptions", rule.exceptions(),
                "scope", rule.scope());
    }

    private static NormalizedRule toRule(WebsiteAttribute attribute) {
        Map<String, Object> extra = attribute.getExtra();
        return new NormalizedRule(
                attribute.getAttrValue(),
                stringValue(extra.get("category")),
                stringValue(extra.get("severity")),
                stringValue(extra.get("matchMode")),
                stringList(extra.get("exceptions")),
                stringValue(extra.get("scope")));
    }

    private static AdminLexiconEntry toResponse(WebsiteAttribute attribute) {
        NormalizedRule rule = toRule(attribute);
        return new AdminLexiconEntry(
                attribute.getId(),
                rule.keyword(),
                rule.group(),
                rule.level(),
                rule.matchMode(),
                rule.exceptions(),
                rule.scope(),
                attribute.getSortOrder(),
                attribute.isActive());
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static String duplicateKey(NormalizedRule rule) {
        return normalizeDuplicatePart(rule.keyword()) + "\u0000"
                + normalizeDuplicatePart(rule.group()) + "\u0000"
                + normalizeDuplicatePart(rule.scope());
    }

    private static String normalizeDuplicatePart(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String uniqueAttrKey(NormalizedRule rule) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(duplicateKey(rule).getBytes(StandardCharsets.UTF_8));
            return "LEX_" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(SettingErrorCode.SETTING_LEXICON_INVALID_VALUE, message);
    }

    private record NormalizedRule(
            String keyword, String group, String level, String matchMode, List<String> exceptions, String scope) {}
}
