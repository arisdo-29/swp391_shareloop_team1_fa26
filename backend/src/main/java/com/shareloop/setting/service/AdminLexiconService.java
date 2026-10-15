package com.shareloop.setting.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminLexiconEntry;
import com.shareloop.setting.dto.AdminLexiconRequest;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final WebsiteAttributeRepository repository;

    public AdminLexiconService(WebsiteAttributeRepository repository) {
        this.repository = repository;
    }

    public List<AdminLexiconEntry> listAll() {
        return repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                .map(AdminLexiconService::toResponse)
                .toList();
    }

    @Transactional
    public AdminLexiconEntry create(AdminLexiconRequest request) {
        NormalizedRule rule = normalizeAndValidate(request);
        ensureUnique(rule, null);
        int sortOrder = repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                        .mapToInt(WebsiteAttribute::getSortOrder)
                        .max()
                        .orElse(0)
                + 10;
        WebsiteAttribute saved = saveUnique(
                new WebsiteAttribute(ATTR_GROUP, uniqueAttrKey(rule), rule.keyword(), toExtra(rule), sortOrder));
        return toResponse(saved);
    }

    @Transactional
    public AdminLexiconEntry update(long id, AdminLexiconRequest request) {
        WebsiteAttribute attribute = requireEntry(id);
        NormalizedRule rule = normalizeAndValidate(request);
        ensureUnique(rule, id);
        attribute.updateLexicon(uniqueAttrKey(rule), rule.keyword(), toExtra(rule));
        return toResponse(saveUnique(attribute));
    }

    @Transactional
    public AdminLexiconEntry setActive(long id, boolean active) {
        WebsiteAttribute attribute = requireEntry(id);
        if (active) {
            NormalizedRule currentRule = toRule(attribute);
            ensureUnique(currentRule, id);
            attribute.activate();
        } else {
            attribute.deactivate();
        }
        return toResponse(repository.saveAndFlush(attribute));
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
        List<String> exceptions = request.exceptions() == null
                ? List.of()
                : request.exceptions().stream()
                        .map(exception -> requiredText(exception, "exception"))
                        .distinct()
                        .toList();
        return new NormalizedRule(keyword, group, level, matchMode, exceptions, scope);
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
