package com.shareloop.catalog.service;

import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.mapper.AreaMapper;
import com.shareloop.catalog.repository.AreaRepository;
import java.util.List;
import java.util.Map;
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
}
