# ADR 0005 — Original num storage S3, RustFS no desenvolvimento

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

O original precisa ser guardado: pra reindexar quando mudar o chunking ou o
modelo de embedding, e pra mostrar a fonte da resposta. No desenvolvimento,
precisa rodar local, sem conta na nuvem.

## Decisão

- Armazenamento de objetos com API S3, via **AWS SDK v2**, atrás da interface
  `ObjectStorage` (implementação `S3Integration`).
- Local e nos testes: **RustFS** (`rustfs/rustfs:1.0.4`), compatível com S3,
  licença Apache 2.0, com console web.
- Chave: `documents/{documentId}/v{version}/original.{ext}`.
- O bucket é criado pela aplicação na subida quando
  `storage.create-bucket=true` (padrão no ambiente local).

## Alternativas consideradas

- MinIO: a edição comunitária deixou de distribuir imagens oficiais.
- Garage: leve, mas exige configurar o layout do cluster depois de subir.
- LocalStack: pesado pra uma máquina de desenvolvimento modesta.
- Adobe S3Mock: maduro, mas é um mock de teste sem console; fica de plano B.
- Arquivo em disco ou blob no PostgreSQL: não representa o cenário real e
  incha o banco.

## Consequências

- Trocar pra AWS S3 real é só configuração (endpoint e credenciais).
- O código só conhece a API S3: trocar o RustFS por outro storage compatível
  é mudar o `docker-compose.yml` e o container de teste.
- Arquivo gravado sem linha no banco (falha entre os dois passos) é lixo
  inofensivo; uma limpeza pode entrar no backlog.
