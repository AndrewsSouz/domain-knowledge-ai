# Visão

## O problema

Em empresa grande, o conhecimento está espalhado em PDFs, manuais e
procedimentos que ninguém acha na hora que precisa. O funcionário pergunta pro
colega, abre chamado ou faz errado.

## O que o sistema faz

1. **Ingestão:** um funcionário sobe um documento. O sistema guarda o
   original, registra os metadados e devolve na hora um id e o status.
2. **Indexação (assíncrona):** um consumidor do Kafka pega o documento,
   extrai o texto, quebra em trechos (*chunks*), gera um *embedding* (vetor
   que representa o significado) de cada trecho e grava num banco vetorial.
3. **Consulta (RAG):** alguém faz uma pergunta. O sistema transforma a
   pergunta em vetor, busca os trechos mais parecidos, monta um prompt só com
   esses trechos e pede a resposta a um LLM. A resposta volta **com as
   fontes** (documento e trecho), e sem fonte o sistema diz que não sabe.
4. **Controle:** cada documento tem dono, status e permissão. Ninguém recebe
   resposta montada com trecho de documento que não pode ler.

## Princípios

- Roda com um comando e tem uma demo curta.
- Decisões escritas em ADRs.
- Backend de verdade: assíncrono, falhas tratadas, testes de integração,
  observabilidade.

## Corpus de demonstração

Uma empresa fictícia (uma rede de farmácias inventada) com 10 a 20 documentos
curtos escritos pra demo: política de férias, procedimento de troca de
produto, manual do PDV, código de conduta.

## Fora de escopo (até a 1.0.0)

- Interface web elaborada (no máximo uma página simples, no backlog).
- Microsserviços separados (é um monólito modular: ADR 0002).
- OCR de documento escaneado, áudio, vídeo.
- Treinar ou ajustar modelo.
- Multi-tenant (várias empresas).

## Perguntas de arquitetura que o projeto responde

| Pergunta | Onde |
| --- | --- |
| Por que Kafka? | ADR 0004, fatia 1 |
| Por que guardar o original num storage S3? | ADR 0005, fatia 1 |
| Por que PostgreSQL? E por que pgvector e não um banco vetorial dedicado? | ADR 0003 |
| O registro no banco e a publicação no Kafka podem ficar inconsistentes? | `arquitetura.md` §6, fatia 4 (outbox) |
| E se o Kafka entregar a mesma mensagem duas vezes? | Idempotência no consumidor, fatias 1 e 2 |
| E se a indexação falhar no meio? | Ciclo de vida + `FAILED` + reprocesso, fatias 1 e 4 |
| E se o provedor de IA devolver 429? | Retry com backoff e limite de concorrência, fatia 4 |
| Como impedir acesso indevido? | Permissão no documento **e** filtro nos chunks na busca, fatia 4 |
| Como saber se a resposta é boa? | Conjunto de perguntas de avaliação, fatia 5 |
| Por que não microsserviços? | ADR 0002 |
