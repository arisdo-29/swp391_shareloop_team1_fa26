package com.shareloop.catalog.service;

import com.shareloop.catalog.AreaErrorCode;
import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.mapper.AreaMapper;
import com.shareloop.catalog.repository.AreaRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AreaService {

    private final AreaRepository repository;
    private final AreaMapper mapper;

    public AreaService(AreaRepository repository, AreaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<AreaResponse> listTree() {
        List<Area> areas = repository.findByIsActiveTrueOrderByIdAsc();
        Map<Long, List<AreaResponse>> childrenByParent = areas.stream()
                .filter(area -> area.getLevel() == 2 && area.getParentId() != null)
                .collect(Collectors.groupingBy(
                        Area::getParentId,
                        Collectors.mapping(area -> mapper.toResponse(area, List.of()), Collectors.toList())));

        return areas.stream()
                .filter(area -> area.getLevel() == 1 && area.getParentId() == null)
                .map(area -> mapper.toResponse(area, childrenByParent.getOrDefault(area.getId(), List.of())))
                .toList();
    }

    /** Tên khu vực theo id cho trang Admin; khu vực đang ẩn vẫn có tên, chỉ khu vực đã xoá mềm mới trả rỗng. */
    public Optional<String> findName(long areaId) {
        return repository.findById(areaId).map(area -> area.getName());
    }

    /** Khu vực có tồn tại và đang hoạt động không. Module khác (user) gọi hàm này thay vì đọc bảng areas. */
    public boolean isActiveArea(long areaId) {
        return repository.existsByIdAndIsActiveTrue(areaId);
    }

    /**
     * Quận/huyện hợp lệ cho bài đăng: phải tồn tại, đang hoạt động (active), thuộc cấp 2 (quận/huyện)
     * và khu vực cha (tỉnh/thành) cũng đang hoạt động. Nếu không hợp lệ thì coi như không tìm thấy (404).
     */
    public AreaResponse getDistrict(long id) {
        Area area = repository
                .findById(id)
                .filter(a -> a.isActive() && a.getLevel() == 2 && a.getParentId() != null)
                .orElseThrow(() -> new BusinessException(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
        if (!repository.existsByIdAndIsActiveTrue(area.getParentId())) {
            throw new BusinessException(AreaErrorCode.CATALOG_AREA_NOT_FOUND);
        }
        return mapper.toResponse(area, List.of());
    }
}
