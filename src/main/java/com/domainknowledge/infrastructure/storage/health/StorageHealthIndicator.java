package com.domainknowledge.infrastructure.storage.health;

import com.domainknowledge.infrastructure.storage.config.StorageProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Entra no {@code /actuator/health} como {@code storage} (o Boot tira o sufixo
 * {@code HealthIndicator} do nome do bean). Faz um {@code HEAD} no bucket:
 * prova que o storage responde, que as credenciais valem e que o bucket existe.
 * Qualquer exceção vira {@code DOWN}, tratada pelo {@link AbstractHealthIndicator}.
 */
@Component
@RequiredArgsConstructor
class StorageHealthIndicator extends AbstractHealthIndicator {

    /** Health não pode travar: storage lento conta como fora do ar. */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        builder.withDetail("bucket", properties.bucket());
        s3Client.headBucket(request -> request
                .bucket(properties.bucket())
                .overrideConfiguration(config -> config.apiCallTimeout(TIMEOUT)));
        builder.up();
    }
}
