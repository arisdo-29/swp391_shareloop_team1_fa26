package com.shareloop.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3StorageServiceTest {
    private S3Client client;
    private S3StorageService storage;

    @BeforeEach
    void setUp() {
        client = mock(S3Client.class);
        storage = new S3StorageService(client, "shareloop-media");
    }

    @Test
    void saveUploadsToBucketWithSameKeyFormatAsLocal() throws Exception {
        String key = storage.save(new byte[] {1, 2, 3}, "png");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("shareloop-media");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(key).matches("[0-9a-f-]{36}\\.png");
    }

    @Test
    void readReturnsObjectBytes() throws Exception {
        byte[] bytes = {4, 5, 6};
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes));

        assertThat(storage.read("a.png")).containsExactly(bytes);
    }

    @Test
    void readMissingObjectThrowsNoSuchFile() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());

        assertThatThrownBy(() -> storage.read("missing.png")).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void storageErrorsBecomeIoException() {
        SdkClientException failure = SdkClientException.create("connection refused");
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(failure);
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(failure);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> storage.save(new byte[] {1}, "jpg")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.read("a.jpg")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.delete("a.jpg")).isInstanceOf(IOException.class);
    }

    @Test
    void missingBucketFailsAtStartup() {
        assertThatThrownBy(() -> new S3StorageService(client, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.storage.s3.bucket");
    }
}
