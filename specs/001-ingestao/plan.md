# Plano técnico — Fatia 1, ingestão (spec 001)

Derivado da spec 001 v1.0.0. Aqui fica o "como"; o "por quê" das escolhas
maiores está nos ADRs. Nomes de código em inglês.

## 1. Versões fixadas

Todas publicadas há pelo menos duas semanas em 22/11/2026, menos o JDK (ver a
nota).

| Peça | Versão | Publicada em |
| --- | --- | --- |
| JDK | Temurin 25.0.5.1+2 | 17/11/2026 (exceção: correção da Adoptium sobre a 25.0.5 de outubro) |
| Gradle (wrapper) | 9.8.2 | 19/10/2026 |
| Spring Boot | 4.1.2 | 22/10/2026 |
| `io.spring.dependency-management` | 1.1.7 | 12/2024 |
| AWS SDK v2 (BOM) | 2.57.11 | 06/11/2026 |
| Apache Tika (`tika-core`) | 4.2.0 | 25/10/2026 |
| Lombok (pelo BOM do Boot) | 1.18.48 | 01/09/2026 |
| Pelo BOM do Boot 4.1.2 (conferido na T001) | Spring Framework 7.0.10, Spring Kafka 4.1.2 (cliente Kafka 4.2.2), Hibernate 7.4.13, Flyway 12.4.0, Jackson 3.1.7, Testcontainers 2.0.5 | — |
| Imagem Postgres | `pgvector/pgvector:0.8.6-pg18` | 13/08/2026 |
| Imagem Kafka | `apache/kafka:4.3.2` | 20/10/2026 |
| Imagem storage | `rustfs/rustfs:1.0.4` | 25/10/2026 |

O Spring AI não entra nesta fatia (nenhuma chamada de IA ainda); a versão é
decidida no plan da fatia 2.

## 2. Estrutura do projeto

```
domain-knowledge-ai/
├── build.gradle, settings.gradle, gradle.properties
├── docker-compose.yml, .env.example
├── docs/ (demo.http entra aqui)
└── src/
    ├── main/java/com/domainknowledge/
    │   ├── DomainKnowledgeApplication.java
    │   ├── document/      # o documento e o ciclo de vida dele
    │   ├── ingestion/     # lado síncrono: upload e publicação
    │   ├── indexing/      # lado assíncrono: consumo e processamento
    │   ├── infrastructure/
    │   │   └── storage/   # S3 (fatia 2: ai/, cliente do Gemini)
    │   └── shared/        # config (Clock), erros (Problem Details), logs (MDC)
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/V1__create_document.sql
    └── test/java/com/domainknowledge/ (mesmos pacotes + support/)
```

Pacote base `com.domainknowledge`. No primeiro nível ficam os **domínios**
(`document`, `ingestion`, `indexing`; `query` na fatia 3), organizados por
conceito de negócio, como no DDD. O que fala com sistema externo (S3, e o
provedor de IA na fatia 2) fica em `infrastructure/`, um pacote por sistema.
Dentro de cada domínio ou sistema, subpacotes por papel da classe, criados
conforme aparecem:

| Subpacote | O que vai | Exemplo |
| --- | --- | --- |
| `model` | entidades, enums, records de dados | `document.model.Document`, `NewDocument` |
| `exception` | exceções do domínio | `document.exception.DuplicateDocumentException` |
| `repository` | Spring Data | `document.repository.DocumentRepository` |
| `service` | regras, orquestração e interfaces que o resto da aplicação usa | `document.service.DocumentLifecycle`, `infrastructure.storage.service.ObjectStorage` |
| `integration` | clientes de sistemas externos (o `*Integration` do esboço) | `infrastructure.storage.integration.S3Integration` |
| `controller` | endpoints HTTP e DTOs de resposta | `document.controller.DocumentController` |
| `config` | `@Configuration` e propriedades | `shared.config.ClockConfiguration` |
| `health` | indicadores do `/actuator/health` | `infrastructure.storage.health.StorageHealthIndicator` |

Regra do ADR 0002: um pacote não usa o repositório de outro. `ingestion` e
`indexing` mudam status só pelo `DocumentLifecycle`. É convenção de código,
seguida na escrita e conferida na revisão.

