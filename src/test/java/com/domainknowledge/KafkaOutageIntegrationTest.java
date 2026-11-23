package com.domainknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A8: o Kafka cai de verdade (o container é pausado) no meio do upload. A API
 * responde 503 sem travar, o documento fica {@code RECEIVED}; com o Kafka de
 * volta, reenviar o mesmo arquivo leva o documento até {@code INDEXED}.
 */
@IntegrationTest
class KafkaOutageIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    KafkaContainer kafka;

    @Autowired
    DocumentRepository repository;

    private RestClient http;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void uploadDuringKafkaOutageIsRecoveredByResending() {
        ResponseEntity<String> duringOutage;
        long start = System.nanoTime();
        pauseKafka();
        try {
            duringOutage = upload();
        } finally {
            unpauseKafka();
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(duringOutage.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
        JsonNode problem = JSON.readTree(duringOutage.getBody());
        UUID id = UUID.fromString(problem.get("documentId").asString());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.RECEIVED);

        // Kafka de volta: o cliente pode levar alguns segundos pra reconectar, e
        // um reenvio ainda pode dar 503. Timeout no producer não quer dizer que
        // a mensagem não chegou: se chegou, o consumidor assume o documento
        // direto de RECEIVED, ele vira INDEXED e o reenvio seguinte responde
        // 200 (já existe). Por isso 202 e 200 valem as duas.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1))
                .until(() -> upload().getStatusCode().is2xxSuccessful());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.INDEXED));
        assertThat(repository.count()).isEqualTo(1);
    }

    private ResponseEntity<String> upload() {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ClassPathResource("fixtures/sample.pdf")).filename("regras.pdf");
        return http.post().uri("/v1/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().toEntity(String.class);
    }

    private void pauseKafka() {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    private void unpauseKafka() {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }
}
