# Roadmap

Cada fatia termina em algo que roda ponta a ponta e vira um MINOR. Testes,
`docker compose`, ADRs e README andam junto desde a fatia 1.

| Fatia | Spec | Versão | Entrega | Status |
| --- | --- | --- | --- | --- |
| 1. Ingestão | 001 | 0.1.0 | Upload → storage S3 → Postgres → Kafka → consumidor muda status. O indexador ainda é um stub que baixa o arquivo e confere o hash. | em andamento |
| 2. Indexação | 002 | 0.2.0 | Extração (Tika), chunking, embeddings, pgvector, `INDEXED`. | — |
| 3. Consulta RAG | 003 | 0.3.0 | Pergunta → trechos → LLM → resposta com fontes. Primeira versão demonstrável, com GIF no README. | — |
| 4. Segurança e resiliência | 004 | 0.4.0 | Autenticação, permissão por documento e nos chunks, outbox, retry/DLQ, backoff pra 429, reprocessar `FAILED`, nova versão de documento. | — |
| 5. Acabamento | 005 | 0.5.0 → 1.0.0 | Métricas e tracing, avaliação da qualidade das respostas, análise de dependências (OWASP) e qualidade (Sonar), README final. | — |

## Decisões por fatia

- **Provedor de IA: Gemini.** Gemini Flash para gerar a resposta (fatia 3) e
  o modelo de embedding do Gemini (fatia 2). Vira ADR no plan da fatia 2, com
  o modelo exato e os limites da cota gratuita.
- **Fatia 2:** `VectorStore` do Spring AI ou tabela própria com JDBC.
- **Fatia 3:** tamanho do top-k, limiar de similaridade.
- **Fatia 4:** como autenticar (JWT simples ou Keycloak no compose).

## Backlog

- Endpoint só de busca (`POST /v1/search`): devolve os trechos com o score,
  pra outro sistema montar o próprio prompt com o próprio LLM.
- Busca híbrida (texto + vetor) e reranking.
- OCR pra PDF escaneado.
- Página web simples pra upload e pergunta.
- Resposta em streaming (SSE).
- Cache de embedding de pergunta repetida.
- Deploy em nuvem com custo zero.
- TXT e Markdown como tipos aceitos.
