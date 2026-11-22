---
id: "001"
slug: ingestao
titulo: "Fatia 1 — Ingestão funcional"
versao: "1.0.0"
status: aprovada
criada_em: 2026-11-22
revisada_em: 2026-11-22
implementacao: "—"
---

> Escopo da fatia 1 do `docs/roadmap.md`. Alterações de escopo sobem a versão
> deste arquivo e entram no `CHANGELOG.md`. Arquitetura em
> `docs/arquitetura.md`; decisões em `docs/adr/`.

# Fatia 1 — Ingestão funcional

## 1. Objetivo

Provar o **esqueleto assíncrono ponta a ponta**: um documento sobe pela API,
fica guardado no storage S3 local (RustFS), registrado no Postgres, vira um
evento no Kafka, e um consumidor pega esse evento e leva o documento até o
fim do ciclo de vida.

Nesta fatia o "indexador" é um **stub**: baixa o arquivo do storage, confere o
SHA-256 e marca `INDEXED`. A fatia 2 troca o stub pela indexação de verdade
sem mexer no resto. (Na engenharia isso se chama *walking skeleton*: o
esqueleto inteiro andando antes de ter músculo.)

## 2. Fora do escopo

Extração de texto, chunking, embeddings, consulta, autenticação, permissão,
outbox, DLQ, nova versão de documento, interface web.

## 3. Requisitos funcionais

**RF1 — Upload.** `POST /v1/documents`, `multipart/form-data`, campos `file`
(obrigatório) e `title` (opcional; padrão = nome do arquivo).
- Tipos aceitos: **PDF e DOCX**, detectados pelo conteúdo, não só pela
  extensão.
- Tamanho máximo: 20 MB (ajustável).
- Resposta `202 Accepted` com o `id` do documento, `version`, `status` e o link pro
  `GET`.
- Tipo não aceito → `415`; arquivo vazio ou grande demais → `400`/`413`;
  erros no formato *Problem Details* (RFC 9457, padrão do Spring).

**RF2 — Duplicado.** Se já existe documento com o mesmo SHA-256:
- em `QUEUED`, `PROCESSING` ou `INDEXED` → responde `200` com o documento
  existente e **não** publica evento novo;
- em `RECEIVED` (a publicação falhou) ou `FAILED` → reenfileira o mesmo
  documento (volta pra `RECEIVED`, publica, `QUEUED`) e responde `202`.
  Reenviar o arquivo é a forma de tentar de novo nesta fatia.

**RF3 — Consulta de um documento.** `GET /v1/documents/{id}` → metadados,
`status`, `errorMessage` (quando `FAILED`), `createdAt`, `updatedAt`.
Id inexistente → `404`.

**RF4 — Listagem.** `GET /v1/documents?status=&page=&size=` paginada, mais
recentes primeiro.

**RF5 — Processamento.** O consumidor recebe `DocumentUploadedEvent`
(`docs/arquitetura.md` §5), marca `PROCESSING`, baixa o original, confere o
SHA-256 e marca `INDEXED`.

**RF6 — Idempotência.** Evento de documento que já está `INDEXED` ou
`FAILED` é ignorado (com log), sem erro. Evento de documento em
`PROCESSING` **é processado de novo**: é o caso da aplicação que caiu no meio
e recebeu a mensagem de volta do Kafka. Isso é seguro porque a chave da
mensagem é o `documentId` (eventos do mesmo documento chegam em ordem, um de
cada vez) e o processamento pode ser repetido sem efeito duplicado.

**RF7 — Falha no processamento.** Arquivo sumido no storage ou hash
diferente → `FAILED` com `errorMessage`, sem retentar. Erro inesperado: até 3
tentativas com intervalo crescente, depois `FAILED`. A mensagem não fica
travando o tópico.

**RF8 — Falha na publicação.** Se o Kafka não aceitar o evento no upload, o
documento fica `RECEIVED` e a API responde `503` (Problem Details com o
`documentId`). Reenviar o mesmo arquivo reenfileira (RF2). A correção
definitiva é o outbox da fatia 4.

## 4. Requisitos não funcionais

- **RNF1** `docker compose up -d` sobe Postgres (imagem com pgvector),
  Kafka (KRaft, um nó) e RustFS, storage compatível com S3 (ADR 0005; o
  bucket é criado pela aplicação). Uso de RAM do compose abaixo de ~1,5 GB.
- **RNF2** Schema só por Flyway.
- **RNF3** Logs com `documentId` em todas as linhas do fluxo (MDC), dos dois
  lados do Kafka.
- **RNF4** `/actuator/health` mostrando banco, Kafka e storage.
- **RNF5** `./gradlew build` roda unitários e integração (Testcontainers) sem
  rede externa e sem chave.
- **RNF6** `.env.example` com todas as variáveis; nenhum segredo versionado.

## 5. Modelo de dados (rascunho; o plan fecha)

Tabela `document`: `id` (UUID), `title`, `original_filename`, `content_type`,
`size_bytes`, `sha256` (único), `storage_key`, `version` (sempre 1 nesta
fatia), `status`, `error_message`, `uploaded_by` (nulo até a fatia 4),
`created_at`, `updated_at`.

## 6. Critérios de aceite

Automáticos (viram teste):

- **A1** Upload de PDF válido → `202`, linha `QUEUED` no banco, objeto no
  storage com o mesmo SHA-256, evento no tópico com a chave = `documentId`.
- **A2** Fluxo completo com Testcontainers: upload → em até 10 s o `GET`
  mostra `INDEXED`.
- **A3** Mesmo arquivo duas vezes → segundo upload `200` com o mesmo
  `documentId`, só um evento publicado.
- **A4** Tipo não aceito → `415`; acima do limite → `413`; vazio → `400`.
- **A5** Evento de documento já `INDEXED` → ignorado; evento de documento
  parado em `PROCESSING` → processado até `INDEXED` (RF6).
- **A6** Objeto removido do storage antes do consumo → `FAILED` com
  mensagem, sem retentativa.
- **A7** `GET` de id inexistente → `404` em Problem Details.
- **A8** Kafka fora do ar no upload → `503`, documento `RECEIVED`; com o
  Kafka de volta, reenviar o arquivo → `202` e o fluxo termina em `INDEXED`.
- **A9** Documento `FAILED` reenviado → volta pra fila e termina `INDEXED`.

Demo (roteiro manual):

- **D1** Do zero: `docker compose up -d`, `./gradlew bootRun`, upload de um
  PDF (as Comprehensive Rules, `docs/visao.md`) pelo `docs/demo.http`, ver `QUEUED` virar `INDEXED` no `GET` e o
  arquivo no console do RustFS.
- **D2** Derrubar a aplicação com documento na fila, subir de novo e ver o
  documento terminar (o Kafka guardou a mensagem).

## 7. Decisões

1. **Endpoint `POST /v1/documents`.** Salva no storage (RustFS), registra no
   Postgres e publica no Kafka só a referência (`storageKey`), não o arquivo.
2. **PDF e DOCX.** TXT e Markdown ficam no backlog.
3. **Duplicado por SHA-256 devolve o existente.** Alternativas descartadas:
   aceitar tudo como novo (duplica trechos na busca, e a resposta citaria o
   mesmo trecho duas vezes, além de pagar embedding em dobro) e recusar com
   `409 Conflict` (obriga o cliente a tratar erro pra algo que não é erro).
4. **Gradle** com Groovy DSL.
5. **Java 25.** ADR 0001.
