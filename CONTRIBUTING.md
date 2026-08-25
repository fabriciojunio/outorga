# Contribuindo

## Antes de escrever código

Leia [docs/adr](docs/adr). São oito documentos curtos que explicam por que o
projeto é do jeito que é. Mudança que contraria um ADR precisa de um ADR novo
que o substitua, não de um comentário no pull request.

## Regras que não se negociam

**A regra de dependência.** `domain` não importa nada de `application`,
`infrastructure` ou `api`. Nem Spring, nem JDBC, nem Jakarta. Se você precisou
de um import desses no domínio, a regra está no lugar errado.

**Regra de negócio mora no domínio.** Controller traduz. Caso de uso busca,
orquestra e grava. Nenhum `if` de negócio em controller.

**Nada de `Instant.now()` em regra.** O relógio é injetado. Teste que depende da
data de hoje passa hoje e quebra na virada do mês.

**Dinheiro em centavos.** `Dinheiro`, nunca `double`, nunca `float`.

**Consulta recebe o tenant.** Todo método de repositório que devolve dado de
cliente recebe `tenantId` como parâmetro. Não é opcional e não é redundante.

**Publicação passa por `Titulo.publicar`.** Não crie caminho paralelo. É o gate
de conteúdo, e ele é o produto.

## Testes

Rode antes de abrir pull request:

```bash
cd backend && mvn verify
cd web && npm run typecheck && npm run build
```

O build falha se a cobertura de linhas de qualquer pacote do domínio cair abaixo
de 80%.

Como escrever teste aqui:

- **Nome descreve comportamento**, em português, no `@DisplayName`. "recusa
  publicar com licenca vencida", não "testPublicar2"
- **Um comportamento por teste.** Se o nome precisa de "e", provavelmente são dois
- **Dublê que guarda estado**, não mock com expectativa. Os dublês estão em
  `Dubles.java` e são implementações de verdade. Teste que verifica chamada
  envelhece quando a implementação muda; teste que verifica comportamento não
- **Relógio fixo.** `Clock.fixed`, sempre
- **Cenário compartilhado** em `CenarioDeTeste`, para o teste falar do que
  importa

## Estilo

- Código, comentário, nome de classe, log e mensagem de erro em **português**,
  com acentuação correta em tudo que o usuário lê. O código-fonte fica sem
  acento para evitar problema de codificação entre sistemas
- Linha até 110 colunas
- Comentário explica **por quê**, não o quê. `// incrementa o contador` não
  ajuda ninguém; `// cartao recusado por limite volta a passar em dois dias`
  ajuda quem for mexer nisso daqui a um ano
- Nada de abreviação inventada. `licenca`, não `lic`
- Sem `System.out.println`. Use o logger

## Commits

- Mensagem em português, no imperativo: "adiciona carencia na inadimplencia"
- **Nunca use travessão** na mensagem
- Um assunto por commit
- Corpo explicando o motivo quando a mudança não é óbvia

## Migração de banco

- Arquivo novo com número seguinte em `backend/src/main/resources/db/migration`
- **Nunca edite migração já aplicada.** O Flyway guarda o checksum e recusa
- Toda coluna de dado de cliente carrega `tenant_id`
- Índice de consulta começa por `tenant_id`
- Instante em `timestamptz`, dinheiro em `bigint` de centavos
- Comente o índice que não é óbvio, dizendo qual consulta ele atende
