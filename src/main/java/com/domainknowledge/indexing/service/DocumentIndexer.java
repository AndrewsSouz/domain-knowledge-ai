package com.domainknowledge.indexing.service;

import com.domainknowledge.document.model.Document;

/**
 * O trabalho de indexar um documento. Na fatia 1 é um stub
 * ({@link ChecksumOnlyIndexer}); na fatia 2 vira extração do texto, divisão em
 * trechos, embeddings e gravação no pgvector, sem mudar quem chama.
 *
 * <p>Precisa poder ser repetido sem efeito duplicado: o Kafka entrega "pelo
 * menos uma vez", e um documento em {@code PROCESSING} é processado de novo
 * depois de uma queda (RF6).
 */
public interface DocumentIndexer {

    /** Lança exceção se não deu pra indexar; quem chama decide se tenta de novo. */
    void index(Document document);
}
