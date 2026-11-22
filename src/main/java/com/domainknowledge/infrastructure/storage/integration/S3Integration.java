package com.domainknowledge.infrastructure.storage.integration;

import com.domainknowledge.infrastructure.storage.config.StorageProperties;
import com.domainknowledge.infrastructure.storage.exception.StorageException;
import com.domainknowledge.infrastructure.storage.exception.StorageObjectNotFoundException;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import java.io.InputStream;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** {@link ObjectStorage} sobre a API S3 (AWS SDK v2). */
@Component
@RequiredArgsConstructor
class S3Integration implements ObjectStorage {

    /** Nome do metadado com o SHA-256 do conteúdo ({@code x-amz-meta-sha256}). */
    static final String SHA256_METADATA = "sha256";

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void put(ObjectUpload upload, InputStream content) {
        try {
            s3Client.putObject(request -> request
                            .bucket(properties.bucket())
                            .key(upload.key())
                            .contentType(upload.contentType())
                            .contentLength(upload.sizeBytes())
                            .metadata(Map.of(SHA256_METADATA, upload.sha256())),
                    RequestBody.fromInputStream(content, upload.sizeBytes()));
        } catch (SdkException e) {
            throw new StorageException("Falha ao gravar no storage: " + upload.key(), e);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3Client.getObject(request -> request.bucket(properties.bucket()).key(key));
        } catch (NoSuchKeyException e) {
            throw new StorageObjectNotFoundException(key, e);
        } catch (SdkException e) {
            throw new StorageException("Falha ao ler do storage: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(request -> request.bucket(properties.bucket()).key(key));
            return true;
        } catch (S3Exception e) {
            // HEAD não tem corpo, então o "não existe" chega só como 404.
            if (e.statusCode() == 404) {
                return false;
            }
            throw new StorageException("Falha ao consultar o storage: " + key, e);
        } catch (SdkException e) {
            throw new StorageException("Falha ao consultar o storage: " + key, e);
        }
    }
}
