package com.domainknowledge.ingestion.model;

import com.domainknowledge.document.model.Document;

/**
 * Resultado do upload. {@code sealed}: só existem estes dois casos, e um
 * {@code switch} sobre ele que esqueça um caso não compila. O controller usa
 * isso pra escolher entre {@code 202} e {@code 200} (plan §4).
 */
public sealed interface UploadResult {

    Document document();

    /** Documento novo, ou duplicado em {@code RECEIVED}/{@code FAILED} que voltou pra fila. */
    record Accepted(Document document) implements UploadResult {
    }

    /** Duplicado que já está na fila, processando ou indexado; nada foi publicado. */
    record AlreadyExists(Document document) implements UploadResult {
    }
}
