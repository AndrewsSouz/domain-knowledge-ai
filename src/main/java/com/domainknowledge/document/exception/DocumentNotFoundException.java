package com.domainknowledge.document.exception;

import java.util.UUID;

/** Id que não existe ({@code 404}, RF3). */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(UUID id) {
        super("Documento não encontrado: " + id);
    }
}
