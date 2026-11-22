package com.domainknowledge.infrastructure.storage.model;

import lombok.Builder;

/**
 * Dados de um objeto a gravar no storage; o conteúdo vai à parte, em stream.
 *
 * @param key       caminho do objeto no bucket, ex.: {@code documents/{id}/v1/original.pdf}
 * @param sizeBytes tamanho exato do conteúdo (o S3 exige saber antes de enviar)
 * @param sha256    hash do conteúdo, gravado nos metadados do objeto
 */
@Builder
public record ObjectUpload(String key, String contentType, long sizeBytes, String sha256) {
}
