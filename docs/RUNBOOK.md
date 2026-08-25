# Runbook

O que fazer quando alguma coisa acontece. Escrito para ser lido às três da
manhã, com o mínimo de prosa.

## Painel de saúde

| Endereço | O que responde |
|---|---|
| `/actuator/health` | estado geral |
| `/actuator/health/readiness` | pronto para receber tráfego |
| `/actuator/health/liveness` | processo vivo |
| `/actuator/metrics` | métricas, exige papel de plataforma |
| `/actuator/prometheus` | coleta, exige papel de plataforma |

Métricas próprias que valem acompanhar:

- `mirante_direitos_titulos_bloqueados_total`
- `mirante_direitos_titulos_liberados_total`

Salto brusco no primeiro significa contrato vencendo em lote. Não é defeito do
sistema, é aviso de que alguém precisa renovar.

## Rotinas automáticas

| Rotina | Quando | O que faz |
|---|---|---|
| Varredura de direitos | 30s após subir, depois de hora em hora | bloqueia e libera título e canal conforme a licença |
| Encerrar assinaturas | 03:15 (Brasília) | encerra o que venceu e não voltou |
| Limpar sessões | a cada 2 minutos | fecha sessão sem sinal de vida |
| Limpar tokens | 03:40 (Brasília) | apaga refresh token vencido |

Todas engolem exceção e registram no log. Falha de rotina não derruba a
aplicação, mas aparece como `ERROR` com o nome da rotina.

---

## "Um título sumiu do catálogo"

Provavelmente não sumiu: foi bloqueado por direito.

1. Painel, aba Catálogo. Procure o título
2. Situação `bloqueado por direito` mostra o motivo ao lado
3. Motivos possíveis e o que fazer:

| Motivo | O que fazer |
|---|---|
| Janela de licenciamento vencida | renovar o contrato e cadastrar nova licença |
| Licença rescindida | licença rescindida não volta. Cadastre uma nova |
| Licença sem comprovação anexada | anexar a comprovação |
| Janela ainda não começou | esperar a data de início |
| Licença vinculada não encontrada | vincular uma licença e publicar de novo |

Depois de acertar a licença, o título volta sozinho na próxima varredura, em até
uma hora. Para não esperar, chame `POST /api/v1/plataforma/revisao-de-direitos`
com token de plataforma.

## "Recebemos uma notificação sobre um título"

1. Painel, Licenças. Localize a licença do título
2. Rescinda com o motivo. **Tudo** que dependia dela sai do ar na mesma hora
3. Exporte a auditoria do período:
   `GET /api/v1/painel/auditoria?de=...&ate=...`
4. A trilha mostra qual contrato autorizou, quem publicou e quando

Se a notificação for sobre um item e não sobre o contrato inteiro, despublique só
aquele título e mantenha a licença para os demais.

## "O assinante diz que não consegue assistir"

Peça o código de erro que apareceu na tela. Ele é a resposta.

| Código | Significa | O que dizer |
|---|---|---|
| `SEM_ASSINATURA` | conta sem assinatura | mandar para os planos |
| `ASSINATURA_SEM_ACESSO` | venceu e não voltou | verificar o pagamento |
| `LIMITE_DE_TELAS` | telas do plano ocupadas | fechar outro aparelho ou subir de plano |
| `LIMITE_DE_DISPOSITIVOS` | aparelhos registrados no limite | remover um em Minha conta |
| `FORA_DO_TERRITORIO` | licença não cobre o país | esperado fora do Brasil |
| `DISPOSITIVO_NAO_LICENCIADO` | contrato não cobre aquele tipo de aparelho | verificar a licença |
| `BLOQUEADO_PELO_CONTROLE_PARENTAL` | perfil com teto abaixo da classificação | trocar de perfil |
| `TITULO_FORA_DO_AR` | bloqueado por direito | ver o procedimento acima |
| `VIDEO_INDISPONIVEL` | arquivo não está no armazenamento | conferir a referência do vídeo |

Se o assinante jura que fechou tudo e ainda dá `LIMITE_DE_TELAS`, a sessão antiga
morre sozinha em 2 minutos sem sinal de vida.

## "Um pagamento entrou e a assinatura não liberou"

1. Confira se o webhook chegou: log com `PAGAMENTO_CONFIRMADO` ou
   `WEBHOOK_NAO_AUTENTICO`
2. `WEBHOOK_NAO_AUTENTICO` significa segredo diferente entre o painel do gateway
   e a variável `COBRANCA_WEBHOOK_SEGREDO`. Acerte e peça reenvio pelo gateway
3. Resposta "assinatura não encontrada" significa que a cobrança é de outro
   ambiente apontando para a mesma URL. Comum quando sandbox e produção
   compartilham endereço
4. Se o webhook não chegou, reenvie pelo painel do gateway. O processamento é
   idempotente: reenviar não dá mês de graça

## "A API está lenta ou fora"

No plano gratuito do Render, a instância dorme depois de 15 minutos sem acesso, e
a primeira requisição leva de 30 a 60 segundos. Isso não é defeito; é o plano.

Se não for isso:

1. `/actuator/health` diz qual componente caiu
2. Banco fora: confira o painel do provedor e o limite de conexões. O
   `DB_POOL_MAX` deve ficar bem abaixo do limite do plano
3. Memória: a instância tem 512 MB e a JVM está limitada a 70% disso. Log com
   `OutOfMemoryError` pede plano maior, não ajuste de parâmetro

## Expurgo de auditoria

A tabela cresce e é o que mais consome espaço no banco gratuito. Retenção
sugerida: 12 meses.

```sql
delete from auditoria where ocorrido_em < now() - interval '12 months';
```

Sessões de reprodução, 6 meses:

```sql
delete from sessoes_reproducao
where fechada_em is not null and fechada_em < now() - interval '6 months';
```

Rode fora do horário de pico e confira o espaço antes e depois.

## Abrir um cliente novo

Com token de `ADMIN_PLATAFORMA`:

```
POST /api/v1/plataforma/clientes
{
  "slug": "cinesertao",
  "nome": "Cine Sertao Ltda",
  "documento": "00000000000000",
  "dominioProprio": "assista.cinesertao.com.br",
  "nomeExibido": "Cine Sertao",
  "corPrimaria": "#c04a2b",
  "corDeFundo": "#12100e",
  "emailDoDono": "dono@cinesertao.com.br",
  "nomeDoDono": "Nome do dono",
  "senhaDoDono": "uma-senha-longa"
}
```

O cliente nasce em implantação, com 14 dias de teste. Para liberar de vez:
`POST /api/v1/plataforma/clientes/{id}/producao`.

Suspender por inadimplência: `POST /api/v1/plataforma/clientes/{id}/suspensao`.
O painel continua abrindo; o espectador não entra.

## Incidente com dado pessoal

1. Contenha primeiro: revogue chaves, derrube sessões, feche o acesso
2. Levante o alcance pela trilha de auditoria: quais tenants, quais contas,
   quais dados
3. Avise o controlador, que é o cliente dono do serviço, com o que se sabe
4. A comunicação à ANPD e aos titulares é do controlador. O operador fornece os
   dados técnicos
5. Registre a linha do tempo enquanto ela está fresca
