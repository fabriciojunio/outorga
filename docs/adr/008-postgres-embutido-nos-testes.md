# ADR 008: PostgreSQL embutido nos testes, em vez de Testcontainers ou H2

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

O esquema usa recursos que só existem no PostgreSQL: `text[]`, `jsonb`, índice
parcial com `where`, `insert ... on conflict`, e uma função SQL `sem_acento`
usada em índice funcional para busca sem acento.

Testar isso com H2 em modo de compatibilidade daria uma sensação falsa de
segurança: o teste passaria e o primeiro deploy quebraria.

Testcontainers seria a resposta óbvia, mas a máquina de desenvolvimento deste
projeto não tem Docker instalado. Na prática, isso significaria que o único
teste que executa o SQL de verdade só rodaria no CI, e o retorno chegaria minutos
depois de cada mudança em vez de segundos.

## Decisão

`io.zonky.test:embedded-postgres`, que baixa e sobe um PostgreSQL de verdade
como processo, sem Docker.

Duas classes usam:

- `PersistenciaEmPostgresTest`: cada adaptador de repositório, ida e volta
- `AplicacaoTest`: sobe o contexto inteiro do Spring e percorre a API por HTTP

Ambas rodam no ciclo normal de `mvn test`. Não há profile separado nem
`-DskipITs`: teste que só roda quando alguém lembra não protege nada.

## Consequências positivas

- O SQL é executado a cada build, na mesma versão de banco que roda em produção
- Não exige Docker em lugar nenhum, nem na máquina nem no CI
- Já pagou o próprio custo: pegou o `motivoDaSuspensao` que o `Tenant` gravava
  mas não lia de volta, um bug invisível para todo teste de unidade
- `AplicacaoTest` responde a pergunta que nenhum teste de unidade responde: as
  peças, ligadas como serão em produção, funcionam?

## Consequências negativas

- A primeira execução baixa o binário do PostgreSQL, alguns dezenas de
  megabytes. As seguintes usam o cache do Maven
- Sobe cerca de 25 segundos ao tempo total do build
- É preciso declarar o binário de cada sistema operacional que vai rodar o
  build. Hoje são Windows e Linux
- O `truncate` entre testes acopla o teste à lista de tabelas do esquema

## Alternativas consideradas

**Testcontainers.** A escolha padrão, e a certa quando há Docker. Chegou a
entrar no `pom.xml` e saiu por causa do ambiente.

**H2 em modo PostgreSQL.** Rápido e sem download, mas não entende `text[]`,
`jsonb` nem índice parcial. Testaria outra coisa.

**Só testar repositório no CI.** Foi descartada pelo motivo de sempre: o retorno
lento faz o teste ser ignorado, e aí ele para de existir na prática.
