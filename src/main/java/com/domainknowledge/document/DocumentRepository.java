package com.domainknowledge.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso à tabela {@code document}. Só o pacote {@code document} usa (ADR
 * 0002); os outros pacotes passam pelo {@code DocumentLifecycle}. Os
 * {@code UPDATE} condicionais de status entram na T102.
 */
interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findBySha256(String sha256);
}
