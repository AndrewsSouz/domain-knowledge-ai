package com.domainknowledge.document;

import com.domainknowledge.document.model.NewDocument;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Dados de teste para documentos. */
public final class DocumentFixtures {

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

    public static String randomSha256() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
