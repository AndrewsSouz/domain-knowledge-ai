package com.domainknowledge.document.model;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Documento enviado pela API. Só leitura do lado da aplicação: o status muda
 * por {@code UPDATE} condicional no {@code DocumentRepository}, chamado pelo
 * {@code DocumentLifecycle}. Por isso a entidade não tem setters.
 *
 * <p>O builder é privado: todo documento nasce por {@link #received}, que
 * garante status {@code RECEIVED} e versão 1.
 */
@Entity
@Table(name = "document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
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
    @Getter(AccessLevel.NONE)
    @Builder.Default
    private boolean isNew = true;

    /** Documento novo, em {@link DocumentStatus#RECEIVED}. */
    public static Document received(NewDocument data, Instant now) {
        // O Postgres guarda microssegundos; truncar evita diferença ao reler.
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        return Document.builder()
                .id(data.id())
                .title(data.title())
                .originalFilename(data.originalFilename())
                .contentType(data.contentType())
                .sizeBytes(data.sizeBytes())
                .sha256(data.sha256())
                .storageKey(data.storageKey())
                .uploadedBy(data.uploadedBy())
                .version(1)
                .status(DocumentStatus.RECEIVED)
                .createdAt(at)
                .updatedAt(at)
                .build();
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
}
