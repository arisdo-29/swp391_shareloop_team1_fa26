package com.shareloop.catalog.mapper;

import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.entity.Area;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AreaMapper {

    public AreaResponse toResponse(Area area, List<AreaResponse> children) {
        return new AreaResponse(area.getId(), area.getName(), area.getParentId(), area.getLevel(), children);
    }
}
