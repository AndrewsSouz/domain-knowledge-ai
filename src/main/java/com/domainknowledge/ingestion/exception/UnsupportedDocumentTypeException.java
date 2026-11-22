package com.domainknowledge.ingestion.exception;

/** Arquivo que não é PDF nem DOCX pelo conteúdo (RF1, {@code 415}). */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String filename) {
        super("Tipo de arquivo não aceito (só PDF e DOCX): " + filename);
    }
}
