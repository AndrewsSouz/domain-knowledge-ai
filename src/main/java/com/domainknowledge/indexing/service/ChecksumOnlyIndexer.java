package com.domainknowledge.indexing.service;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.indexing.exception.ChecksumMismatchException;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import com.domainknowledge.shared.util.ContentHasher;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Indexador da fatia 1 (o "walking skeleton"): baixa o original do storage e
 * confere se o SHA-256 bate com o do upload. Prova o caminho inteiro (Kafka →
 * storage → banco) sem ainda extrair texto nem gerar embedding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChecksumOnlyIndexer implements DocumentIndexer {

    private final ObjectStorage storage;

    @Override
    public void index(Document document) {
        String actual;
        try (InputStream content = storage.get(document.getStorageKey())) {
            actual = ContentHasher.sha256(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!actual.equals(document.getSha256())) {
            throw new ChecksumMismatchException(document.getId(), document.getSha256(), actual);
        }
        log.info("SHA-256 bate com o do upload");
    }
}
