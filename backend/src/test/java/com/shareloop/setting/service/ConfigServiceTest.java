package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {

    private final WebsiteAttributeRepository repository = mock(WebsiteAttributeRepository.class);
    private final ConfigService service = new ConfigService(repository);

    @Test
    void listIncludesEveryKeyAndUsesDefaultWhenNoActiveValueExists() {
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CONFIG"))
                .thenReturn(List.of());

        var configs = service.listConfigs();

        assertThat(configs).hasSize(ConfigKey.values().length);
        assertThat(configs).anySatisfy(config -> {
            assertThat(config.key()).isEqualTo("post_fee");
            assertThat(config.value()).isEqualTo("5");
            assertThat(config.defaultValue()).isEqualTo("5");
        });
    }

    @Test
    void listUsesActiveDatabaseValueAndKeepsDefaultValue() {
        var attribute = mock(WebsiteAttribute.class);
        when(attribute.getAttrKey()).thenReturn("post_fee");
        when(attribute.getAttrValue()).thenReturn("8");
        when(repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CONFIG"))
                .thenReturn(List.of(attribute));
        when(repository.findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", "post_fee"))
                .thenReturn(Optional.of(attribute));

        assertThat(service.listConfigs())
                .filteredOn(config -> config.key().equals("post_fee"))
                .singleElement()
                .satisfies(config -> {
                    assertThat(config.value()).isEqualTo("8");
                    assertThat(config.defaultValue()).isEqualTo("5");
                });
        assertThat(service.get(ConfigKey.POST_FEE)).isEqualTo("8");
    }
}
