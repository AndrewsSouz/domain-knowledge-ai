package com.domainknowledge.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Vetores de teste conhecidos do SHA-256 (FIPS 180-2). */
class ContentHasherTest {

    @Test
    void hashesKnownInput() throws IOException {
        assertThat(ContentHasher.sha256(stream("abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashesEmptyInput() throws IOException {
        assertThat(ContentHasher.sha256(stream("")))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
