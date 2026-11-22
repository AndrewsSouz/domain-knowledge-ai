package com.domainknowledge.document.repository;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso à tabela {@code document}. Só o pacote {@code document} usa (ADR
 * 0002); os outros pacotes passam pelo {@code DocumentLifecycle}.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findBySha256(String sha256);

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);

    /**
     * Muda o status só se o documento estiver num dos estados de origem.
     * A checagem e a escrita são o mesmo comando no banco, então duas
     * transições concorrentes não passam as duas: a segunda espera o lock da
     * linha, reavalia o {@code WHERE} e não muda nada.
     *
     * <p>{@code errorMessage} é sempre sobrescrito: só existe em
     * {@code FAILED}, e toda saída de {@code FAILED} limpa o erro.
     *
     * @return linhas alteradas: 1 se a transição valeu, 0 se não
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Document d
               set d.status = :to, d.errorMessage = :errorMessage, d.updatedAt = :now
             where d.id = :id and d.status in :from
            """)
    int transition(UUID id, Collection<DocumentStatus> from, DocumentStatus to, String errorMessage, Instant now);
}