## 3. Do esboço inicial para as classes

| Esboço (`docs/diagramas/`) | Classe | Pacote | O que faz |
| --- | --- | --- | --- |
| `/v1/ingest` | `DocumentController` | `document` | `POST/GET /v1/documents`. Fino: recebe, chama o serviço, traduz o resultado em HTTP. |
| `IngestionQueueFacade` | `DocumentUploadService` | `ingestion` | Orquestra o upload: valida, calcula o hash, trata duplicado, grava no storage, registra, publica. Vira serviço porque faz mais do que "mandar pra fila". |
| `IngestionProducer` | `IngestionProducer` | `ingestion` | Só publica o `DocumentUploadedEvent`. Não toca no banco. |
| Kafka | tópico `document-uploaded` | — | 3 partições, chave = `documentId`. |
| `IngestionConsumer` | `IngestionConsumer` | `indexing` | Só recebe o evento, põe o `documentId` no log e chama o serviço. Não toca no banco. |
| `IngestionService` | `DocumentIndexingService` | `indexing` | Assume o documento (`PROCESSING`), chama o indexador, marca `INDEXED` ou `FAILED`. |
| `DocumentUtil` | `DocumentIndexer` (interface) + `ChecksumOnlyIndexer` (stub) | `indexing` | Fatia 1: baixa e confere o hash. Fatia 2: troca por extração + chunking + embeddings. |
| `S3Integration` | `ObjectStorage` (interface) + `S3Integration` | `infrastructure.storage` | `put`, `get`, `exists`. Usado pelos dois lados. |
| `DocumentRepository` | `DocumentRepository` | `document` | Spring Data JPA + updates condicionais de status (§5). |
| — (novo) | `DocumentLifecycle` | `document` | Única porta para mudar status. Encapsula as transições válidas. |

Recursos do Java 25 usados:

- `record` para o evento, os DTOs e os resultados, criados por builder
  (`@Builder` do Lombok, ADR 0001).
- `sealed interface UploadResult permits Accepted, AlreadyExists` e
  `switch` com *pattern matching* no controller para escolher `202` ou `200`:
  o compilador obriga a tratar todos os casos.
- *Virtual threads* (`spring.threads.virtual.enabled=true`): cada requisição
  roda numa thread leve; upload esperando disco/rede não prende thread do
  sistema.

## 4. API

`POST /v1/documents` (`multipart/form-data`: `file`, `title?`)

| Situação | Status | Corpo |
| --- | --- | --- |
| Novo, enfileirado | `202` + `Location: /v1/documents/{id}` | `DocumentResponse` |
| Duplicado em `QUEUED`/`PROCESSING`/`INDEXED` | `200` | `DocumentResponse` do existente |
| Duplicado em `RECEIVED`/`FAILED` | `202` | reenfileirado |
| Vazio | `400` | Problem Details |
| Acima de 20 MB | `413` | Problem Details |
| Nem PDF nem DOCX | `415` | Problem Details |
| Kafka recusou | `503` | Problem Details com `documentId` |

`GET /v1/documents/{id}` → `200 DocumentResponse` ou `404`.
`GET /v1/documents?status=&page=0&size=20` → página, ordem `createdAt desc`.

```java
record DocumentResponse(UUID id, String title, String originalFilename,
    String contentType, long sizeBytes, String sha256, int version,
    DocumentStatus status, String errorMessage, Instant createdAt, Instant updatedAt) {}
```

Problem Details: `spring.mvc.problemdetails.enabled=true` + um
`@RestControllerAdvice` (`ApiExceptionHandler`) para as exceções do domínio:
`EmptyFileException` → 400, `UnsupportedDocumentTypeException` → 415,
`MaxUploadSizeExceededException` → 413, `DocumentNotFoundException` → 404,
`EventPublishException` → 503.

## 5. Banco e ciclo de vida

`V1__create_document.sql`:

```sql
create table document (
    id                uuid primary key,
    title             varchar(255) not null,
    original_filename varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null,
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
create index idx_document_status_created_at on document (status, created_at desc);
```

A extensão `vector` só entra na migration da fatia 2.

