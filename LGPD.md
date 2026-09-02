# LGPD

Como o Outorga TV trata dado pessoal, e o que quem contrata a plataforma precisa
fazer do lado dele.

## Papéis

Na relação com o assinante final, **quem contrata o Outorga TV é o controlador** e
o **Outorga TV é operador**. Quem decide o que coletar, por quanto tempo guardar e
para que usar é o dono do serviço; a plataforma trata os dados por conta dele.

Isso precisa estar escrito no contrato entre as partes, com cláusula de
tratamento de dados. A minuta está em `comercial/`.

## O que é coletado

| Dado | Para quê | Base legal |
|---|---|---|
| Nome e e-mail | identificar a conta e permitir o login | execução de contrato |
| Senha (hash) | autenticar | execução de contrato |
| Documento (CPF ou CNPJ) | emitir a cobrança | obrigação legal |
| Perfis e classificação | controle parental | execução de contrato |
| Aparelhos registrados | limite do plano e segurança | execução de contrato |
| Histórico de reprodução | continuar assistindo e limite de telas | execução de contrato |
| Endereço IP nos registros | auditoria e segurança | legítimo interesse |
| Eventos de assinatura | prova do que aconteceu na cobrança | obrigação legal |

O que **não** é coletado: dado de cartão (fica no gateway), localização precisa,
contato da agenda, identificador de publicidade.

## Minimização

- O endereço IP é guardado só na trilha de auditoria, não em cada requisição
- Log de aplicação registra e-mail **mascarado** (`fa***@exemplo.com`), nunca o
  endereço completo
- A trilha de auditoria nunca guarda senha, token ou corpo de requisição

## Direitos do titular

Implementados e acessíveis na própria conta, sem abrir chamado:

**Acesso e portabilidade.** `GET /api/v1/me/meus-dados` devolve, em JSON, tudo
que a plataforma guarda: nome, e-mail, datas, perfis, aparelhos e situação da
assinatura. Na web, o botão "Baixar meus dados" em Minha conta.

**Exclusão.** `DELETE /api/v1/me/minha-conta`.

A exclusão é **anonimização, não `DELETE`**, e isso é uma decisão consciente:

- perfis e aparelhos são apagados de verdade
- o registro do usuário permanece com nome e e-mail substituídos por marcador,
  senha inutilizada e conta travada
- assinaturas, pagamentos e trilha de auditoria continuam, agora sem dono

O motivo é que registro de pagamento e trilha de auditoria têm obrigação legal
de guarda própria, e apagar a linha inteira quebraria a conciliação financeira
do cliente. O que se apaga é o que identifica a pessoa; o que fica é o fato
contábil.

A exclusão exige que a assinatura esteja cancelada. Pedir exclusão com cobrança
ativa geraria cobrança de um titular que não existe mais.

**Correção.** Nome e senha pela própria conta. Troca de e-mail passa pelo
suporte do cliente.

**Revogação de sessão.** A exclusão derruba todos os refresh tokens na hora.

## Retenção

| Dado | Prazo |
|---|---|
| Conta ativa | enquanto durar a relação |
| Conta anonimizada | permanece sem identificação |
| Trilha de auditoria | 12 meses, com expurgo manual documentado no RUNBOOK |
| Sessões de reprodução | 90 meses não é necessário; o expurgo sugerido é 6 meses |
| Registro de cobrança | 5 anos, por obrigação fiscal |

O expurgo ainda é manual. Automatizar está na lista de melhorias.

## Incidente

Em caso de incidente com dado pessoal, a LGPD prevê comunicação à ANPD e aos
titulares em prazo razoável. O procedimento operacional está no
[RUNBOOK](docs/RUNBOOK.md), e a comunicação é responsabilidade do controlador,
com o operador fornecendo o que for necessário.

## Encarregado

Quem contrata o Outorga TV precisa indicar um encarregado de dados e publicar o
contato na política de privacidade do próprio serviço. A plataforma não faz isso
pelo cliente.

## O que falta

- Expurgo automático por prazo de retenção
- Registro de consentimento para comunicação de marketing, que hoje não existe
  porque a plataforma não envia marketing
- Relatório de impacto, que é responsabilidade do controlador
