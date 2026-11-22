package com.domainknowledge.infrastructure.storage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração do storage S3 ({@code storage.*} no {@code application.yml}).
 *
 * @param endpoint     endereço da API S3 (RustFS local, ou outro compatível)
 * @param bucket       bucket onde ficam os originais
 * @param createBucket cria o bucket na subida se ele não existir
 */
@Validated
@ConfigurationProperties("storage")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @DefaultValue("true") boolean createBucket) {
}
