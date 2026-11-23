package com.domainknowledge.shared.util;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Contexto dos logs (RNF3). O MDC é um mapa por thread que o logger põe em
 * cada linha: com o {@code documentId} nele, todas as linhas do fluxo de um
 * documento saem com {@code [id]}, dos dois lados do Kafka, sem repetir o id
 * em cada mensagem. O padrão está no {@code application.yml}
 * ({@code logging.pattern.level}).
 */
public final class LogContext {

    public static final String DOCUMENT_ID = "documentId";

    private LogContext() {
    }

    /** Use com try-with-resources: o id sai do MDC no fim do bloco, mesmo com exceção. */
    public static MDC.MDCCloseable documentId(UUID documentId) {
        return MDC.putCloseable(DOCUMENT_ID, documentId.toString());
    }
}
