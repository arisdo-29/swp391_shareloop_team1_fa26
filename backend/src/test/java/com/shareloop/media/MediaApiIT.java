package com.shareloop.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.media.entity.MediaFile;
import com.shareloop.media.entity.MediaStatus;
import com.shareloop.media.repository.MediaFileRepository;
import com.shareloop.media.service.MediaService;
import com.shareloop.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class MediaApiIT extends IntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    MediaFileRepository repository;

    @Autowired
    MediaService service;

    @MockitoBean
    StorageService storage;

    @Test
    void uploadRequiresAuthenticationAndAttachKeepsOwner() throws Exception {
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", jpg);
        mvc.perform(multipart("/api/v1/media").file(file)).andExpect(status().isUnauthorized());

        when(storage.save(jpg, "jpg")).thenReturn("integration-test.jpg");
        mvc.perform(multipart("/api/v1/media")
                        .file(file)
                        .header("Authorization", "Bearer " + jwtService.issue(77L, false)))
                .andExpect(status().isCreated());

        MediaFile media = repository.findAll().stream()
                .filter(row -> row.getStorageKey().equals("integration-test.jpg"))
                .findFirst()
                .orElseThrow();
        assertThat(media.getCreatedBy()).isEqualTo(77L);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        service.attach(List.of(media.getId()), 77L);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.ATTACHED);

        when(storage.read("integration-test.jpg")).thenReturn(jpg);
        mvc.perform(get("/api/v1/media/{id}", media.getId())).andExpect(status().isOk());
    }
}
