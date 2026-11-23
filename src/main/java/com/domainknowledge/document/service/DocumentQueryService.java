package com.domainknowledge.document.service;

import com.domainknowledge.document.exception.DocumentNotFoundException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.repository.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leituras de documento para os outros pacotes; mudanças passam pelo {@link DocumentLifecycle}. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentQueryService {

    private final DocumentRepository repository;

    /** Mais recentes primeiro; o id desempata, pra paginação não repetir nem pular linha. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    /** @throws DocumentNotFoundException se o id não existe */
    public Document getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** @param status {@code null} lista todos */
    public Page<Document> list(DocumentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    }

    public Optional<Document> findBySha256(String sha256) {
        return repository.findBySha256(sha256);
    }
}
