package com.domainknowledge.indexing.exception;

import java.util.UUID;
import lombok.Getter;

/**
 * O arquivo baixado do storage não tem o SHA-256 registrado no upload: foi
 * trocado ou corrompido. Não adianta tentar de novo (RF7).
 */
@Getter
public class ChecksumMismatchException extends RuntimeException {

    private final UUID documentId;

    public ChecksumMismatchException(UUID documentId, String expected, String actual) {
        super("SHA-256 diferente do registrado no upload (esperado %s, encontrado %s)".formatted(expected, actual));
        this.documentId = documentId;
    }
}
