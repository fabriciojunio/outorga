# Gate de conteúdo

Este documento descreve como o Outorga TV impede que algo vá ao ar sem direito de
exibição, e o que ele deliberadamente não faz.

Não é parecer jurídico. É a descrição de um comportamento do sistema.

## O problema

Streaming com catálogo de terceiros esbarra em licenciamento, não em tecnologia.
No Brasil, a ANCINE publicou em 2026 a Instrução Normativa nº 174, que disciplina
representações sobre oferta não autorizada de conteúdo audiovisual protegido em
ambiente digital. Música embutida em vídeo pode envolver direitos próprios, e o
ECAD trata de licença para transmissão musical em serviços digitais.

A conclusão de produto é direta: quem responde pelo direito é quem tem o
conteúdo. O Outorga TV entrega a plataforma; o acervo e o contrato são do cliente.

## Como o sistema cobra isso

### A licença é uma entidade, não um campo de observação

[Licenca](../backend/src/main/java/br/com/outorga/domain/rights/Licenca.java)
guarda titular, referência do contrato, territórios, janela de vigência,
dispositivos autorizados e a comprovação anexada.

Uma licença nasce em `RASCUNHO` e não autoriza nada. Só passa a `VIGENTE` quando
alguém anexa a comprovação, e é isso que a torna utilizável.

### Publicar exige informar a licença

`Titulo.publicar(licenca, agora)` é a **única** porta para o status `PUBLICADO`.
Não há setter público de status, não há atalho de administrador e o próprio
`ADMIN_PLATAFORMA` passa por ela. A publicação é recusada quando:

- não há licença informada
- a licença pertence a outro cliente
- a licença não está vigente na data
- não existe vídeo para reproduzir

### A varredura acerta o que o tempo estragou

[RevisarDireitosVigentes](../backend/src/main/java/br/com/outorga/application/usecases/rights/RevisarDireitosVigentes.java)
roda de hora em hora, e também logo depois de a aplicação subir. Ela compara o
que está no ar com o que tem licença vigente agora e acerta a diferença nos dois
sentidos:

- título no ar sem licença vigente vira `BLOQUEADO_POR_DIREITO`
- título bloqueado cuja licença voltou a valer volta ao ar sozinho

`BLOQUEADO_POR_DIREITO` é um status separado de `DESPUBLICADO` de propósito. O
operador precisa enxergar que foi a licença que caiu, e não alguém que
despublicou. E título despublicado à mão não volta sozinho na varredura, porque
quem tirou tinha um motivo.

### Rescisão tira do ar na hora

Esperar o job noturno significaria manter conteúdo sem direito no ar por horas.
`RescindirLicenca` bloqueia, na mesma transação, todo título e canal que
dependia daquela licença, e devolve quantos foram.

### A reprodução confere de novo

Nada é aceito só porque foi aprovado antes. Em cada pedido de play,
[PoliticaDeReproducao](../backend/src/main/java/br/com/outorga/domain/playback/PoliticaDeReproducao.java)
confere:

1. o serviço do cliente aceita tráfego
2. a assinatura dá acesso
3. o título está no ar
4. a licença está vigente **agora**
5. a licença cobre o território de quem pede
6. a licença cobre aquele tipo de aparelho
7. o conteúdo cabe na classificação do perfil
8. há tela livre no plano

Cada recusa tem código próprio. Não existe "conteúdo indisponível" genérico:
`FORA_DO_TERRITORIO`, `DISPOSITIVO_NAO_LICENCIADO` e `LIMITE_DE_TELAS` são
respostas diferentes porque exigem ações diferentes de quem recebeu.

O token entregue ao player vale cinco minutos. Se o direito cair no meio do
filme, a próxima renovação já não sai.

### Aviso antes do vencimento

O painel mostra o que vence nos próximos 60 dias, ordenado pelo que vence
primeiro, com a contagem de títulos afetados. Sessenta dias porque renovação de
contrato leva semanas para ser assinada, e avisar no dia do vencimento não
serviria de nada.

### Tudo deixa rastro

Publicação, bloqueio por direito, cadastro e rescisão de licença, autorização e
recusa de reprodução: todos vão para a trilha de auditoria com o autor, o
recurso, o endereço de origem e o motivo. Numa notificação extrajudicial, essa
trilha é a resposta.

## O que o sistema não faz, de propósito

- Não obtém, não extrai e não redistribui stream de terceiro
- Não vem com catálogo. O acervo da demonstração é ficção
- Não valida se o contrato anexado é verdadeiro. Ele registra, versiona e
  bloqueia por vencimento; conferir o documento é trabalho humano
- Não substitui advogado

## Fluxo de retirada

Ao receber uma notificação sobre um título:

1. Localize o título no painel
2. Rescinda a licença correspondente com o motivo. Tudo que dependia dela sai do
   ar imediatamente
3. Exporte a trilha de auditoria do período em Painel, Auditoria
4. A trilha mostra qual contrato autorizou a publicação, quem publicou e quando

Se a notificação for sobre um item específico e não sobre o contrato inteiro,
despublique só aquele título e mantenha a licença para os demais.

## Fontes consultadas

- ANCINE, Instrução Normativa nº 174, de 8 de abril de 2026, sobre oferta não
  autorizada de conteúdo audiovisual protegido em ambiente digital
- ANCINE, Instrução Normativa nº 26, sobre contratos de exploração comercial,
  licenciamento e distribuição
- ECAD, Serviços Digitais e Distribuição, sobre direitos musicais em serviços
  digitais
