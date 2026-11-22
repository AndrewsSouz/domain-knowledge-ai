package com.domainknowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Documento enviado pela API. Só leitura do lado da aplicação: o status muda
 * por {@code UPDATE} condicional no {@link DocumentRepository}, chamado pelo
 * {@code DocumentLifecycle}. Por isso a entidade não tem setters.
 */
@Entity
@Table(name = "document")
public class Document implements Persistable<UUID> {

    @Id
    private UUID id;

    private String title;

    private String originalFilename;

    private String contentType;

    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String sha256;

    private String storageKey;

    /** Versão do documento (fatia 4: nova versão do mesmo documento). Não é lock otimista. */
    private int version;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private String errorMessage;

    private String uploadedBy;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * O id vem da aplicação, então o Spring Data não consegue saber pelo id
     * se é insert ou update; sem isso, o {@code save} faria um SELECT antes.
     */
    @Transient
    private boolean isNew = true;

    protected Document() {
        // exigido pelo JPA
    }

    /** Documento novo, em {@link DocumentStatus#RECEIVED}. */
    static Document received(NewDocument data, Instant now) {
        // O Postgres guarda microssegundos; truncar evita diferença ao reler.
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        Document document = new Document();
        document.id = data.id();
        document.title = data.title();
        document.originalFilename = data.originalFilename();
        document.contentType = data.contentType();
        document.sizeBytes = data.sizeBytes();
        document.sha256 = data.sha256();
        document.storageKey = data.storageKey();
        document.uploadedBy = data.uploadedBy();
        document.version = 1;
        document.status = DocumentStatus.RECEIVED;
        document.createdAt = at;
        document.updatedAt = at;
        return document;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public int getVersion() {
        return version;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
