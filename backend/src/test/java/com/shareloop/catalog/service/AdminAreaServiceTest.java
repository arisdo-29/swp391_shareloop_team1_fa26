package com.shareloop.catalog.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.dto.AdminAreaRequest;
import com.shareloop.catalog.entity.Area;
import com.shareloop.catalog.repository.AreaRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAreaServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    AreaRepository repository;

    @Mock
    ActivityLogService activityLogService;

    @InjectMocks
    AdminAreaService service;

    @Test
    void create_whenTopLevel_logsAreaCreate() {
        // given
        when(repository.saveAndFlush(any(Area.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 3L));

        // when
        service.create(new AdminAreaRequest("Hà Nội", null), ADMIN_ID);

        // then
        verify(activityLogService)
                .log(
                        ActivityType.AREA_CREATE.name(),
                        ADMIN_ID,
                        "AREA",
                        3L,
                        Map.<String, Object>of("name", "Hà Nội", "active", true),
                        null);
    }

    @Test
    void update_whenRenamed_logsAreaUpdate() {
        // given
        Area area = withId(new Area("Ha Noi", null, (short) 1), 3L);
        when(repository.findById(3L)).thenReturn(Optional.of(area));
        when(repository.saveAndFlush(area)).thenReturn(area);

        // when
        service.update(3L, new AdminAreaRequest("Hà Nội", null), ADMIN_ID);

        // then
        verify(activityLogService)
                .log(
                        ActivityType.AREA_UPDATE.name(),
                        ADMIN_ID,
                        "AREA",
                        3L,
                        Map.<String, Object>of("name", "Hà Nội", "active", true),
                        null);
    }

    // id do DB sinh nên unit test phải tự gán để kiểm targetId.
    private static Area withId(Area area, Long id) {
        ReflectionTestUtils.setField(area, "id", id);
        return area;
    }
}
