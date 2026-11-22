package com.domainknowledge.document.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domainknowledge.document.exception.DocumentNotFoundException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.service.DocumentQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Camada web da leitura: documento, 404 em Problem Details (A7) e paginação. */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DocumentQueryService documents;

    @Test
    void returnsTheDocument() {
        Document document = document(DocumentStatus.FAILED);
        when(documents.getById(document.getId())).thenReturn(document);

        MvcTestResult result = mvc.get().uri("/v1/documents/{id}", document.getId()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("FAILED");
        assertThat(result).bodyJson().extractingPath("$.errorMessage").isEqualTo("hash diferente");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-11-22T14:03:00Z");
    }

    @Test
    void unknownIdAnswers404AsProblemDetails() {
        UUID id = UUID.randomUUID();
        when(documents.getById(id)).thenThrow(new DocumentNotFoundException(id));

        MvcTestResult result = mvc.get().uri("/v1/documents/{id}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Documento não encontrado");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/v1/documents/" + id);
    }

    @Test
    void invalidIdAnswers400() {
        assertThat(mvc.get().uri("/v1/documents/nao-e-uuid").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listsAPageFilteredByStatus() {
        Document document = document(DocumentStatus.QUEUED);
        when(documents.list(DocumentStatus.QUEUED, 0, 20))
                .thenReturn(new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1));

        MvcTestResult result = mvc.get().uri("/v1/documents?status=QUEUED").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(document.getId().toString());
        assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.page.size").isEqualTo(20);
    }

    @Test
    void listWithoutFilterAsksForAllStatuses() {
        when(documents.list(eq(null), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        assertThat(mvc.get().uri("/v1/documents?page=2&size=5").exchange()).hasStatusOk();
        verify(documents).list(null, 2, 5);
    }

    @Test
    void invalidStatusOrPageSizeAnswers400() {
        assertThat(mvc.get().uri("/v1/documents?status=DELETED").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/v1/documents?size=101").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/v1/documents?page=-1").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static Document document(DocumentStatus status) {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getStatus()).thenReturn(status);
        when(document.getErrorMessage()).thenReturn(status == DocumentStatus.FAILED ? "hash diferente" : null);
        when(document.getCreatedAt()).thenReturn(Instant.parse("2026-11-22T14:03:00Z"));
        return document;
    }
}
