package com.domainknowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.service.DocumentUploadService;
import com.domainknowledge.support.IntegrationTest;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Logs com o documentId (RNF3) e health com cada componente (RNF4). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    DocumentUploadService uploadService;

    @Autowired
    DocumentRepository repository;

    @BeforeEach
    void cleanTable() {
        repository.deleteAllInBatch();
    }

    /** RNF3: as linhas do fluxo saem com [documentId], do upload ao consumidor. */
    @Test
    void everyLineOfTheFlowCarriesTheDocumentId(CapturedOutput output) throws IOException {
        ClassPathResource pdf = new ClassPathResource("fixtures/sample.pdf");
        UUID id = uploadService.upload(UploadRequest.builder()
                .originalFilename("regras.pdf").sizeBytes(pdf.contentLength()).content(pdf)
                .build()).document().getId();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(DocumentStatus.INDEXED));

        String tag = "[" + id + "]";
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut().lines()
                .filter(line -> line.contains(tag)))
                .anyMatch(line -> line.contains("Documento registrado"))      // upload (HTTP)
                .anyMatch(line -> line.contains("publicado na partição"))     // producer
                .anyMatch(line -> line.contains("recebido"))                  // consumidor
                .anyMatch(line -> line.contains("SHA-256 bate"))              // indexador
                .anyMatch(line -> line.contains("indexado")));                // fim
    }

    /** RNF4: health com banco, Kafka e storage, sem expor os detalhes de cada um. */
    @Test
    void healthShowsEachComponentWithoutDetails() {
        String body = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health").retrieve().body(String.class);

        JsonNode health = JsonMapper.builder().build().readTree(body);
        assertThat(health.get("status").asString()).isEqualTo("UP");
        for (String component : new String[] {"db", "kafka", "storage"}) {
            JsonNode node = health.get("components").get(component);
            assertThat(node.get("status").asString()).as(component).isEqualTo("UP");
            assertThat(node.has("details")).as(component + " sem detalhes").isFalse();
        }
    }
}
