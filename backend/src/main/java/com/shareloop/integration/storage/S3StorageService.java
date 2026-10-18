package com.shareloop.integration.storage;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Lưu ảnh lên storage tương thích S3 (Cloudflare R2, MinIO, Backblaze B2...).
 * Dùng khi app.storage.type = s3. Khoá ảnh có cùng dạng với bản Local ({uuid}.{đuôi}),
 * nên chuyển ảnh cũ chỉ cần chép nguyên tên tệp lên bucket.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {
    private final S3Client client;
    private final String bucket;

    @Autowired
    public S3StorageService(
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.region:auto}") String region,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey) {
        this(buildClient(endpoint, region, accessKey, secretKey), bucket);
    }

    /** Dùng trong test: truyền S3Client giả vào. */
    S3StorageService(S3Client client, String bucket) {
        this.client = client;
        this.bucket = requireText(bucket, "app.storage.s3.bucket");
    }

    @Override
    public String save(byte[] content, String extension) throws IOException {
        String key = UUID.randomUUID() + "." + extension;
        PutObjectRequest request =
                PutObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException exception) {
            throw new IOException("Cannot upload " + key + " to bucket " + bucket, exception);
        }
        return key;
    }

    @Override
    public byte[] read(String key) throws IOException {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            return client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new NoSuchFileException(key);
        } catch (SdkException exception) {
            throw new IOException("Cannot read " + key + " from bucket " + bucket, exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            client.deleteObject(request);
        } catch (SdkException exception) {
            throw new IOException("Cannot delete " + key + " from bucket " + bucket, exception);
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }

    private static S3Client buildClient(String endpoint, String region, String accessKey, String secretKey) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                requireText(accessKey, "app.storage.s3.access-key"),
                requireText(secretKey, "app.storage.s3.secret-key"));
        return S3Client.builder()
                .endpointOverride(URI.create(requireText(endpoint, "app.storage.s3.endpoint")))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                // R2, MinIO, B2 đều nhận đường dẫn dạng {endpoint}/{bucket}/{key}
                .forcePathStyle(true)
                // Chỉ gửi checksum khi API bắt buộc: tránh lỗi ở storage không phải AWS
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when app.storage.type = s3");
        }
        return value;
    }
}
