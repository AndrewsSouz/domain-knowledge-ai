package com.domainknowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.document.repository.DocumentRepository;
import com.domainknowledge.support.IntegrationTest;
import java.net.URI;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A API pelo HTTP de verdade (Tomcat, multipart, Problem Details). */
@IntegrationTest
class DocumentApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    DocumentRepository repository;

    private RestClient http;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                // O teste confere o status; não lançar exceção em 4xx/5xx.
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @Test
    void uploadThenReadTheDocument() {
        ResponseEntity<String> created = upload(new ClassPathResource("fixtures/sample.pdf"), "regras.pdf");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        URI location = created.getHeaders().getLocation();
        assertThat(location).isNotNull();

        ResponseEntity<String> read = http.get().uri(location).retrieve().toEntity(String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode document = JSON.readTree(read.getBody());
        assertThat(document.get("status").asString()).isEqualTo("QUEUED");
        assertThat(document.get("title").asString()).isEqualTo("regras.pdf");

        JsonNode page = JSON.readTree(http.get().uri("/v1/documents?status=QUEUED").retrieve().body(String.class));
        assertThat(page.get("content").get(0).get("id").asString()).isEqualTo(document.get("id").asString());
    }

    /** A4: acima de 20 MB, 413 em Problem Details, sem conexão cortada. */
    @Test
    void fileAboveTheLimitAnswers413() {
        byte[] content = new byte[20 * 1024 * 1024 + 1024];
        byte[] header = "%PDF-1.4\n".getBytes();
        System.arraycopy(header, 0, content, 0, header.length);
        Arrays.fill(content, header.length, content.length, (byte) ' ');

        ResponseEntity<String> response = upload(new ByteArrayResource(content), "grande.pdf");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(JSON.readTree(response.getBody()).get("title").asString()).isEqualTo("Arquivo grande demais");
        assertThat(repository.count()).isZero();
    }

    /** A4: arquivo vazio e tipo errado, pelo HTTP de verdade. */
    @Test
    void emptyAndUnsupportedFilesAreRejected() {
        assertThat(upload(new ClassPathResource("fixtures/empty.pdf"), "empty.pdf").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(new ClassPathResource("fixtures/fake.pdf"), "fake.pdf").getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(repository.count()).isZero();
    }

    /** A7: id inexistente, 404 em Problem Details. */
    @Test
    void unknownIdAnswers404() {
        ResponseEntity<String> response = http.get()
                .uri("/v1/documents/{id}", "00000000-0000-0000-0000-000000000000")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private ResponseEntity<String> upload(Resource content, String filename) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", content).filename(filename);
        return http.post().uri("/v1/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().toEntity(String.class);
    }
}
