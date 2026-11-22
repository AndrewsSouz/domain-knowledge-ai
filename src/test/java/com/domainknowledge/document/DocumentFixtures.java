package com.domainknowledge.document;

import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Dados de teste para documentos (empresa fictícia). */
final class DocumentFixtures {

    private DocumentFixtures() {
    }

    /** PDF novo com hash aleatório, pra não colidir no {@code unique}. */
    static NewDocument newDocument() {
        return newDocument(randomSha256());
    }

    static NewDocument newDocument(String sha256) {
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

    static String randomSha256() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