Na entidade: o `id` vem da aplicação, então `Document` implementa
`Persistable` pra o `save` fazer `INSERT` direto, sem `SELECT` antes; `sha256`
usa `@JdbcTypeCode(SqlTypes.CHAR)` pra bater com o `char(64)` na validação do
schema; a coluna `version` é a versão do documento, não lock otimista (sem
`@Version`). Lombok: `@Getter` e builder privado, sem setters; todo
documento nasce por `Document.received(NewDocument, Instant)` e o status só
muda pelos `UPDATE` condicionais.

**Transições com `UPDATE` condicional.** Cada mudança de status é um
`UPDATE ... WHERE id = ? AND status IN (...)` e devolve quantas linhas mudou.
Zero linhas = a transição não vale naquele estado. Isso resolve concorrência
no banco, sem `SELECT` antes e sem lock na aplicação.

| Método do `DocumentLifecycle` | De | Para |
| --- | --- | --- |
| `register(...)` (insert) | — | `RECEIVED` |
| `markQueued(id)` | `RECEIVED` | `QUEUED` |
| `claimForProcessing(id)` | `RECEIVED`, `QUEUED`, `PROCESSING` | `PROCESSING` |
| `markIndexed(id)` | `PROCESSING` | `INDEXED` |
| `markFailed(id, msg)` | `PROCESSING` | `FAILED` |
| `resetForRetry(id)` | `RECEIVED`, `FAILED` | `RECEIVED` (limpa o erro) |

Dois detalhes importantes:

- `claimForProcessing` aceita `RECEIVED` porque **o consumidor pode ser mais
  rápido que o upload**: o evento chega e é processado antes de o upload
  gravar `QUEUED`. Por isso `markQueued` só muda de `RECEIVED`: se o
  documento já andou, ele não volta pra trás.
- `claimForProcessing` aceita `PROCESSING` por causa do RF6 (aplicação caiu
  no meio, mensagem reentregue).

Na implementação, as seis transições usam um `UPDATE` só no
`DocumentRepository` (`transition(id, from, to, errorMessage, now)`), que
sempre escreve o `error_message`: ele só existe em `FAILED`, e toda saída de
`FAILED` limpa o erro. Os métodos do `DocumentLifecycle` devolvem `boolean`
(valeu ou não) e quem chama decide o que fazer. O `register` traduz a
violação do `unique` de `sha256` (`document_sha256_key`) em
`DuplicateDocumentException`. O horário vem de um bean `Clock` (`shared`).

## 6. Upload, passo a passo (`DocumentUploadService`)

1. Arquivo vazio → `EmptyFileException`. O tamanho máximo é barrado antes,
   pelo `spring.servlet.multipart.max-file-size=20MB`.
