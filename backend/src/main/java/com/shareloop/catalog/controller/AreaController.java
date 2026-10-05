package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.service.AreaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/areas")
public class AreaController {

    private final AreaService areaService;

    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    @GetMapping
    public List<AreaResponse> list() {
        return areaService.listTree();
    }
}
