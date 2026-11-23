package com.domainknowledge.document.controller;

import com.domainknowledge.document.model.DocumentResponse;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.service.DocumentQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura de documentos (RF3, RF4). O {@code POST} do mesmo caminho fica no
 * {@code DocumentUploadController}, do domínio de ingestão.
 */
@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
class DocumentController {

    private final DocumentQueryService documents;

    @GetMapping("/{id}")
    DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documents.getById(id));
    }

    /**
     * Página de documentos, mais recentes primeiro. O {@link PagedModel} é o
     * formato estável de página do Spring Data: {@code content} + {@code page}.
     */
    @GetMapping
    PagedModel<DocumentResponse> list(
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return new PagedModel<>(documents.list(status, page, size).map(DocumentResponse::from));
    }
}
