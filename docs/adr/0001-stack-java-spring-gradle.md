# ADR 0001 — Java 25, Spring Boot 4.1 e Gradle

- **Status:** aceito
- **Data:** 2026-11-22

## Contexto

Backend Java com Spring é o centro do projeto. A stack precisa ser atual, mas
estável, e o build precisa ser reproduzível em qualquer máquina.

## Decisão

- **Java 25** (LTS atual), distribuição Eclipse Temurin.
- **Spring Boot 4.1.x**; Spring AI entra na fatia 2.
- **Gradle com Groovy DSL** (`build.gradle`), wrapper versionado com checksum
  da distribuição, *toolchain* Java 25.
- Versões de BOM pelo plugin `io.spring.dependency-management` (`mavenBom`);
  o BOM do Spring Boot é importado pelo próprio plugin do Boot.
- **Flyway** pra migrations, **Testcontainers** pra testes de integração.
- Sem Lombok: `record` cobre DTOs e eventos.

## Alternativas consideradas

- Maven: igualmente válido; Gradle dá build incremental mais rápido.
- Kotlin DSL: autocomplete melhor na IDE, mas Groovy é o formato mais comum
  nos projetos Spring existentes.
- Java 21: o mais usado hoje nas empresas; o código seria o mesmo, e o 25 já
  é LTS.
- Spring Boot 3.5: mais material de exemplo, mas a linha 4 é a atual.

## Consequências

- O *toolchain* garante que o build usa o JDK 25, não o que estiver no PATH.
- Spring Boot 4 tem starters modulares (inclusive de teste) e Jackson 3; os
  nomes conferidos estão no plan da spec 001.
