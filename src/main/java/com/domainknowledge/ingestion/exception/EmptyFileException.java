package com.domainknowledge.ingestion.exception;

/** Arquivo sem conteúdo (RF1, {@code 400}). */
public class EmptyFileException extends RuntimeException {

    public EmptyFileException(String filename) {
        super("Arquivo vazio: " + filename);
    }
}
