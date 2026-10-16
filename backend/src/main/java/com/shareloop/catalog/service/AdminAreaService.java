package com.shareloop.catalog.service;

import com.shareloop.catalog.AreaErrorCode;
import com.shareloop.catalog.dto.AdminAreaRequest;
import com.shareloop.catalog.dto.AdminAreaResponse;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.repository.AreaRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAreaService {
    private final AreaRepository repository;

    public AdminAreaService(AreaRepository repository) {
        this.repository = repository;
    }

    public List<AdminAreaResponse> listAll() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(AdminAreaService::toResponse)
                .toList();
    }

    @Transactional
    public AdminAreaResponse create(AdminAreaRequest request) {
        short level = validateParent(request.parentId(), null);
        return toResponse(repository.saveAndFlush(new Area(request.name().trim(), request.parentId(), level)));
    }

    @Transactional
    public AdminAreaResponse update(long id, AdminAreaRequest request) {
        Area area = requireArea(id);
        short level = validateParent(request.parentId(), id);
        if (level == 2 && repository.existsByParentId(id)) {
            throw new BusinessException(AreaErrorCode.CATALOG_AREA_HAS_CHILDREN);
        }
        area.update(request.name().trim(), request.parentId(), level);
        return toResponse(repository.saveAndFlush(area));
    }

    @Transactional
    public AdminAreaResponse setActive(long id, boolean active) {
        Area area = requireArea(id);
        if (active) {
            validateParent(area.getParentId(), id);
            area.activate();
        } else {
            area.deactivate();
            repository.findByParentId(id).forEach(Area::deactivate);
        }
        return toResponse(repository.saveAndFlush(area));
    }

    private short validateParent(Long parentId, Long areaId) {
        if (parentId == null) {
            return 1;
        }
        if (parentId.equals(areaId)) {
            throw invalidParent();
        }
        requireActiveParent(parentId);
        return 2;
    }

    private void requireActiveParent(long parentId) {
        Area parent = repository.findById(parentId).orElseThrow(this::invalidParent);
        if (!parent.isActive() || parent.getLevel() != 1 || parent.getParentId() != null) {
            throw invalidParent();
        }
    }

    private Area requireArea(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }

    private BusinessException invalidParent() {
        return new BusinessException(
                AreaErrorCode.CATALOG_INVALID_AREA_PARENT,
                AreaErrorCode.CATALOG_INVALID_AREA_PARENT.defaultMessage(),
                "parentId",
                Map.of());
    }

    private static AdminAreaResponse toResponse(Area area) {
        return new AdminAreaResponse(
                area.getId(), area.getName(), area.getParentId(), area.getLevel(), area.isActive());
    }
}
