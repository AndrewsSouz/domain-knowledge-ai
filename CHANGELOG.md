# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versão em `VERSION`. Organização do projeto em `docs/sdd.md`.

## [Unreleased]

### Adicionado

- Documentação: visão, arquitetura, roadmap e ADRs 0001 a 0006.
- Spec 001 (fatia 1, ingestão) com plan e tasks.
- Projeto Gradle (Groovy DSL, wrapper 9.8.2), Java 25, Spring Boot 4.1.2;
  versão do projeto lida do arquivo `VERSION`.
- `docker-compose.yml` com PostgreSQL 18 + pgvector, Kafka 4.3 (KRaft) e
  RustFS, com healthcheck e limite de memória.
- Configuração da aplicação para banco e Kafka, lendo o mesmo `.env` do
  compose.
- README com visão geral e como rodar.
- Base de testes de integração com Testcontainers (PostgreSQL + pgvector,
  Kafka e RustFS) e teste de fumaça da infraestrutura.
- Tabela `document` (migration V1, com `unique` no SHA-256 e `check` nos
  status), entidade `Document` e `DocumentRepository`.
- Lombok (getters e builders), com a versão do BOM do Spring Boot.