2. `ContentTypeDetector` (Tika): lê os primeiros bytes e usa o nome do
   arquivo como dica. Aceita só `application/pdf` e
   `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
   Um `.txt` renomeado pra `.pdf` é barrado (415). Devolve
   `Optional<DocumentType>` (`ingestion.model`: `PDF`, `DOCX`, com media type
   e extensão da chave no storage). Limites do `tika-core` 4.2.0, conferidos
   na T301: DOCX sem a extensão `.docx` é recusado (ele vê um pacote Office,
   mas não sabe se é Word ou Excel); um ZIP qualquer chamado `.docx` passa e
   só falha na extração (fatia 2); arquivo vazio não tem bytes, então vale o
   nome, e por isso o passo 1 vem antes.
3. SHA-256 do conteúdo, calculado em streaming.
4. Procura por `sha256`:
   - existe em `QUEUED`/`PROCESSING`/`INDEXED` → `AlreadyExists`;
   - existe em `RECEIVED`/`FAILED` → `resetForRetry`, publica, `markQueued`
     → `Accepted`;
   - não existe → segue.
5. `id = UUID.randomUUID()`, `storageKey = documents/{id}/v1/original.{pdf|docx}`.
6. `ObjectStorage.put` (com o SHA-256 nos metadados do objeto).
7. `DocumentLifecycle.register` → `RECEIVED`. Se outra requisição gravou o
   mesmo hash no meio tempo, vem `DuplicateDocumentException` e trata como
   duplicado.
8. `IngestionProducer.publish(event)` e espera o *ack* do Kafka (timeout de
   5 s). Falhou → `EventPublishException` → `503`, documento fica `RECEIVED`.
9. `markQueued` → `Accepted`.

Ordem storage → banco → Kafka: se cair entre o storage e o banco, sobra um
arquivo órfão (inofensivo). Se cair entre o banco e o Kafka, sobra um
`RECEIVED` (visível e recuperável reenviando). O contrário (evento sem
arquivo) nunca acontece.

## 7. Kafka

- Tópico `document-uploaded`, criado por um bean `NewTopic` (3 partições,
  réplica 1).
- **Producer:** `acks=all`, idempotência ligada (padrão do cliente), valor
  em JSON (serializador Jackson do Spring Kafka), chave `String`
  (`documentId`). Sem cabeçalho de tipo: o consumidor sabe o tipo.
- **Consumer:** grupo `indexing`, `auto-offset-reset=earliest`,
  `ErrorHandlingDeserializer` em volta do JSON (mensagem malformada não trava
  o tópico: vai pro log e é descartada).
- **Erros:** `DefaultErrorHandler` com backoff exponencial (1 s, 2 s, 4 s; 3
  tentativas). `StorageObjectNotFoundException` e
  `ChecksumMismatchException` são não retentáveis. Esgotou → um
  `ConsumerRecordRecoverer` chama `markFailed` com a mensagem do erro. DLQ fica
  pra fatia 4.

```java
record DocumentUploadedEvent(UUID eventId, String eventType, Instant occurredAt,
    UUID documentId, int version, String storageKey, String contentType, String sha256) {}
