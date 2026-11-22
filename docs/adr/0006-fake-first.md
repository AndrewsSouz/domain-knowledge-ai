# ADR 0006 — Provedores de IA fake-first

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

Embedding e LLM custam dinheiro e dependem de rede e chave. Os testes precisam
ser determinísticos e o projeto precisa rodar pra quem clonar sem chave nenhuma.

## Decisão

- `EmbeddingIntegration` e `ChatIntegration` são interfaces com uma
  implementação fake determinística (o mesmo texto gera sempre o mesmo vetor)
  e uma real (Spring AI), escolhida por propriedade.
- Sem chave configurada, a aplicação sobe com os fakes e avisa no log.
- Nenhum teste chama provedor real; o teste com provedor real é manual
  (roteiro D).

## Consequências

- Fatias 1 e 2 andam sem gastar nada; o provedor real (Gemini, roadmap)
  entra por configuração.
- O fake não mede qualidade de resposta; isso é a avaliação da fatia 5.
