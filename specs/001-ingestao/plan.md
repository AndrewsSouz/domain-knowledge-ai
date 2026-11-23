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
    │   │   ├── kafka/     # admin client e health do cluster
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
| `model` | entidades, enums e records de dados (POJOs), inclusive os DTOs de resposta da API | `document.model.Document`, `NewDocument`, `DocumentResponse` |
| `exception` | exceções do domínio | `document.exception.DuplicateDocumentException` |
| `repository` | Spring Data | `document.repository.DocumentRepository` |
| `service` | regras, orquestração e interfaces que o resto da aplicação usa | `document.service.DocumentLifecycle`, `infrastructure.storage.service.ObjectStorage` |
| `integration` | clientes de sistemas externos (o `*Integration` do esboço) | `infrastructure.storage.integration.S3Integration` |
| `messaging` | producer e consumer do Kafka de um domínio | `ingestion.messaging.IngestionProducer` |
| `controller` | endpoints HTTP (só os controllers e o tratamento de erro) | `document.controller.DocumentController`, `ingestion.controller.DocumentUploadController` |
| `config` | `@Configuration` e propriedades | `shared.config.ClockConfiguration` |
| `health` | indicadores do `/actuator/health` | `infrastructure.storage.health.StorageHealthIndicator` |
| `util` | funções puras usadas por mais de um domínio | `shared.util.ContentHasher` |

Regra do ADR 0002: um pacote não usa o repositório de outro. `ingestion` e
`indexing` mudam status só pelo `DocumentLifecycle`. É convenção de código,
seguida na escrita e conferida na revisão.

## 3. Do esboço inicial para as classes

| Esboço (`docs/diagramas/`) | Classe | Pacote | O que faz |
| --- | --- | --- | --- |
| `/v1/ingest` | `DocumentUploadController` + `DocumentController` | `ingestion` + `document` | `POST /v1/documents` no `ingestion`, `GET` no `document`. Dois controllers no mesmo caminho: um só ficaria no `document` dependendo do `ingestion`, que já depende do `document` (ciclo entre pacotes). Finos: recebem, chamam o serviço, traduzem o resultado em HTTP. |
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
- `sealed interface UploadResult` com os records `Accepted` e
  `AlreadyExists` aninhados (com os subtipos no mesmo arquivo, o `permits`
  fica implícito) e
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

Problem Details: `ApiExceptionHandler` (`shared.controller`), um
`@RestControllerAdvice` que estende o `ResponseEntityExceptionHandler` do
Spring. A classe mãe já devolve Problem Details pras exceções do Spring MVC
(parâmetro inválido, parte do multipart faltando, validação), então a
propriedade `spring.mvc.problemdetails.enabled` não é necessária. As do
domínio: `EmptyFileException` → 400, `UnsupportedDocumentTypeException` →
415, `MaxUploadSizeExceededException` → 413, `DocumentNotFoundException` →
404, `EventPublishException` → 503 (com `documentId`), `StorageException`
→ 503. Qualquer outra exceção → 500, também em Problem Details, com mensagem
genérica (o detalhe vai só pro log). A mensagem do 413 lê o limite de
`spring.servlet.multipart.max-file-size`, sem repetir o número.

Na implementação (T304):

- Listagem em `PagedModel` do Spring Data (`content` + `page`), formato
  estável; ordem `createdAt desc` com `id` de desempate; `size` de 1 a 100,
  `page` a partir de 0; fora disso, 400. A ordenação não vem do cliente.
- `title` com até 255 caracteres; nome do arquivo sem caminho e cortado em
  255.
- Limite de upload: `spring.servlet.multipart.max-file-size=20MB`
  (`max-request-size=21MB`). `server.tomcat.max-swallow-size=25MB`: acima do
  limite o Tomcat ainda lê e descarta o resto do corpo pra o cliente receber
  o 413; com o padrão de 2 MB a conexão é cortada (*Broken pipe*), conferido
  na T304.

## 5. Banco e ciclo de vida

`V1__create_document.sql`:

```sql
create table document (
    id                uuid primary key,
    title             varchar(255) not null,
    original_filename varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null,
    sha256            char(64)     not null constraint uk_document_sha256 unique,
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
violação do `unique` de `sha256` (`uk_document_sha256`, nome explícito na
V1) em
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

Na implementação (T303):

- O serviço recebe um `UploadRequest` (`ingestion.model`), não um
  `MultipartFile`: o conteúdo é um `InputStreamSource` do Spring core, que o
  `MultipartFile` já implementa. Cada leitura abre um stream novo (tipo, hash,
  storage), então o arquivo nunca fica inteiro na memória.
- Sem `@Transactional` no serviço: cada passo do `DocumentLifecycle` é uma
  transação curta, e nenhuma transação do banco fica aberta esperando o
  Kafka.
- Leituras do documento por um `DocumentQueryService` (`document.service`):
  `getById` (lança `DocumentNotFoundException`), `findBySha256` e `list`; o
  `DocumentLifecycle` continua só com as mudanças.
- `ContentHasher` calcula o SHA-256 com `DigestInputStream`.
- Corridas tratadas: `DuplicateDocumentException` no `register` vira
  duplicado; `resetForRetry` que não vale (outra requisição reenfileirou
  antes) devolve `AlreadyExists`; `markQueued` que não vale (o consumidor já
  pegou) é ignorado.

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

Na implementação (T302):

- O evento (`ingestion.model`) é o contrato entre o upload e a indexação:
  guarda o nome do tópico (`DocumentUploadedEvent.TOPIC`) e é montado a partir
  do `Document` por `DocumentUploadedEvent.of(document, now)`.
- Serializador `JacksonJsonSerializer` (Spring Kafka 4, Jackson 3), com
  `spring.json.add.type.headers=false`. Datas em ISO-8601, como no exemplo da
  arquitetura §5.
- Kafka fora do ar não pode prender a requisição: `max.block.ms=5000` (achar
  o broker), `request.timeout.ms=3000`, `delivery.timeout.ms=5000`
  (confirmar). O `IngestionProducer` espera o *ack* com um teto de 12 s, só
  como rede de segurança, e traduz qualquer falha em `EventPublishException`
  com o `documentId`.

## 8. Indexação (`DocumentIndexingService`)

1. `getById(documentId)`. Não existe → `DocumentNotFoundException` (não
   retentável).
2. `version` do evento diferente da do banco → log e sai, antes de assumir o
   documento (preparado pra versões na fatia 4).
3. `claimForProcessing(documentId)`. Zero linhas → já `INDEXED`/`FAILED`
   → log e sai (RF6).
4. `DocumentIndexer.index(document)`: na fatia 1, o `ChecksumOnlyIndexer`
   baixa o objeto, recalcula o SHA-256 e compara
   (`ChecksumMismatchException` se diferente).
5. Deu certo → `markIndexed`. Qualquer erro sobe: o error handler do §7
   decide entre tentar de novo e chamar `markFailed`, então o `FAILED` é
   decidido num lugar só.

Na implementação (T401): `IngestionConsumer` (`indexing.messaging`) com
`@KafkaListener` no grupo `indexing`; consumidor com `ErrorHandlingDeserializer`
em volta do `JacksonJsonDeserializer` (tipo fixo `DocumentUploadedEvent`, sem
cabeçalho de tipo), `concurrency: 3` (uma thread por partição) e
`ack-mode: record` (offset confirmado a cada mensagem processada). O
`ContentHasher` foi pra `shared.util`, porque o upload e a indexação usam.

Erros (T402): `DefaultErrorHandler` em `indexing.config`, ligado pelo Boot ao
container. `ExponentialBackOffWithMaxRetries(3)` com 1 s inicial e
multiplicador 2 (1 s, 2 s, 4 s). Não retentáveis:
`StorageObjectNotFoundException`, `ChecksumMismatchException` e
`DocumentNotFoundException` (evento de documento que não existe). O
`MarkFailedRecoverer` (`indexing.messaging`) tira o `documentId` da chave e
chama `markFailed` com a mensagem da exceção do listener (até 1000
caracteres); mensagem malformada (`DeserializationException`) só vai pro log.
`commitRecovered=true`: o offset da mensagem recuperada é confirmado na hora.
A espera do retry acontece na thread da partição: as mensagens de trás
esperam, e a ordem por documento se mantém.

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

- **Logs:** `documentId` no MDC (`shared.util.LogContext`, com
  try-with-resources: sai do MDC no fim do bloco, mesmo com exceção). Do lado
  do upload, no `DocumentUploadService`, que é quem gera o id (o controller
  não conhece o id antes); do lado do Kafka, no `IngestionConsumer` e no
  `MarkFailedRecoverer`, que roda fora do listener. Padrão
  `logging.pattern.level: %5p%replace( [%X{documentId}]){' \[\]', ''}`:
  `[id]` depois do nível, e nada quando a linha não é de um documento. As
  mensagens não repetem o id.
- **Health:** `/actuator/health` com banco (padrão do Boot),
  `StorageHealthIndicator` (`headBucket` com timeout de 3 s; aparece como
  `storage`) e `KafkaHealthIndicator`
  (`describeCluster` com timeout de 3 s; o Boot não traz um pronto), em
  `infrastructure.kafka.health`, com um `AdminClient` único
  (`infrastructure.kafka.config`) reaproveitado a cada checagem. Exposição:
  `show-components: always` (status de cada componente) e
  `show-details: when-authorized` (detalhes como bucket e id do cluster ficam
  escondidos até ter autenticação, na fatia 4).
- **Configuração:** `application.yml` importa o `.env` da raiz
  (`spring.config.import: optional:file:.env[.properties]`), o mesmo que o
  compose lê, com padrão igual ao do compose; `./gradlew bootRun` funciona
  com ou sem `.env`.
- **Demo (T502–T504):** roteiro em `docs/demo.http`. Pra D2 ter documento
  na fila de verdade, a aplicação sobe com o consumidor desligado
  (`spring.kafka.listener.auto-startup=false`, propriedade do Boot, sem
  código), recebe o upload e cai; ao subir normal, o consumidor do grupo
  `indexing` continua do offset confirmado. Logo depois da subida, o
  consumidor leva alguns segundos pra receber as partições (entrada no
  grupo), e o documento fica `QUEUED` nesse intervalo.

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
- Indexação (T401–T403): `DocumentIndexingIntegrationTest` monta cada
  cenário registrando o documento e publicando o evento à mão (objeto
  ausente, hash diferente, documento já `INDEXED` ou parado em
  `PROCESSING`); "sem retentativa" é provado pelo tempo (`FAILED` em menos
  de 4 s, contra mais de 7 s com os retries); "ignorado" com
  `await().during(3 s)`. A8 (`KafkaOutageIntegrationTest`) pausa o container
  do Kafka de verdade (`docker pause`) durante o upload e despausa no fim.
  Como o consumidor roda em todos os testes de integração, eles esperam o
  estado final em vez de conferir `QUEUED`.
- `@IntegrationTest` sobe o servidor numa porta aleatória
  (`RANDOM_PORT`): o limite de upload só aparece passando pelo Tomcat de
  verdade, que o MockMvc não usa. Os `@WebMvcTest` cobrem o mapeamento de
  cada caso pra HTTP com os serviços como dublê.
- Fixtures em código: `DocumentFixtures.documentIn(status)` monta um
  `Document` de verdade (não um mock) no status pedido, e
  `IngestionFixtures.uploadedEvent()` um evento completo. Entidade mockada é
  frágil: getter não configurado devolve `null` sem avisar.
- `support/TopicReader` lê um tópico do começo com um grupo novo e devolve
  as mensagens de uma chave; depois de achar o esperado, continua lendo 2 s
  pra pegar mensagem a mais (é assim que o A3 prova "um evento só").
- Agente do Mockito carregado com `-javaagent` na task `test` (configuração
  `mockitoAgent` no `build.gradle`), em vez de ele se anexar sozinho em tempo
  de execução, o que o Java avisa que vai bloquear.
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
- **RAM:** o heap do daemon do Gradle já é limitado no `gradle.properties`
  (`-Xmx768m`); os testes de integração rodam com o `docker compose`
  parado.
