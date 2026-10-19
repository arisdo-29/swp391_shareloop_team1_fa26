package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.service.AreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "area-controller", description = "Tra cứu khu vực công khai")
@RestController
@RequestMapping("/api/v1/areas")
public class AreaController {

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    @Operation(
            summary = "Xem cây khu vực đang hoạt động",
            description = "Không cần đăng nhập. Trả về khu vực cấp thành phố và các khu vực con đang hoạt động.")
    @GetMapping
    public List<AreaResponse> list() {
        return areaService.listTree();
    }
}
