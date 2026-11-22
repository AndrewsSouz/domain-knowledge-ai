package com.domainknowledge.ingestion.controller;

import com.domainknowledge.document.controller.DocumentResponse;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.model.UploadResult;
import com.domainknowledge.ingestion.service.DocumentUploadService;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Upload de documento (RF1, RF2). Fino: monta o pedido, chama o serviço, escolhe o status HTTP. */
@RestController
@RequestMapping("/v1/documents")
@RequiredArgsConstructor
class DocumentUploadController {

    /** Tamanho da coluna {@code original_filename}. */
    private static final int MAX_FILENAME = 255;

    private final DocumentUploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 255) String title) {
        UploadResult result = uploadService.upload(UploadRequest.builder()
                .originalFilename(filenameOf(file))
                .title(title)
                .sizeBytes(file.getSize())
                .content(file)
                .build());

        DocumentResponse body = DocumentResponse.from(result.document());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(body.id())
                .toUri();
        // UploadResult é sealed: se aparecer um terceiro caso, isto não compila.
        return switch (result) {
            case UploadResult.Accepted accepted -> ResponseEntity.accepted().location(location).body(body);
            case UploadResult.AlreadyExists existing -> ResponseEntity.ok().location(location).body(body);
        };
    }

    /** Só o nome, sem caminho (alguns navegadores mandam o caminho inteiro), e no tamanho da coluna. */
    private static String filenameOf(MultipartFile file) {
        String name = StringUtils.getFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(name)) {
            return "documento";
        }
        return name.length() > MAX_FILENAME ? name.substring(name.length() - MAX_FILENAME) : name;
    }
}
