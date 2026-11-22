package com.domainknowledge.infrastructure.storage.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cria o bucket na subida, se ele não existir e {@code storage.create-bucket}
 * estiver ligado. Roda durante a criação dos beans, antes de o servidor web
 * aceitar requisições; storage fora do ar derruba a subida (falha rápido).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BucketInitializer implements InitializingBean {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void afterPropertiesSet() {
        String bucket = properties.bucket();
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3Client.headBucket(request -> request.bucket(bucket));
            log.info("Bucket {} encontrado", bucket);
        } catch (S3Exception e) {
            // HEAD não tem corpo, então o "não existe" chega só como 404.
            if (e.statusCode() != 404) {
                throw e;
            }
            s3Client.createBucket(request -> request.bucket(bucket));
            log.info("Bucket {} criado", bucket);
        }
    }
}
