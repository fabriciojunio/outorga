# 009 — Migração de esquema sem derrubar a versão anterior

Data: 2026-09-02
Situação: aceita

## Contexto

Publicar uma versão nova sem tirar o sistema do ar significa, por alguns segundos, ter as duas
versões do código rodando contra o mesmo banco. O Flyway aplica a migração quando a primeira
instância nova sobe, e a instância antiga continua atendendo requisição durante a troca.

Se essa migração apaga uma coluna que a versão antiga ainda lê, a janela vira erro para quem
estiver usando o sistema naquele instante. O mesmo vale para renomear coluna e para trocar o tipo
dela.

Aqui o estrago tem um endereço certo, e é o que torna este documento diferente do conselho
genérico. A decisão de deixar alguém apertar o play passa por `PoliticaDeReproducao`, que lê
licença, assinatura, dispositivo e sessão em toda requisição de reprodução. Uma coluna que some
no meio de uma implantação não devolve "erro ao carregar" para o assinante: devolve conteúdo
negado a quem pagou. Errar para o lado de negar é o pior jeito de errar num produto cujo valor é
justamente autorizar.

É um problema que não aparece em desenvolvimento, onde só existe uma versão do código.

## Decisão

Mudança de esquema incompatível acontece em três implantações separadas, e não em uma.

**Expandir.** A coluna nova entra ao lado da antiga, aceitando nulo. O código passa a escrever
nas duas e a ler ainda da antiga. Nada quebra, porque nada foi tirado.

**Migrar.** Os dados antigos são copiados para a coluna nova, e o código passa a ler da nova. A
antiga continua lá, ainda sendo escrita, o que é o que torna possível voltar atrás sem perder o
que entrou no meio do caminho.

**Contrair.** Só depois de a versão anterior não existir mais em ambiente nenhum, a coluna antiga
sai. É aqui que o comando destrutivo entra, e é o único lugar onde ele é seguro.

## O que garante isso

Processo escrito em documento é esquecido. O que segura é uma regra no build.

`MigracaoSemQuebraTest` lê todos os arquivos de migração do repositório e reprova comando
destrutivo: apagar coluna, renomear coluna, trocar o tipo e apertar para não nulo. Os quatro
quebram a versão anterior enquanto ela ainda roda.

O teste não impede o terceiro passo, que é legítimo. Ele exige que quem o escreve declare no
próprio arquivo:

```sql
-- contrair: a versão 1.4 saiu de todos os ambientes em 02/09/2026
alter table licencas drop column territorio;
```

A marca precisa de um motivo na mesma linha. Sem essa exigência ela viraria um comentário colado
para o build passar, o que é pior que não ter regra: dá a impressão de proteção sem proteger.

Detalhe de implementação que quase passou: o padrão que reconhece a marca, se usar o atalho de
espaço em branco, atravessa a quebra de linha. Com isso uma marca vazia engoliria o próprio
comando abaixo dela como se fosse a justificativa, e liberaria tudo. O teste que fecha essa porta
é o `marcaSemMotivoNaoLibera`.

## Por que a numeração aqui é simples, e onde ela não seria

O sistema tem um banco só e uma sequência de migrações só, então o número seguinte é sempre o
maior mais um, e o Flyway valida em ordem sem surpresa.

Vale registrar o caso oposto porque ele é comum e custa caro: quando alguém organiza as
migrações em faixas por assunto — 1.x para uma área, 2.x para outra —, o Flyway continua
validando em ordem numérica global e recusa uma migração cuja versão seja menor que a última
aplicada. A faixa parece dar independência e, no primeiro deploy depois de a faixa mais alta ter
rodado, derruba o ambiente. Se um dia este esquema for repartido, a decisão de ligar
`spring.flyway.out-of-order` precisa vir junto, e não depois.

## O que isto não cobre

Não cobre migração de dados demorada. Copiar milhões de linhas dentro do Flyway trava a subida da
aplicação e a plataforma mata a instância pela sonda de vida. Nesse caso o passo dois precisa ser
um trabalho em lotes, fora da migração, e não há nada aqui que force isso.

Não cobre a decisão de quando a versão anterior realmente saiu de circulação. Isso é operação, e
depende de olhar o que está rodando, não o repositório.

E não cobre índice criado sem `concurrently`, que trava escrita na tabela enquanto é construído.
Distinguir tabela grande de tabela pequena não dá para fazer lendo o arquivo.
