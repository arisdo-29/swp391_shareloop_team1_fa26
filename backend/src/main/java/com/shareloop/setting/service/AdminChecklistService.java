package com.shareloop.setting.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminChecklistEntry;
import com.shareloop.setting.dto.AdminChecklistReorderRequest;
import com.shareloop.setting.dto.AdminChecklistRequest;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminChecklistService {

    private static final String ATTR_GROUP = "CHECKLIST";

    private final WebsiteAttributeRepository repository;

    public AdminChecklistService(WebsiteAttributeRepository repository) {
        this.repository = repository;
    }

    public List<AdminChecklistEntry> listAll() {
        return repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                .map(AdminChecklistService::toResponse)
                .toList();
    }

    @Transactional
    public AdminChecklistEntry create(AdminChecklistRequest request) {
        String question = validateQuestion(request);
        int sortOrder = repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                        .mapToInt(WebsiteAttribute::getSortOrder)
                        .max()
                        .orElse(0)
                + 10;
        String attrKey = "chk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        WebsiteAttribute attribute = new WebsiteAttribute(ATTR_GROUP, attrKey, question, null, sortOrder);
        return toResponse(repository.saveAndFlush(attribute));
    }

    @Transactional
    public AdminChecklistEntry update(long id, AdminChecklistRequest request) {
        WebsiteAttribute attribute = requireEntry(id);
        String question = validateQuestion(request);
        attribute.updateChecklist(question);
        return toResponse(repository.saveAndFlush(attribute));
    }

    @Transactional
    public AdminChecklistEntry setActive(long id, boolean active) {
        WebsiteAttribute attribute = requireEntry(id);
        if (active) {
            attribute.activate();
        } else {
            // BR-A06: luôn có ít nhất 1 mục đang dùng (is_active = true)
            long activeCount = repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                    .filter(item -> !item.getId().equals(id))
                    .count();
            if (activeCount == 0) {
                throw new BusinessException(SettingErrorCode.SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED);
            }
            attribute.deactivate();
        }
        return toResponse(repository.saveAndFlush(attribute));
    }

    @Transactional
    public List<AdminChecklistEntry> reorder(AdminChecklistReorderRequest request) {
        if (request == null || request.itemIds() == null || request.itemIds().isEmpty()) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE, "Danh sách id sắp xếp không được để trống.");
        }

        List<Long> itemIds = request.itemIds();
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE,
                    "Danh sách id không được chứa giá trị trùng lặp.");
        }

        List<WebsiteAttribute> allEntries = repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP);
        if (itemIds.size() != allEntries.size()) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE,
                    "Danh sách sắp xếp phải bao gồm đầy đủ tất cả các mục checklist.");
        }

        Map<Long, WebsiteAttribute> entryMap =
                allEntries.stream().collect(Collectors.toMap(WebsiteAttribute::getId, Function.identity()));

        for (Long id : itemIds) {
            if (!entryMap.containsKey(id)) {
                throw new BusinessException(
                        SettingErrorCode.SETTING_CHECKLIST_NOT_FOUND, "Không tìm thấy mục checklist với id: " + id);
            }
        }

        int currentOrder = 10;
        for (Long id : itemIds) {
            WebsiteAttribute entry = entryMap.get(id);
            entry.updateSortOrder(currentOrder);
            currentOrder += 10;
        }

        repository.saveAllAndFlush(allEntries);

        return repository.findByAttrGroupOrderBySortOrderAscIdAsc(ATTR_GROUP).stream()
                .map(AdminChecklistService::toResponse)
                .toList();
    }

    private WebsiteAttribute requireEntry(long id) {
        return repository
                .findById(id)
                .filter(attribute -> ATTR_GROUP.equals(attribute.getAttrGroup()))
                .orElseThrow(() -> new BusinessException(SettingErrorCode.SETTING_CHECKLIST_NOT_FOUND));
    }

    private static String validateQuestion(AdminChecklistRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CHECKLIST_INVALID_VALUE, "Nội dung câu hỏi không được để trống.");
        }
        return request.question().trim();
    }

    private static AdminChecklistEntry toResponse(WebsiteAttribute attribute) {
        return new AdminChecklistEntry(
                attribute.getId(),
                attribute.getAttrKey(),
                attribute.getAttrValue(),
                attribute.getSortOrder(),
                attribute.isActive());
    }
}
