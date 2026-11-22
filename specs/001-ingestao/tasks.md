# Tasks — Fatia 1, ingestão (spec 001)

`[ ]` pendente, `[~]` em andamento, `[x]` concluída (com o hash do commit),
`[-]` cancelada. Task com critério A fecha quando o teste passa no
`./gradlew build`; task com critério D fecha quando o roteiro manual é
executado e confirmado.

## T0 — Fundação

Fim do bloco: projeto compila, infraestrutura sobe, teste de integração
vazio passa com os três containers.

- [x] T001 (d3f271a) `git init`, `.gitignore`, `.editorconfig`, `.env.example`.
  Projeto Gradle (Groovy DSL, wrapper 9.8.2 com checksum, toolchain Java 25)
  com as versões do plan §1. Conferir os nomes dos starters do Boot 4.1.
  `./gradlew build` verde.
- [x] T002 (d3af157) Seção "Comandos" do `CLAUDE.md` e `README.md` inicial (o que é,
  status, como rodar).
- [x] T003 (d3af157) `docker-compose.yml` (Postgres com pgvector, Kafka KRaft, RustFS)
  com healthcheck e limite de memória. Conferir as variáveis do RustFS 1.0.4.
  **D:** `docker compose up -d` deixa os três `healthy` e o
  `docker stats` abaixo de 1,5 GB. *Confirmado em 22/11/2026: três
  `healthy`, ≈ 435 MB, console do storage acessado.*
- [x] T004 (d3af157) Suporte de teste de integração (`support/`): containers
  compartilhados, `@ServiceConnection` (Postgres, Kafka), RustFS por
  `DynamicPropertyRegistrar`. Teste de subida do contexto verde.

## T1 — Documento e ciclo de vida

- [x] T101 (a8f49e0) Migration `V1__create_document.sql`, entidade `Document`, enum
  `DocumentStatus`, `DocumentRepository`.
- [x] T102 (3f091d0) `DocumentLifecycle` com as transições do plan §5 (updates
  condicionais). Teste de integração de cada transição válida e inválida.

## T2 — Storage

- [x] T201 (4c63e99) `ObjectStorage`, `S3Integration`, `StorageProperties`,
  `BucketInitializer`, `StorageObjectNotFoundException`. Teste contra o
  RustFS: gravar, ler, objeto inexistente.
- [x] T202 (476b583) `StorageHealthIndicator`.

## T3 — Upload

Fim do bloco: upload grava no storage, no banco e publica no Kafka.

- [ ] T301 Fixtures (`sample.pdf`, `sample.docx`, `fake.pdf`, `empty.pdf`)
  com texto próprio. `ContentTypeDetector` (Tika) + teste
  unitário.
- [ ] T302 `DocumentUploadedEvent`, tópico `document-uploaded` (`NewTopic`),
  `IngestionProducer` com espera do ack. Teste: evento no tópico com chave =
  `documentId`.
- [ ] T303 `DocumentUploadService` com o fluxo do plan §6 (duplicado,
  reenfileirar, falha na publicação) e o `UploadResult` selado. **A1, A3.**
- [ ] T304 `DocumentController` (`POST`, `GET` por id, `GET` paginado) e
  `ApiExceptionHandler` com Problem Details. **A4, A7.**

## T4 — Consumo

Fim do bloco: o fluxo completo termina em `INDEXED`.

- [ ] T401 `IngestionConsumer`, `DocumentIndexingService`,
  `DocumentIndexer` + `ChecksumOnlyIndexer`. Deserializer à prova de mensagem
  malformada. **A2.**
- [ ] T402 Error handler: backoff 1/2/4 s, exceções não retentáveis,
  recoverer marcando `FAILED`. **A6.**
- [ ] T403 Idempotência e recuperação. **A5, A8, A9.**

## T5 — Operação, demo e fechamento

- [ ] T501 `documentId` no MDC dos dois lados, padrão de log,
  `KafkaHealthIndicator`, exposição do `/actuator/health`. (RNF3, RNF4)
- [ ] T502 `docs/demo.http` e a seção "Como rodar" do README.
- [ ] T503 **D1:** subir do zero, fazer upload pelo `demo.http`, ver
  `QUEUED` → `INDEXED` e o arquivo no console do RustFS.
- [ ] T504 **D2:** derrubar a aplicação com documento na fila, subir
  de novo e ver o documento terminar.
- [ ] T505 Revisão de coerência (spec, plan, tasks, roadmap, estado,
  CHANGELOG), `VERSION` e Gradle em 0.1.0, spec com `implementacao: "0.1.0"`.
  Tag `v0.1.0`.
