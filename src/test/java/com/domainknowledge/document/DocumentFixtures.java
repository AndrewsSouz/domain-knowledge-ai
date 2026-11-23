package com.domainknowledge.document;

import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.test.util.ReflectionTestUtils;

/** Dados de teste para documentos. */
public final class DocumentFixtures {

    /** Horário fixo dos documentos de teste. */
    public static final Instant CREATED_AT = Instant.parse("2026-11-22T14:03:00Z");

    /** Mensagem de erro dos documentos de teste em {@code FAILED}. */
    public static final String ERROR_MESSAGE = "hash diferente";

    private DocumentFixtures() {
    }

    /** PDF novo com hash aleatório, pra não colidir no {@code unique}. */
    public static NewDocument newDocument() {
        return newDocument(randomSha256());
    }

    public static NewDocument newDocument(String sha256) {
        UUID id = UUID.randomUUID();
        return NewDocument.builder()
                .id(id)
                .title("Política de férias")
                .originalFilename("politica-ferias.pdf")
                .contentType("application/pdf")
                .sizeBytes(1024)
                .sha256(sha256)
                .storageKey("documents/" + id + "/v1/original.pdf")
                .build();
    }

    /**
     * Documento de verdade (não um mock) já no status pedido, sem banco. Em
     * produção o status só muda pelo {@code DocumentLifecycle}; aqui o teste
     * escreve o campo direto pra montar o cenário.
     */
    public static Document documentIn(DocumentStatus status) {
        Document document = Document.received(newDocument(), CREATED_AT);
        ReflectionTestUtils.setField(document, "status", status);
        if (status == DocumentStatus.FAILED) {
            ReflectionTestUtils.setField(document, "errorMessage", ERROR_MESSAGE);
        }
        return document;
    }

    public static String randomSha256() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
