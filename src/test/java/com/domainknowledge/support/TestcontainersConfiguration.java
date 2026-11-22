package com.domainknowledge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers dos testes de integração, com as mesmas imagens do docker compose.
 *
 * <p>Os containers são beans: o Spring sobe e derruba junto com o contexto, e o
 * cache de contexto do Spring Test reaproveita o mesmo contexto (e os mesmos
 * containers) entre as classes de teste que usam {@link IntegrationTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // A imagem do pgvector é um Postgres oficial com a extensão a mais.
        return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.6-pg18")
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.2"));
    }

    @Bean
    RustFsContainer storageContainer() {
        return new RustFsContainer();
    }

    /**
     * O Spring Boot não tem conexão automática para storage S3, então as
     * propriedades são registradas à mão a partir do container.
     */
    @Bean
    DynamicPropertyRegistrar storageProperties(RustFsContainer storage) {
        return registry -> {
            registry.add("storage.endpoint", storage::s3Endpoint);
            registry.add("storage.access-key", () -> RustFsContainer.ACCESS_KEY);
            registry.add("storage.secret-key", () -> RustFsContainer.SECRET_KEY);
        };
    }
}
