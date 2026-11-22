# ADR 0004 — Kafka para indexação assíncrona, evento por referência

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

Extrair texto e gerar embeddings leva de segundos a minutos e depende de
serviço externo que pode falhar ou limitar. O upload não pode esperar isso.

## Decisão

- O upload grava o original no storage, registra o documento e publica um
  evento no Kafka; responde `202 Accepted`.
- O evento leva **referência** (`documentId`, `version`, `storageKey`,
  `sha256`), nunca o conteúdo do arquivo.
- Chave da mensagem = `documentId`.

## Alternativas consideradas

- `@Async`/fila em memória: perde trabalho se a aplicação cair.
- RabbitMQ: serviria, mas o Kafka é padrão em backend Java e permite
  reprocessar relendo o tópico.
- Arquivo dentro da mensagem: estoura o limite de mensagem (1 MB padrão) e
  duplica o armazenamento.

## Consequências

- Precisa tratar entrega duplicada (idempotência) e dual write (outbox,
  fatia 4).
- Mais um container no compose (Kafka KRaft, um nó, heap limitado).
