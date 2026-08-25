# ADR 003: SQL explícito com JdbcClient em vez de ORM

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

O domínio usa construtores privados, fábricas que devolvem `Result`, objetos de
valor (`Dinheiro`, `Email`, `Territorio`, `JanelaDeLicenca`) e coleções
encapsuladas. Nada disso combina com o que o JPA espera: construtor sem
argumentos, campos mutáveis e getters e setters.

Além disso, o sistema é multi-tenant, e o risco número um de multi-tenancy é
consulta que esquece o filtro por cliente e vaza dado de um para outro.

## Decisão

Persistência com **SQL explícito via JdbcClient**, mais Flyway para migração.
Sem JPA, sem Hibernate.

Cada agregado tem um adaptador que traduz linha em entidade pelo método
`reconstituir`. **Toda consulta recebe o tenant como parâmetro obrigatório na
assinatura do método.**

## Consequências positivas

- O domínio fica livre de anotação de persistência
- O filtro por tenant é cobrado pela assinatura do método. Com `@TenantId` do
  Hibernate, o filtro depende de configuração e de um contexto de thread; um
  esquecimento ali vaza dado de cliente sem avisar
- Não existe consulta N+1 acidental: quem escreve o SQL enxerga quantas idas ao
  banco vai fazer. O carregamento de temporadas e episódios faz duas consultas
  para qualquer quantidade de títulos, e isso está no código, à vista
- Nada de sessão, cache de primeiro nível, `LazyInitializationException` ou
  descoberta tardia de que o `flush` aconteceu na hora errada
- Recurso de PostgreSQL entra sem ginástica: `text[]`, `jsonb`, índice parcial,
  `insert ... on conflict`

## Consequências negativas

- Mais linhas de SQL escritas à mão, e um `insert` por agregado
- Migração para outro banco exigiria reescrever consultas. Aceito: o projeto
  assume PostgreSQL
- Sem verificação de tipo entre coluna e campo em tempo de compilação. É o que o
  teste de persistência contra PostgreSQL de verdade cobre
- Arranjo e `jsonb` viram literal com cast explícito no SQL, e o escape mora em
  `Colunas`

## Alternativas consideradas

**Spring Data JPA com `@TenantId`.** Foi a primeira opção, e chegou a entrar no
`pom.xml`. Saiu por causa da fricção com o modelo rico e pelo filtro de tenant
depender de configuração em vez de assinatura.

**jOOQ.** Resolveria o tipo em tempo de compilação, mas exige geração de código
a partir de um banco vivo no build, o que complica CI, e a licença comercial
pesa para bancos não abertos.