```

## 8. Indexação (`DocumentIndexingService`)

1. `claimForProcessing(documentId)`. Zero linhas → já `INDEXED`/`FAILED`
   → log e sai (RF6).
2. `version` do evento diferente da do banco → log e sai (preparado pra
   versões na fatia 4).
3. `DocumentIndexer.index(document)`: na fatia 1, o `ChecksumOnlyIndexer`
   baixa o objeto, recalcula o SHA-256 e compara.
4. Deu certo → `markIndexed`. Erro não retentável → `markFailed`. Erro
   retentável → relança (o error handler do §7 cuida).

## 9. Storage

- `S3Client` do AWS SDK v2 com `endpointOverride`, `forcePathStyle(true)`
  (exigido pelo RustFS) e credenciais do `.env`.
- `StorageProperties` (`storage.endpoint`, `storage.bucket`,
  `storage.access-key`, `storage.secret-key`, `storage.create-bucket`).
- `BucketInitializer`: na subida, cria o bucket se não existir e
  `create-bucket=true`.
- `NoSuchKeyException` → `StorageObjectNotFoundException` (nossa, pra não
  vazar o SDK pra fora do pacote). Qualquer outra falha do SDK vira
  `StorageException`, que é a classe mãe.

Na implementação:

- `ObjectStorage` (`infrastructure.storage.service`) com `put(ObjectUpload, InputStream)`,
  `get(key)` (stream; quem chama fecha) e `exists(key)`. `ObjectUpload` é um
  record com builder: chave, content type, tamanho e SHA-256 (vai no
  metadado `x-amz-meta-sha256`).
- Checksum do SDK em `WHEN_REQUIRED`: o padrão do AWS SDK v2 manda checksum
  em todo upload, num formato que nem todo storage compatível aceita.
- `BucketInitializer` roda na criação dos beans, antes de o servidor web
  aceitar requisição; storage fora do ar derruba a subida (falha rápido).
- "Não existe" no `HEAD` chega só como `404` (sem corpo), então `exists` e
  o `BucketInitializer` olham o status, não o tipo da exceção.

## 10. Operação

- **Logs:** `documentId` no MDC; no controller depois de gerar o id, no
  consumer antes de processar, limpo no `finally`. Padrão de log com
  `%X{documentId}`.
- **Health:** `/actuator/health` com banco (padrão do Boot),
  `StorageHealthIndicator` (`headBucket` com timeout de 3 s; aparece como
  `storage`) e `KafkaHealthIndicator`
  (`describeCluster` com timeout de 3 s; o Boot não traz um pronto).
- **Configuração:** `application.yml` importa o `.env` da raiz
  (`spring.config.import: optional:file:.env[.properties]`), o mesmo que o
  compose lê, com padrão igual ao do compose; `./gradlew bootRun` funciona
  com ou sem `.env`.

## 11. docker-compose.yml

| Serviço | Portas | Limite de memória | Observação |
| --- | --- | --- | --- |
| `postgres` | 5432 | 256 MB | banco `dka`, healthcheck `pg_isready` |
| `kafka` | 9092 | 768 MB | KRaft com um nó (broker + controller), `KAFKA_HEAP_OPTS=-Xmx384m -Xms384m` |
| `storage` (RustFS) | 9000 (S3), 9001 (console em `/rustfs/console/`) | 256 MB | `RUSTFS_ACCESS_KEY`/`RUSTFS_SECRET_KEY` do `.env`, healthcheck em `/health` |

Meta: menos de 1,5 GB somando os três. Medido na T003, ocioso: ≈ 435 MB
(Kafka 320 MB, Postgres 55 MB, RustFS 60 MB). Postgres 18 monta o volume em
`/var/lib/postgresql` (não mais em `.../data`).

## 12. Testes

| Tipo | Ferramenta | Cobre |
| --- | --- | --- |
| Unitário | JUnit 5 + AssertJ | `ContentTypeDetector`, cálculo de hash, regras do `DocumentUploadService` com dublês |
| Fatia web | `@WebMvcTest` | status HTTP e Problem Details do controller (A4, A7) |
| Integração | `@SpringBootTest` + Testcontainers | Postgres e Kafka com `@ServiceConnection`; RustFS com `GenericContainer` + `DynamicPropertyRegistrar`; Awaitility pra esperar o assíncrono (A1, A2, A3, A5, A6, A8, A9) |

- Containers como beans numa `@TestConfiguration`
  (`support/TestcontainersConfiguration`), com as mesmas imagens do compose.
  A anotação `@IntegrationTest` junta `@SpringBootTest` e essa configuração;
  o cache de contexto do Spring Test reaproveita os mesmos containers entre
  as classes que usam a mesma configuração.
- Testcontainers 2: `org.testcontainers.postgresql.PostgreSQLContainer` e
  `org.testcontainers.kafka.KafkaContainer` (pacotes novos, sem genérico). A
  imagem do pgvector entra com `asCompatibleSubstituteFor("postgres")`.
- RustFS sem `@ServiceConnection` (o Boot não tem conexão automática pra S3):
  propriedades `storage.*` por um bean `DynamicPropertyRegistrar`.
- Tempo medido na T004: ≈ 40 s pra subir os três containers, uma vez por
  execução.
- Fixtures em `src/test/resources/fixtures/`: `sample.pdf` (PDF 1.4 de uma
  página) e `sample.docx` (pacote OOXML mínimo), os dois com o mesmo texto
  curto e extraível; `fake.pdf` (texto com extensão `.pdf`); `empty.pdf`
  (0 bytes). Menos de 1 KB cada.
- Em máquina com pouca RAM: rodar os testes de integração com o `docker compose`
  parado.

## 13. Riscos

- **Spring Boot 4 é recente:** artefatos mudaram de nome em relação a
  tutoriais do Boot 3. Conferido na T001:
  - starter web agora é `spring-boot-starter-webmvc`;
  - Kafka e Flyway ganharam starter próprio (`spring-boot-starter-kafka`,
    `spring-boot-starter-flyway`);
  - teste também é modular: um `*-test` por tecnologia
    (`spring-boot-starter-webmvc-test`, `-kafka-test`...);
  - Testcontainers 2 renomeou os módulos (`testcontainers-postgresql`,
    `testcontainers-kafka`);
  - Jackson 3 mudou de pacote (`tools.jackson` em vez de
    `com.fasterxml.jackson`).
- **RustFS jovem:** plano B é o Adobe S3Mock (ADR 0005).
- **RAM:** se o build com testes ficar lento, limitar o heap do daemon do
  Gradle em `gradle.properties`.
