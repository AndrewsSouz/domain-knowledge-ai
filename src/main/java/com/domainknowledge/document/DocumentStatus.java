package com.domainknowledge.document;

/**
 * Onde o documento está no pipeline. As transições válidas ficam no
 * {@code DocumentLifecycle}, nunca espalhadas pelo código.
 */
public enum DocumentStatus {

    /** Original no storage e registro no banco; evento ainda não confirmado pelo Kafka. */
    RECEIVED,

    /** Evento publicado; esperando o consumidor. */
    QUEUED,

    /** Um consumidor assumiu o documento e está indexando. */
    PROCESSING,

    /** Indexado; pronto pra consulta. */
    INDEXED,

    /** A indexação falhou; o motivo fica em {@code errorMessage}. */
    FAILED
}
