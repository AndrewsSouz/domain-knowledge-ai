package com.domainknowledge.document.service;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.repository.DocumentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Leituras de documento para os outros pacotes; mudanças passam pelo {@link DocumentLifecycle}. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentQueryService {

    private final DocumentRepository repository;

    public Optional<Document> findById(UUID id) {
        return repository.findById(id);
    }

    public Optional<Document> findBySha256(String sha256) {
        return repository.findBySha256(sha256);
    }
}
