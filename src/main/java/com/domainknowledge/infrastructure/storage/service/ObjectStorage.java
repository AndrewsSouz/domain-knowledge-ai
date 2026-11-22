package com.domainknowledge.infrastructure.storage.service;

import com.domainknowledge.infrastructure.storage.exception.StorageException;
import com.domainknowledge.infrastructure.storage.exception.StorageObjectNotFoundException;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import java.io.InputStream;

/**
 * Onde ficam os arquivos originais. O upload grava, a indexação lê. A
 * implementação é S3; quem usa não conhece o SDK. Qualquer falha de
 * comunicação vira {@link StorageException}.
 */
public interface ObjectStorage {

    /** Grava (ou sobrescreve) o objeto, lendo o conteúdo em stream. */
    void put(ObjectUpload upload, InputStream content);

    /**
     * Abre o conteúdo do objeto em stream. Quem chama fecha o stream.
     *
     * @throws StorageObjectNotFoundException se o objeto não existe
     */
    InputStream get(String key);

    boolean exists(String key);
}
