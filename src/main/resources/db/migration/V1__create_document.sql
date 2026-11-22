-- Documento enviado pela API e o estado dele no pipeline de indexação.
-- O arquivo original fica no storage S3 (storage_key); aqui só metadados.
create table document (
    id                uuid primary key,
    title             varchar(255) not null,
    original_filename varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null,
    -- SHA-256 do conteúdo em hexadecimal; o unique barra o mesmo arquivo duas vezes.
    sha256            char(64)     not null unique,
    storage_key       varchar(512) not null,
    version           integer      not null default 1,
    status            varchar(20)  not null
        check (status in ('RECEIVED', 'QUEUED', 'PROCESSING', 'INDEXED', 'FAILED')),
    error_message     text,
    uploaded_by       varchar(100),
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null
);

-- Atende a listagem GET /v1/documents?status=..., mais recentes primeiro.
create index idx_document_status_created_at on document (status, created_at desc);
