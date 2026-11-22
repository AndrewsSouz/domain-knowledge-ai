# ADR 0003 — PostgreSQL com pgvector para metadados e vetores

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

Precisamos guardar metadados e ciclo de vida dos documentos (transacional) e
os embeddings dos trechos (busca por similaridade). O desenho v0 tem
"VectorDB" separado do PostgreSQL.

## Decisão

Um servidor PostgreSQL com a extensão **pgvector**: tabela de documentos e
tabela de trechos com coluna `vector` no mesmo banco. O `VectorDbIntegration`
continua existindo como interface, pra permitir trocar o banco vetorial depois.

## Alternativas consideradas

- Banco vetorial dedicado (Qdrant, Weaviate, Pinecone): melhor em milhões de
  vetores, mas é mais um container (RAM) e perde a transação conjunta.
- Elasticsearch/OpenSearch: dá busca híbrida, mas é pesado pra essa máquina.

## Consequências

- Gravar trechos e mudar o status pra `INDEXED` numa transação só: ou tudo, ou
  nada. Responde "e se falhar no meio?".
- Filtrar por permissão na busca vira um `WHERE` comum junto da similaridade.
- A imagem do compose já é `pgvector/pgvector` desde a fatia 1, pra fatia 2
  não mexer na infra.
- Limite: em volume muito grande, trocar a implementação do
  `VectorDbIntegration`.
