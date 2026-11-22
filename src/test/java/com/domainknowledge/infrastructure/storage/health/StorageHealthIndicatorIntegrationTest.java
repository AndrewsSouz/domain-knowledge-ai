package com.domainknowledge.infrastructure.storage.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.infrastructure.storage.config.StorageProperties;
import com.domainknowledge.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.services.s3.S3Client;

/** O storage aparece no health e fica {@code DOWN} quando o bucket não responde. */
@IntegrationTest
class StorageHealthIndicatorIntegrationTest {

    @Autowired
    HealthEndpoint healthEndpoint;

    @Autowired
    S3Client s3Client;

    @Autowired
    StorageProperties properties;

    @Test
    void storageIsUpInTheHealthEndpoint() {
        assertThat(healthEndpoint.healthForPath("storage").getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void storageIsDownWhenTheBucketDoesNotExist() {
        StorageProperties missingBucket = StorageProperties.builder()
                .endpoint(properties.endpoint())
                .bucket("bucket-que-nao-existe")
                .accessKey(properties.accessKey())
                .secretKey(properties.secretKey())
                .build();

        Health health = new StorageHealthIndicator(s3Client, missingBucket).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("bucket", "bucket-que-nao-existe");
    }
}
