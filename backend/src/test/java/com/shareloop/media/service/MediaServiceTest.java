package com.shareloop.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.storage.StorageService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.entity.MediaFile;
import com.shareloop.media.entity.MediaStatus;
import com.shareloop.media.repository.MediaFileRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    private static final int ONE_MB = 1024 * 1024;

    @Mock
    MediaFileRepository repository;

    @Mock
    StorageService storage;

    @Mock
    ConfigService config;

    @InjectMocks
    MediaService service;

    @Test
    void uploadUsesFileSignatureAndReturnsId() throws IOException {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        when(config.getInt(ConfigKey.IMAGE_MAX_MB)).thenReturn(5);
        when(storage.save(png, "png")).thenReturn("image.png");
        when(repository.saveAndFlush(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 7L);
            ReflectionTestUtils.setField(media, "createdBy", 4L);
            return media;
        });

        var response = service.upload(new MockMultipartFile("file", "fake.jpg", "image/jpeg", png), 4L);
        assertThat(response.mediaId()).isEqualTo(7L);
        assertThat(response.url()).isEqualTo("/api/v1/media/7");
        verify(repository).saveAndFlush(any(MediaFile.class));
    }

    @Test
    void attachedEvidenceBecomesPrivateAndCannotBeReadPublicly() {
        MediaFile owned = media(1L, 4L);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(owned));
        when(repository.findById(1L)).thenReturn(Optional.of(owned));
        service.attachPrivateEvidence(List.of(1L), 4L);
        assertThat(owned.getVisibility()).isEqualTo("PRIVATE");
        assertThat(owned.getStatus()).isEqualTo(MediaStatus.ATTACHED);
        assertThatThrownBy(() -> service.readPublic(1L))
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    void invalidImageNeverReachesStorage() throws IOException {
        when(config.getInt(ConfigKey.IMAGE_MAX_MB)).thenReturn(5);
        var file = new MockMultipartFile("file", "x.png", "image/png", new byte[] {1, 2, 3});
        assertThatThrownBy(() -> service.upload(file, 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_INVALID_FILE);
        verify(storage, never()).save(any(), any());
    }

    @Test
    void upload_whenImageLargerThanImageMaxMb_throwsFileTooLarge() throws IOException {
        // given: giới hạn 1 MB, ảnh lớn hơn đúng 1 byte
        when(config.getInt(ConfigKey.IMAGE_MAX_MB)).thenReturn(1);
        var file = new MockMultipartFile("file", "big.png", "image/png", pngWithSize(ONE_MB + 1));

        // when
        assertThatThrownBy(() -> service.upload(file, 4L))
                // then
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_FILE_TOO_LARGE);
        verify(storage, never()).save(any(), any());
        verify(repository, never()).saveAndFlush(any(MediaFile.class));
    }

    @Test
    void upload_whenImageExactlyImageMaxMb_isAccepted() throws IOException {
        // given: ảnh đúng bằng giới hạn 1 MB vẫn hợp lệ
        byte[] png = pngWithSize(ONE_MB);
        when(config.getInt(ConfigKey.IMAGE_MAX_MB)).thenReturn(1);
        when(storage.save(png, "png")).thenReturn("image.png");
        when(repository.saveAndFlush(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile media = invocation.getArgument(0);
            ReflectionTestUtils.setField(media, "id", 8L);
            ReflectionTestUtils.setField(media, "createdBy", 4L);
            return media;
        });

        // when
        var response = service.upload(new MockMultipartFile("file", "edge.png", "image/png", png), 4L);

        // then
        assertThat(response.mediaId()).isEqualTo(8L);
        verify(storage).save(png, "png");
    }

    @Test
    void attachChecksAllOwnersBeforeChangingStatus() {
        MediaFile owned = media(1L, 4L);
        MediaFile foreign = media(2L, 5L);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(owned));
        when(repository.findForUpdate(2L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.attach(List.of(1L, 2L), 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_NOT_OWNER);
        assertThat(owned.getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    @Test
    void attachRejectsReuse() {
        MediaFile owned = media(1L, 4L);
        when(repository.findForUpdate(1L)).thenReturn(Optional.of(owned));
        service.attach(List.of(1L), 4L);
        assertThat(owned.getStatus()).isEqualTo(MediaStatus.ATTACHED);
        assertThatThrownBy(() -> service.attach(List.of(1L), 4L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MediaErrorCode.MEDIA_NOT_UPLOADED);
    }

    @Test
    void temporaryGatewaysFailBeforeChangingMedia() {
        assertThatThrownBy(() -> service.deactivate(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deactivate");
        assertThatThrownBy(() -> service.remove(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("remove");
        verifyNoInteractions(repository, storage);
    }

    /** Mảng byte có chữ ký PNG ở đầu để qua bước nhận dạng định dạng, phần còn lại là số 0. */
    private static byte[] pngWithSize(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private MediaFile media(long id, long owner) {
        MediaFile media = new MediaFile("key" + id, "image/png", 10);
        ReflectionTestUtils.setField(media, "id", id);
        ReflectionTestUtils.setField(media, "createdBy", owner);
        return media;
    }
}
