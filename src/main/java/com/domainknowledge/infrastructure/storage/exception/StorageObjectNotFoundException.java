package com.domainknowledge.infrastructure.storage.exception;

import lombok.Getter;

/** O objeto não existe no bucket. Não adianta tentar de novo (RF7). */
@Getter
public class StorageObjectNotFoundException extends StorageException {

    private final String key;

    public StorageObjectNotFoundException(String key, Throwable cause) {
        super("Objeto não encontrado no storage: " + key, cause);
        this.key = key;
    }
}
