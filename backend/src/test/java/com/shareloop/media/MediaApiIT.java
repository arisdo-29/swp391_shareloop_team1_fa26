package com.shareloop.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.media.entity.MediaFile;
import com.shareloop.media.entity.MediaStatus;
import com.shareloop.media.repository.MediaFileRepository;
import com.shareloop.media.service.MediaService;
import com.shareloop.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Autowired
    JdbcTemplate jdbc;

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

    @Test
    void readPublic_whenSuccess_returnsPublicCacheControlForSevenDays() throws Exception {
        // given
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        MediaFile media = new MediaFile("cached.jpg", "image/jpeg", 4);
        media.attach();
        media = repository.saveAndFlush(media);
        when(storage.read("cached.jpg")).thenReturn(jpg);

        // when
        var result = mvc.perform(get("/api/v1/media/{id}", media.getId()));

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=604800")))
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    void readPublic_whenStorageFileMissing_returnsNotFoundFileMissing() throws Exception {
        // given
        MediaFile media = new MediaFile("lost-on-render.jpg", "image/jpeg", 4);
        media.attach();
        media = repository.saveAndFlush(media);
        when(storage.read("lost-on-render.jpg")).thenThrow(new NoSuchFileException("lost-on-render.jpg"));

        // when
        var result = mvc.perform(get("/api/v1/media/{id}", media.getId()));

        // then
        result.andExpect(status().isNotFound())
                // Spring Security tự gắn no-cache cho response lỗi; chỉ cần chắc không bị cache 7 ngày.
                .andExpect(header().string("Cache-Control", not(containsString("max-age=604800"))))
                .andExpect(jsonPath("$.code").value("MEDIA_FILE_MISSING"));
    }

    @Test
    void readPublic_whenStorageFails_returnsStorageError() throws Exception {
        // given
        MediaFile media = new MediaFile("bucket-down.jpg", "image/jpeg", 4);
        media.attach();
        media = repository.saveAndFlush(media);
        when(storage.read("bucket-down.jpg")).thenThrow(new IOException("Cannot read bucket-down.jpg"));

        // when
        var result = mvc.perform(get("/api/v1/media/{id}", media.getId()));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("MEDIA_STORAGE_ERROR"));
    }

    @Test
    void readPublic_whenMediaNotAttached_returnsNotFound() throws Exception {
        // given: ảnh vừa tải lên, chưa gắn vào bài đăng hay ảnh đại diện
        MediaFile media = repository.saveAndFlush(new MediaFile("not-attached.jpg", "image/jpeg", 4));

        // when, then
        mvc.perform(get("/api/v1/media/{id}", media.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void readPublic_whenMediaIsPrivate_returnsNotFound() throws Exception {
        // given: ảnh riêng tư đã gắn (chat, giao nhận) không đọc được qua đường công khai
        MediaFile media = new MediaFile("private-chat.jpg", "image/jpeg", 4);
        media.attach();
        media.makePrivate();
        media = repository.saveAndFlush(media);

        // when, then
        mvc.perform(get("/api/v1/media/{id}", media.getId())).andExpect(status().isNotFound());
    }

    @Test
    void preview_whenOwnerOfUnattachedMedia_returnsImageAndOthersGetNotFound() throws Exception {
        // given
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        when(storage.save(jpg, "jpg")).thenReturn("preview-owner.jpg");
        when(storage.read("preview-owner.jpg")).thenReturn(jpg);
        mvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", jpg))
                        .header("Authorization", "Bearer " + jwtService.issue(81L, false)))
                .andExpect(status().isCreated());
        MediaFile media = repository.findAll().stream()
                .filter(row -> row.getStorageKey().equals("preview-owner.jpg"))
                .findFirst()
                .orElseThrow();

        // when, then: người tải lên xem được, người khác nhận 404, chưa đăng nhập nhận 401
        mvc.perform(get("/api/v1/media/{id}/preview", media.getId())
                        .header("Authorization", "Bearer " + jwtService.issue(81L, false)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}/preview", media.getId())
                        .header("Authorization", "Bearer " + jwtService.issue(82L, false)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/{id}/preview", media.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void upload_whenOverHourlyLimit_returnsTooFast() throws Exception {
        // given: người dùng 90 đã tải đủ số ảnh cho phép trong giờ qua
        int limit = 40;
        for (int i = 0; i < limit; i++) {
            MediaFile media = new MediaFile("limit-" + i + ".jpg", "image/jpeg", 4);
            ReflectionTestUtils.setField(media, "createdBy", 90L);
            repository.save(media);
        }
        repository.flush();
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};

        // when, then
        mvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "x.jpg", "image/jpeg", jpg))
                        .header("Authorization", "Bearer " + jwtService.issue(90L, false)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_TOO_FAST"))
                .andExpect(jsonPath("$.message").value("Bạn tải ảnh quá nhanh. Vui lòng thử lại sau ít phút."));
    }

    @Test
    void cleanupOrphanedUploads_deletesOldUnattachedFilesOnlyAndKeepsAttachedOnes() throws Exception {
        // given: một ảnh chưa gắn quá 24 giờ, một ảnh chưa gắn còn mới, một ảnh đã gắn cũng quá 24 giờ
        MediaFile oldOrphan = repository.saveAndFlush(new MediaFile("orphan-old.jpg", "image/jpeg", 4));
        MediaFile freshOrphan = repository.saveAndFlush(new MediaFile("orphan-fresh.jpg", "image/jpeg", 4));
        MediaFile attached = new MediaFile("attached-old.jpg", "image/jpeg", 4);
        attached.attach();
        attached = repository.saveAndFlush(attached);
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE media_files SET created_at = ? WHERE id IN (?, ?)",
                Timestamp.from(now.minusSeconds(30 * 3600)),
                oldOrphan.getId(),
                attached.getId());
        jdbc.update(
                "UPDATE media_files SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(3600)),
                freshOrphan.getId());

        // when
        int cleaned = service.cleanupOrphanedUploads(now);

        // then: chỉ ảnh chưa gắn và quá hạn bị xoá, đối tượng trên kho cũng bị xoá
        assertThat(cleaned).isEqualTo(1);
        repository.flush();
        verify(storage).delete("orphan-old.jpg");
        verify(storage, never()).delete("orphan-fresh.jpg");
        verify(storage, never()).delete("attached-old.jpg");
        assertThat(jdbc.queryForObject(
                        "SELECT is_deleted FROM media_files WHERE id = ?", Boolean.class, oldOrphan.getId()))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT is_deleted FROM media_files WHERE id = ?", Boolean.class, attached.getId()))
                .isFalse();
    }
}
