package com.domainknowledge.document.model;

import java.util.UUID;
import lombok.Builder;

/**
 * Dados de um documento que acabou de chegar, antes de existir no banco.
 *
 * @param id               gerado pela aplicação, porque a chave no storage já
 *                         usa o id antes do insert
 * @param sha256           hash do conteúdo em hexadecimal (64 caracteres)
 * @param storageKey       onde o original foi gravado no storage
 * @param uploadedBy       quem enviou; fica vazio até a autenticação (fatia 4)
 */
@Builder
public record NewDocument(
        UUID id,
        String title,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageKey,
        String uploadedBy) {
}
