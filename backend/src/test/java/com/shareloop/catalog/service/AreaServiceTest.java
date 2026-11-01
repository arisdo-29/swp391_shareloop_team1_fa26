package com.shareloop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.AreaErrorCode;
import com.shareloop.catalog.dto.AreaResponse;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.mapper.AreaMapper;
import com.shareloop.catalog.repository.AreaRepository;
import com.shareloop.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AreaServiceTest {

    @Mock
    AreaRepository repository;

    @Spy
    AreaMapper mapper = new AreaMapper();

    @InjectMocks
    AreaService service;

    @Test
    @DisplayName("getDistrict: trả về quận/huyện hợp lệ khi là cấp 2, active và tỉnh/thành active")
    void getDistrict_validActiveDistrict_returnsResponse() {
        Area district = new Area("Quận 1", 1L, (short) 2);
        ReflectionTestUtils.setField(district, "id", 10L);
        when(repository.findById(10L)).thenReturn(Optional.of(district));
        when(repository.existsByIdAndIsActiveTrue(1L)).thenReturn(true);

        AreaResponse response = service.getDistrict(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Quận 1");
        assertThat(response.level()).isEqualTo((short) 2);
        assertThat(response.parentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getDistrict: ném CATALOG_AREA_NOT_FOUND khi id không tồn tại")
    void getDistrict_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDistrict(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }

    @Test
    @DisplayName("getDistrict: ném CATALOG_AREA_NOT_FOUND khi khu vực là cấp 1 (tỉnh/thành)")
    void getDistrict_level1City_throwsException() {
        Area city = new Area("TP. Hồ Chí Minh", null, (short) 1);
        ReflectionTestUtils.setField(city, "id", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(city));

        assertThatThrownBy(() -> service.getDistrict(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }

    @Test
    @DisplayName("getDistrict: ném CATALOG_AREA_NOT_FOUND khi quận bị ẩn (inactive)")
    void getDistrict_inactiveDistrict_throwsException() {
        Area district = new Area("Quận 1", 1L, (short) 2);
        ReflectionTestUtils.setField(district, "id", 10L);
        district.deactivate();
        when(repository.findById(10L)).thenReturn(Optional.of(district));

        assertThatThrownBy(() -> service.getDistrict(10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }

    @Test
    @DisplayName("getDistrict: ném CATALOG_AREA_NOT_FOUND khi tỉnh/thành cha bị ẩn")
    void getDistrict_inactiveParentCity_throwsException() {
        Area district = new Area("Quận 1", 1L, (short) 2);
        ReflectionTestUtils.setField(district, "id", 10L);
        when(repository.findById(10L)).thenReturn(Optional.of(district));
        when(repository.existsByIdAndIsActiveTrue(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.getDistrict(10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(AreaErrorCode.CATALOG_AREA_NOT_FOUND));
    }
}
