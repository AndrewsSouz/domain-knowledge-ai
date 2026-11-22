package com.domainknowledge.infrastructure.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                // Path-style: o bucket vai no caminho (host/bucket/chave), não no
                // nome do host (bucket.host/chave). Storage local não tem DNS por bucket.
                .forcePathStyle(true)
                // O SDK exige uma região; fora da AWS ela não importa.
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                // Checksum extra só quando a operação exige: o padrão do SDK manda
                // checksums em formato que nem todo storage compatível aceita.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }
}
