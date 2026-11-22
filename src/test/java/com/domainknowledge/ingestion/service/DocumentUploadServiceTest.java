package com.domainknowledge.ingestion.service;

import static com.domainknowledge.document.model.DocumentStatus.FAILED;
import static com.domainknowledge.document.model.DocumentStatus.INDEXED;
import static com.domainknowledge.document.model.DocumentStatus.QUEUED;
import static com.domainknowledge.document.model.DocumentStatus.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.domainknowledge.document.exception.DuplicateDocumentException;
import com.domainknowledge.document.model.Document;
import com.domainknowledge.document.model.DocumentStatus;
import com.domainknowledge.document.model.NewDocument;
import com.domainknowledge.document.service.DocumentLifecycle;
import com.domainknowledge.document.service.DocumentQueryService;
import com.domainknowledge.infrastructure.storage.model.ObjectUpload;
import com.domainknowledge.infrastructure.storage.service.ObjectStorage;
import com.domainknowledge.ingestion.exception.EmptyFileException;
import com.domainknowledge.ingestion.exception.EventPublishException;
import com.domainknowledge.ingestion.exception.UnsupportedDocumentTypeException;
import com.domainknowledge.ingestion.messaging.IngestionProducer;
import com.domainknowledge.ingestion.model.DocumentUploadedEvent;
import com.domainknowledge.ingestion.model.UploadRequest;
import com.domainknowledge.ingestion.model.UploadResult;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

/**
 * Regras do upload com dublês: storage, banco e Kafka são mocks; o detector de
 * tipo é o de verdade, sobre as fixtures.
 */
