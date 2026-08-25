# ADR 002: Java 21 e Spring Boot com Clean Architecture

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

A escolha de stack precisava atender a três coisas ao mesmo tempo: a regra de
negócio do produto é densa e cheia de invariantes; o sistema lida com dinheiro e
com direito de terceiros, onde erro silencioso é caro; e o projeto serve de
peça de portfólio no eixo de back-end Java e Spring.

## Decisão

Java 21 com Spring Boot 3.5, organizado em Clean Architecture com regra de
dependência estrita.

```
domain/         regra pura. Zero import de Spring, de banco ou de HTTP
application/    casos de uso e portas
infrastructure/ adaptadores: persistência, segurança, fornecedores, rotinas
api/            controllers finos
```

Decisões que acompanham:

- **Casos de uso são classes comuns**, sem anotação do Spring. O wiring é
  manual, em `ComposicaoDaAplicacao`
- **Entidades com comportamento**, não modelos anêmicos. `Titulo` sabe se pode
  ir ao ar; `Assinatura` sabe se dá acesso hoje
- **Result em vez de exceção** para condição esperada. Ver
  [ADR 004](004-result-em-vez-de-excecao.md)
- **Relógio injetado** (`java.time.Clock`). Nada de `Instant.now()` dentro da
  regra
- Recursos de linguagem que ajudam: `record`, `sealed interface`, pattern
  matching em `switch`

## Consequências positivas

- A regra de negócio é testável sem subir contexto e sem banco. Os 200 e poucos
  testes de domínio e caso de uso rodam em menos de dois segundos
- Trocar fornecedor de vídeo ou de cobrança é trocar uma classe atrás de uma
  interface
- O wiring manual num arquivo só deixa visível quando um caso de uso está
  acumulando dependência demais
- `switch` sobre enum sem `default` transforma "esqueci de tratar um caso novo"
  em erro de compilação

## Consequências negativas

- Mais arquivos e mais cerimônia que um CRUD direto com Spring Data
- `reconstituir` em cada entidade é repetitivo, e esquecer um campo ali é um bug
  silencioso. Foi exatamente o que aconteceu com `motivoDaSuspensao` e o que o
  teste de persistência pegou
- Wiring manual precisa de manutenção quando um caso de uso ganha dependência

## Alternativas consideradas

**Spring Data JPA com entidades anotadas.** Menos código, mas as anotações
puxariam a persistência para dentro do domínio, e o modelo rico com construtor
privado e objetos de valor brigaria com o Hibernate. Ver
[ADR 003](003-sql-explicito-em-vez-de-orm.md).

**Node com TypeScript.** Seria mais rápido de escrever, e o autor tem mais
repositórios nessa stack. Perde nos dois pontos que decidiram: garantia em tempo
de compilação para um domínio cheio de estado, e o eixo de carreira.
