# ADR 001: Plataforma white-label em vez de catálogo próprio

**Data:** 2026-08-24
**Status:** Aceito
**Decisores:** Fabrício Júnio Almeida Dias

## Contexto

O ponto de partida era um blueprint de agregador de streaming "tudo em um", com
filmes, séries, canais ao vivo e esportes, no modelo de serviços que circulam
com esse tipo de oferta.

O próprio blueprint identifica o risco central: o maior obstáculo não é o
aplicativo, é o licenciamento do catálogo. Comprar direito de exibição de obra
audiovisual custa caro, leva meses de negociação e exige estrutura jurídica que
um desenvolvedor sozinho não tem. Sem isso, o produto ou não sai do papel ou sai
ilegal.

Ao mesmo tempo, existe um público que já tem conteúdo e já tem o direito sobre
ele, e não tem plataforma: produtora regional com acervo parado, escola com
aulas gravadas, academia com treinos, igreja com culto, canal de TV do interior,
curso online hospedado no YouTube sem controle de acesso nem cobrança.

## Decisão

O Mirante é uma **plataforma white-label multi-tenant**. Quem contrata traz o
catálogo e declara o direito de distribuição; a plataforma entrega tecnologia,
marca, cobrança e controle de acesso.

Duas consequências entram no código, não só no discurso:

1. O sistema **não vem com catálogo**. O acervo da demonstração é ficção.
2. A publicação exige uma licença cadastrada e vigente. Ver
   [ADR 006](006-gate-de-conteudo-como-invariante.md).

## Consequências positivas

- Some o gargalo que impedia o produto de existir. O direito é de quem cadastra
- O cliente paga assinatura mensal, que é receita recorrente e previsível
- O mesmo servidor atende muitos clientes, então o custo marginal por cliente
  novo é quase zero
- A conversa comercial fica honesta: nada de "temos 60 mil títulos"
- O gate de conteúdo vira diferencial de venda em vez de restrição

## Consequências negativas

- O mercado é menor e a venda é B2B, com ciclo mais longo que venda direta
- Depende de o cliente ter conteúdo pronto e organizado, o que nem sempre é
  verdade
- Não dá para competir com Netflix ou YouCine em catálogo, e nem é a intenção
- Multi-tenancy encarece a implementação desde o primeiro dia

## Alternativas consideradas

**Catálogo próprio licenciado.** Correto e inviável para o estágio atual: capital
para adiantamento de licença e estrutura jurídica que não existem.

**Agregador de conteúdo de terceiros sem autorização.** Descartado. Não é
questão de risco calculado; é o que a IN 174 da ANCINE trata como oferta não
autorizada.

**Só conteúdo em domínio público.** O acervo é pequeno e o público que se
interessa é menor ainda. Serve como demonstração, não como produto.