class DocumentUploadServiceTest {

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final DocumentLifecycle lifecycle = mock(DocumentLifecycle.class);
    private final DocumentQueryService documents = mock(DocumentQueryService.class);
    private final IngestionProducer producer = mock(IngestionProducer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T14:03:00Z"), ZoneOffset.UTC);

    private DocumentUploadService service;

    @BeforeEach
    void setUp() {
        service = new DocumentUploadService(new ContentTypeDetector(), storage, lifecycle, documents, producer, clock);
    }

    @Test
    void newDocumentIsStoredRegisteredPublishedAndQueued() throws IOException {
        Document registered = document(RECEIVED);
        when(documents.findBySha256(anyString())).thenReturn(Optional.empty());
        when(lifecycle.register(any())).thenReturn(registered);
        when(documents.findById(registered.getId())).thenReturn(Optional.of(registered));

        UploadResult result = service.upload(request("sample.pdf", "regras.pdf", null));

        assertThat(result).isInstanceOf(UploadResult.Accepted.class);
        var order = inOrder(storage, lifecycle, producer);
        ArgumentCaptor<ObjectUpload> upload = ArgumentCaptor.forClass(ObjectUpload.class);
        order.verify(storage).put(upload.capture(), any(InputStream.class));
        ArgumentCaptor<NewDocument> data = ArgumentCaptor.forClass(NewDocument.class);
        order.verify(lifecycle).register(data.capture());
        order.verify(producer).publish(any(DocumentUploadedEvent.class));
        order.verify(lifecycle).markQueued(registered.getId());

        String expectedHash;
        try (InputStream in = new ClassPathResource("fixtures/sample.pdf").getInputStream()) {
            expectedHash = ContentHasher.sha256(in);
        }
        assertThat(data.getValue().sha256()).isEqualTo(expectedHash);
        assertThat(data.getValue().title()).isEqualTo("regras.pdf");
        assertThat(data.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(data.getValue().storageKey())
                .isEqualTo("documents/" + data.getValue().id() + "/v1/original.pdf")
                .isEqualTo(upload.getValue().key());
        assertThat(upload.getValue().sha256()).isEqualTo(expectedHash);
    }

    @Test
    void usesTheGivenTitleWhenPresent() throws IOException {
        Document registered = document(RECEIVED);
        when(documents.findBySha256(anyString())).thenReturn(Optional.empty());
        when(lifecycle.register(any())).thenReturn(registered);
        when(documents.findById(registered.getId())).thenReturn(Optional.of(registered));

        service.upload(request("sample.docx", "regras.docx", "  Regras completas  "));

        ArgumentCaptor<NewDocument> data = ArgumentCaptor.forClass(NewDocument.class);
        verify(lifecycle).register(data.capture());
        assertThat(data.getValue().title()).isEqualTo("Regras completas");
        assertThat(data.getValue().storageKey()).endsWith("/v1/original.docx");
    }

    @Test
    void emptyFileIsRejectedBeforeAnything() throws IOException {
        assertThatThrownBy(() -> service.upload(request("empty.pdf", "empty.pdf", null)))
                .isInstanceOf(EmptyFileException.class);
        verifyNoInteractions(storage, lifecycle, documents, producer);
    }

    @Test
    void unsupportedTypeIsRejectedBeforeAnything() throws IOException {
        assertThatThrownBy(() -> service.upload(request("fake.pdf", "fake.pdf", null)))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        verifyNoInteractions(storage, lifecycle, documents, producer);
    }

    @Test
    void duplicateAlreadyInPipelineIsReturnedWithoutPublishing() throws IOException {
        for (DocumentStatus status : new DocumentStatus[] {QUEUED, DocumentStatus.PROCESSING, INDEXED}) {
            Document existing = document(status);
            when(documents.findBySha256(anyString())).thenReturn(Optional.of(existing));

            UploadResult result = service.upload(request("sample.pdf", "regras.pdf", null));

            assertThat(result).isEqualTo(new UploadResult.AlreadyExists(existing));
        }
        verifyNoInteractions(storage, producer);
        verify(lifecycle, never()).register(any());
    }

    @Test
    void failedDuplicateIsRequeuedWithoutStoringAgain() throws IOException {
        Document existing = document(FAILED);
        when(documents.findBySha256(anyString())).thenReturn(Optional.of(existing));
        when(lifecycle.resetForRetry(existing.getId())).thenReturn(true);
        when(documents.findById(existing.getId())).thenReturn(Optional.of(existing));

        UploadResult result = service.upload(request("sample.pdf", "regras.pdf", null));

        assertThat(result).isInstanceOf(UploadResult.Accepted.class);
        var order = inOrder(lifecycle, producer);
        order.verify(lifecycle).resetForRetry(existing.getId());
        order.verify(producer).publish(any(DocumentUploadedEvent.class));
        order.verify(lifecycle).markQueued(existing.getId());
        verifyNoInteractions(storage);
    }

    @Test
    void publishFailureLeavesTheDocumentReceived() throws IOException {
        Document registered = document(RECEIVED);
        when(documents.findBySha256(anyString())).thenReturn(Optional.empty());
        when(lifecycle.register(any())).thenReturn(registered);
        doThrow(new EventPublishException(registered.getId(), new RuntimeException("broker fora")))
                .when(producer).publish(any());

        assertThatThrownBy(() -> service.upload(request("sample.pdf", "regras.pdf", null)))
                .isInstanceOf(EventPublishException.class);
        verify(lifecycle, never()).markQueued(any());
    }

    @Test
    void concurrentRegistrationOfTheSameFileIsTreatedAsDuplicate() throws IOException {
        Document winner = document(QUEUED);
        when(documents.findBySha256(anyString())).thenReturn(Optional.empty(), Optional.of(winner));
        when(lifecycle.register(any())).thenThrow(new DuplicateDocumentException("x", null));

        UploadResult result = service.upload(request("sample.pdf", "regras.pdf", null));

        assertThat(result).isEqualTo(new UploadResult.AlreadyExists(winner));
        verifyNoInteractions(producer);
    }

    private static UploadRequest request(String fixture, String filename, String title) throws IOException {
        ClassPathResource resource = new ClassPathResource("fixtures/" + fixture);
        return UploadRequest.builder()
                .originalFilename(filename)
                .title(title)
                .sizeBytes(resource.contentLength())
                .content(resource)
                .build();
    }

    private static Document document(DocumentStatus status) {
        Document document = mock(Document.class);
        when(document.getId()).thenReturn(UUID.randomUUID());
        when(document.getStatus()).thenReturn(status);
        return document;
    }
}
