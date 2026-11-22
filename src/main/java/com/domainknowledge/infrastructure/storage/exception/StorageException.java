package com.domainknowledge.infrastructure.storage.exception;

/** Falha ao falar com o storage. Esconde as exceções do AWS SDK do resto da aplicação. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
