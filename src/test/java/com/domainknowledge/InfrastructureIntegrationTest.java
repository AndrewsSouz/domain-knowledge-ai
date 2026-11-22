package com.domainknowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.support.IntegrationTest;
import com.domainknowledge.support.RustFsContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Prova que o contexto sobe com a infraestrutura de teste e que cada peça
 * responde de verdade.
 */
@IntegrationTest
class InfrastructureIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    RustFsContainer storage;

    @Test
    void postgresHasPgvectorAvailable() {
        String version = jdbcTemplate.queryForObject(
                "select default_version from pg_available_extensions where name = 'vector'", String.class);

        assertThat(version).isNotBlank();
    }

    @Test
    void kafkaClusterIsReachable() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var nodes = admin.describeCluster().nodes().get(10, TimeUnit.SECONDS);

            assertThat(nodes).hasSize(1);
        }
    }

    @Test
    void storageIsHealthy() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(storage.s3Endpoint() + "/health")).build(),
                    HttpResponse.BodyHandlers.discarding());

            assertThat(response.statusCode()).isEqualTo(200);
        }
    }
}
