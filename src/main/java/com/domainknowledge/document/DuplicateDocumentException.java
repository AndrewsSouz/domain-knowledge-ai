package com.domainknowledge.document;

import lombok.Getter;

/**
 * Já existe um documento com o mesmo SHA-256. Acontece quando duas
 * requisições com o mesmo arquivo passam juntas pela checagem de duplicado e
 * o {@code unique} do banco barra a segunda.
 */
@Getter
public class DuplicateDocumentException extends RuntimeException {

    private final String sha256;

    public DuplicateDocumentException(String sha256, Throwable cause) {
        super("Já existe documento com o SHA-256 " + sha256, cause);
        this.sha256 = sha256;
    }
}
