package com.domainknowledge.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.domainknowledge.ingestion.model.DocumentType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentTypeDetectorTest {

    private final ContentTypeDetector detector = new ContentTypeDetector();

    @Test
    void detectsPdf() throws IOException {
        assertThat(detect("sample.pdf", "regras.pdf")).contains(DocumentType.PDF);
    }

    @Test
    void detectsDocx() throws IOException {
        assertThat(detect("sample.docx", "regras.docx")).contains(DocumentType.DOCX);
    }

    @Test
    void pdfIsDetectedByContentEvenWithoutExtension() throws IOException {
        assertThat(detect("sample.pdf", "regras")).contains(DocumentType.PDF);
    }

    @Test
    void rejectsTextRenamedToPdf() throws IOException {
        assertThat(detect("fake.pdf", "fake.pdf")).isEmpty();
    }

    @Test
    void rejectsDocxRenamedToPdf() throws IOException {
        // O conteúdo é ZIP e o nome diz PDF: os dois discordam, então não é PDF.
        assertThat(detect("sample.docx", "regras.pdf")).isEmpty();
    }

    private Optional<DocumentType> detect(String fixture, String filename) throws IOException {
        try (InputStream content = getClass().getResourceAsStream("/fixtures/" + fixture)) {
            return detector.detect(content, filename);
        }
    }
}
