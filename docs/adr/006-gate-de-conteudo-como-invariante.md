# ADR 006: O gate de conteúdo é invariante de domínio, não checagem de tela

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

O que separa este produto de um serviço pirata é uma frase: nada vai ao ar sem
direito de distribuição. Frase em contrato não vale nada se o sistema permite o
contrário.

Havia três lugares possíveis para essa regra: no formulário do painel, no
controller, ou dentro do domínio.

## Decisão

A regra mora no domínio, como invariante da entidade.

`Titulo.publicar(Licenca, Instant)` é a única porta para o status `PUBLICADO`.
Não existe `setStatus` público, não existe atalho por papel e o
`ADMIN_PLATAFORMA` passa pelo mesmo caminho.

Em volta disso:

- **Varredura horária** que bloqueia o que perdeu licença e devolve ao ar o que
  foi renovado
- **Rescisão com efeito imediato**, na mesma transação
- **Nova conferência a cada play**, porque o direito pode ter caído desde a
  publicação
- **Status separado** `BLOQUEADO_POR_DIREITO`, distinto de `DESPUBLICADO`
- **Trilha de auditoria** em publicação, bloqueio, cadastro e rescisão

## Consequências positivas

- Não existe caminho no código que publique sem licença. Não é disciplina de
  equipe; é o compilador e o tipo
- A regra é testável sem banco e sem HTTP, e está coberta por teste que descreve
  cada recusa
- Vira argumento de venda concreto: dá para demonstrar ao vivo uma licença
  vencendo e o título saindo do ar
- Numa notificação extrajudicial, a trilha responde qual contrato autorizou o
  quê, quem publicou e quando

## Consequências negativas

- Publicar dá mais trabalho: é preciso cadastrar licença e anexar comprovação
  antes. Para quem só quer subir um vídeo, é fricção
- A varredura roda sobre todos os clientes de hora em hora. Com muitos clientes
  vai precisar de paginação e de execução por lote
- O sistema não valida se o contrato anexado é verdadeiro. Ele registra e
  bloqueia por vencimento; conferir o documento continua sendo trabalho humano

## Alternativas consideradas

**Validar no controller.** Funciona até alguém criar um segundo caminho de
publicação, um importador em lote ou um script de migração. Aí a regra fica para
trás sem ninguém notar.

**Só avisar, sem bloquear.** Foi considerada por ser mais amigável. Descartada:
um aviso que dá para ignorar não é gate, e o risco aqui não é de usabilidade.
