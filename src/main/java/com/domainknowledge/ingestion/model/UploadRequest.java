package com.domainknowledge.ingestion.model;

import lombok.Builder;
import org.springframework.core.io.InputStreamSource;

/**
 * Um arquivo recebido no upload, sem depender da camada web.
 *
 * @param title   opcional; sem título, vale o nome do arquivo
 * @param content abre um stream novo a cada leitura (o {@code MultipartFile}
 *                implementa {@link InputStreamSource}), porque o conteúdo é
 *                lido três vezes: detectar o tipo, calcular o hash e gravar
 */
@Builder
public record UploadRequest(String originalFilename, String title, long sizeBytes, InputStreamSource content) {
}
