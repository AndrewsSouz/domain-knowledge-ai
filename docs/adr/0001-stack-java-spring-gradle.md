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
- **Lombok** pra tirar o código repetitivo das classes: `@Getter` nas
  entidades e criação de objetos por **builder** (`@Builder`), inclusive nos
  `record` de DTOs e eventos. Versão gerenciada pelo BOM do Spring Boot.

## Alternativas consideradas

- Maven: igualmente válido; Gradle dá build incremental mais rápido.
- Kotlin DSL: autocomplete melhor na IDE, mas Groovy é o formato mais comum
  nos projetos Spring existentes.
- Java 21: o mais usado hoje nas empresas; o código seria o mesmo, e o 25 já
  é LTS.
- Spring Boot 3.5: mais material de exemplo, mas a linha 4 é a atual.
- Sem Lombok, só `record` e getters escritos à mão: menos uma ferramenta no
  build, mas entidades JPA (que não podem ser `record`) ficam cheias de
  getters, e construtor com muitos parâmetros é fácil de errar a ordem.

## Consequências

- O *toolchain* garante que o build usa o JDK 25, não o que estiver no PATH.
- Builder em vez de construtor: cada campo é nomeado na criação. Na
  entidade, o builder é privado e a criação passa por um método de fábrica
  que garante o estado inicial válido.
- Spring Boot 4 tem starters modulares (inclusive de teste) e Jackson 3; os
  nomes conferidos estão no plan da spec 001.
