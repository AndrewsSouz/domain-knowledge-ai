package com.domainknowledge.infrastructure.storage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domainknowledge.infrastructure.storage.config.StorageProperties;
import com.domainknowledge.infrastructure.storage.exception.StorageObjectNotFoundException;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import com.domainknowledge.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;

/** {@link S3Integration} contra o RustFS do Testcontainers. */
@IntegrationTest
class S3IntegrationTest {

    private static final String SHA256 = "a".repeat(64);

    @Autowired
    ObjectStorage storage;

    @Autowired
    S3Client s3Client;

    @Autowired
    StorageProperties properties;

    @Test
    void bucketIsCreatedOnStartup() {
        assertThat(s3Client.listBuckets().buckets())
                .anySatisfy(bucket -> assertThat(bucket.name()).isEqualTo(properties.bucket()));
    }

    @Test
    void putsAndReadsBackTheSameContent() throws Exception {
        String key = randomKey();
        byte[] content = "Política de férias da Acme: 30 dias por ano.".getBytes(StandardCharsets.UTF_8);

        storage.put(upload(key, content.length), new ByteArrayInputStream(content));

        try (InputStream stored = storage.get(key)) {
            assertThat(stored.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void storesContentTypeAndHashAsObjectMetadata() {
        String key = randomKey();
        byte[] content = {1, 2, 3};

        storage.put(upload(key, content.length), new ByteArrayInputStream(content));

        var head = s3Client.headObject(request -> request.bucket(properties.bucket()).key(key));
        assertThat(head.contentType()).isEqualTo("application/pdf");
        assertThat(head.metadata()).containsEntry(S3Integration.SHA256_METADATA, SHA256);
    }

    @Test
    void getOfMissingObjectFailsWithNotFound() {
        String key = randomKey();

        assertThatThrownBy(() -> storage.get(key))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .extracting("key").isEqualTo(key);
    }

    @Test
    void existsTellsWhetherTheObjectIsThere() {
        String key = randomKey();
        assertThat(storage.exists(key)).isFalse();

        storage.put(upload(key, 1), new ByteArrayInputStream(new byte[] {42}));

        assertThat(storage.exists(key)).isTrue();
    }

    private static ObjectUpload upload(String key, long size) {
        return ObjectUpload.builder()
                .key(key)
                .contentType("application/pdf")
                .sizeBytes(size)
                .sha256(SHA256)
                .build();
    }

    private static String randomKey() {
        return "documents/" + UUID.randomUUID() + "/v1/original.pdf";
    }
}
