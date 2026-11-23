# ADR 0002 — Monólito modular, não microsserviços

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

O desenho tem várias caixas (upload, indexação, consulta). Dá pra ler como
microsserviços, mas o projeto é pequeno e precisa rodar inteiro numa máquina
de desenvolvimento comum.

## Decisão

Uma aplicação Spring Boot, com pacotes por domínio (`document`,
`ingestion`, `indexing`, `query`) e, separado deles, `infrastructure` com um
pacote por sistema externo (`storage`, `kafka`, `ai`). O Kafka já separa o lado
síncrono do assíncrono **dentro** do mesmo processo. Regra: um pacote não
acessa o repositório de outro direto, só pelo serviço público dele.

## Alternativas consideradas

- Dois serviços (API de upload + worker de indexação): mais "bonito" no
  diagrama, mas dobra build, deploy, configuração e memória, sem ganho real
  nesse tamanho.

## Consequências

- O Kafka e os pacotes já deixam o corte pronto: separar o worker de
  indexação vira uma mudança de deploy, não de código.
- Os limites entre pacotes são convenção de código (subpacotes por papel,
  descritos no plan da spec 001), seguida na escrita e conferida na revisão.
