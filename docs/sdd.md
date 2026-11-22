# Como o projeto é organizado

O projeto segue *Spec-Driven Development*: cada entrega nasce de uma
especificação escrita, e as decisões técnicas ficam registradas perto do
código.

```
visão (docs/visao.md) + arquitetura (docs/arquitetura.md)
  -> spec (o quê e por quê) -> plan (como) -> tasks (ordem) -> código + testes
                                     \-> ADR (decisões técnicas)
                                                           \-> CHANGELOG (o que mudou)
```

| Onde | O que tem |
| --- | --- |
| `docs/visao.md` | Problema, escopo e o que fica de fora. |
| `docs/arquitetura.md` | Componentes, fluxos, ciclo de vida do documento, evento Kafka. |
| `docs/roadmap.md` | Fatias de entrega e backlog. |
| `docs/adr/` | Uma decisão técnica por arquivo: contexto, decisão, alternativas, consequências. |
| `specs/NNN-slug/spec.md` | Escopo de uma fatia: requisitos e critérios de aceite. |
| `specs/NNN-slug/plan.md` | Como a fatia é implementada: pacotes, contratos, schema, tópicos. |
| `specs/NNN-slug/tasks.md` | Checklist ordenado (`[ ]` pendente, `[~]` em andamento, `[x]` concluída). |
| `CHANGELOG.md` | Mudanças por versão. |

## Critérios de aceite

Cada spec separa dois tipos:

- **A (automático):** vira teste. Unitário pra regra pura; integração com
  Testcontainers (Postgres, Kafka e storage S3 de verdade) pra fluxo.
- **D (demo):** roteiro manual reproduzível (`docs/demo.http` ou `curl`) com o
  `docker compose` de pé.

## Versionamento

- O arquivo `VERSION` é a fonte da versão; o Gradle lê dele.
- Cada fatia entregue é um MINOR (fatia 1 = 0.1.0...). `1.0.0` = fatias 1 a 5.
- Schema só muda por migration Flyway nova.

## Regras do código

- Código em inglês; documentação em português.
- Integração externa (S3, Kafka, provedor de IA) fica atrás de uma interface;
  a regra de negócio não importa SDK de fornecedor.
- Provedores de IA têm implementação fake determinística; nenhum teste chama
  serviço pago e a aplicação sobe sem chave.
- O Kafka leva referência ao arquivo, nunca o arquivo.
- Sobe com `docker compose up -d` + `./gradlew bootRun`.
- Versões de dependência fixas.
- Nenhum segredo no repositório (`.env` fora do git, `.env.example` dentro).
- O corpus de demonstração é fictício ou público.
