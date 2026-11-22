package com.domainknowledge.ingestion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.infrastructure.storage.exception.StorageException;
import com.domainknowledge.ingestion.exception.EmptyFileException;
import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.exception.UnsupportedDocumentTypeException;
import com.domainknowledge.ingestion.model.UploadResult;
import com.domainknowledge.ingestion.service.DocumentUploadService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Camada web do upload: status HTTP e Problem Details de cada caso (A4, RF8). */
@WebMvcTest(DocumentUploadController.class)
class DocumentUploadControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DocumentUploadService uploadService;

    @Test
    void newDocumentAnswers202WithLocation() {
        Document document = document(DocumentStatus.QUEUED);
        when(uploadService.upload(any())).thenReturn(new UploadResult.Accepted(document));

        MvcTestResult result = upload(pdf());

        assertThat(result).hasStatus(HttpStatus.ACCEPTED)
                .hasHeader("Location", "http://localhost/v1/documents/" + document.getId());
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(document.getId().toString());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("QUEUED");
    }

    @Test
    void duplicateAnswers200() {
        Document document = document(DocumentStatus.INDEXED);
        when(uploadService.upload(any())).thenReturn(new UploadResult.AlreadyExists(document));

        assertThat(upload(pdf())).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("INDEXED");
    }

    @Test
    void emptyFileAnswers400() {
        when(uploadService.upload(any())).thenThrow(new EmptyFileException("vazio.pdf"));

        assertProblem(upload(pdf()), HttpStatus.BAD_REQUEST, "Arquivo vazio");
    }

    @Test
    void unsupportedTypeAnswers415() {
        when(uploadService.upload(any())).thenThrow(new UnsupportedDocumentTypeException("fake.pdf"));

        assertProblem(upload(pdf()), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de arquivo não aceito");
    }

    @Test
    void publishFailureAnswers503WithDocumentId() {
        UUID documentId = UUID.randomUUID();
        when(uploadService.upload(any())).thenThrow(new EventPublishException(documentId, new RuntimeException()));

        MvcTestResult result = upload(pdf());

        assertProblem(result, HttpStatus.SERVICE_UNAVAILABLE, "Fila indisponível");
        assertThat(result).bodyJson().extractingPath("$.documentId").isEqualTo(documentId.toString());
    }

    @Test
    void storageFailureAnswers503() {
        when(uploadService.upload(any())).thenThrow(new StorageException("fora do ar", new RuntimeException()));

        assertProblem(upload(pdf()), HttpStatus.SERVICE_UNAVAILABLE, "Storage indisponível");
    }

    @Test
    void missingFilePartAnswers400() {
        MvcTestResult result = mvc.post().uri("/v1/documents").multipart().param("title", "sem arquivo").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void titleLongerThan255Answers400() {
        MvcTestResult result = mvc.post().uri("/v1/documents").multipart()
                .file(pdf()).param("title", "x".repeat(256)).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult upload(MockMultipartFile file) {
        return mvc.post().uri("/v1/documents").multipart().file(file).exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String title) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(title);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/v1/documents");
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "regras.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    private static Document document(DocumentStatus status) {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getStatus()).thenReturn(status);
        when(document.getTitle()).thenReturn("regras.pdf");
        when(document.getCreatedAt()).thenReturn(Instant.parse("2026-11-22T14:03:00Z"));
        return document;
    }
}
