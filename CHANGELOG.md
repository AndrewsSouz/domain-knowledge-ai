# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versão em `VERSION`. Organização do projeto em `docs/sdd.md`.

## [Unreleased]

## [0.1.0] - 2026-11-23

Fatia 1: ingestão ponta a ponta (spec 001), com indexador stub.

### Adicionado

- Documentação: visão, arquitetura, roadmap e ADRs 0001 a 0006.
- Spec 001 (fatia 1, ingestão) com plan e tasks.
- Projeto Gradle (Groovy DSL, wrapper 9.8.2), Java 25, Spring Boot 4.1.2 e
  Lombok; versão do projeto lida do arquivo `VERSION`.
- `docker-compose.yml` com PostgreSQL 18 + pgvector, Kafka 4.3 (KRaft) e
  RustFS, com healthcheck e limite de memória.
- Configuração da aplicação para banco e Kafka, lendo o mesmo `.env` do
  compose.
- README com visão geral e como rodar.
- Base de testes de integração com Testcontainers (PostgreSQL + pgvector,
  Kafka e RustFS) e teste de fumaça da infraestrutura.
- Tabela `document` (migration V1, com `unique` no SHA-256 e `check` nos
  status), entidade `Document` e `DocumentRepository`.
- `DocumentLifecycle`: registro do documento e transições de status por
  `UPDATE` condicional, seguras sob concorrência.
- Storage S3 (`ObjectStorage`, implementação `S3Integration`), com criação
  do bucket na subida.
- Storage no `/actuator/health` (`StorageHealthIndicator`).
- Detecção do tipo de arquivo pelo conteúdo (PDF e DOCX, com Tika).
- Evento `DocumentUploadedEvent` no tópico `document-uploaded` (3
  partições, chave = `documentId`), publicado com confirmação do Kafka.
- Upload (`DocumentUploadService`): tipo pelo conteúdo, SHA-256 em stream,
  duplicado devolve o existente ou reenfileira (`RECEIVED`/`FAILED`), grava
  no storage, registra e publica.
- API `POST /v1/documents` (202 novo ou reenfileirado, 200 duplicado),
  `GET /v1/documents/{id}` e `GET /v1/documents` paginado com filtro por
  status; erros em Problem Details (400, 404, 413, 415, 500, 503).
- Consumidor do `document-uploaded`: assume o documento, confere o SHA-256
  do original no storage e marca `INDEXED` (indexador stub da fatia 1).
- Falha na indexação: até 3 novas tentativas (1 s, 2 s, 4 s) pra erro
  inesperado; arquivo sumido, hash diferente ou documento inexistente vão
  direto pra `FAILED` com o motivo; mensagem malformada é descartada sem
  travar a partição.
- `documentId` em todas as linhas de log do fluxo, dos dois lados do Kafka
  (MDC); Kafka no `/actuator/health`, com o status de cada componente.
- Roteiro da demo (`docs/demo.http`, REST Client do VS Code) e seção "Demo"
  no README, com o download do corpus.
