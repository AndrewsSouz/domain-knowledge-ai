package com.domainknowledge.ingestion.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 do conteúdo, lido em stream: o arquivo nunca fica inteiro na memória. */
public final class ContentHasher {

    private ContentHasher() {
    }

    /** @return o hash em hexadecimal minúsculo (64 caracteres) */
    public static String sha256(InputStream content) throws IOException {
        MessageDigest digest = newSha256();
        try (DigestInputStream in = new DigestInputStream(content, digest)) {
            // Os bytes passam pelo digest e são descartados.
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Toda JVM tem SHA-256", e);
        }
    }
}
