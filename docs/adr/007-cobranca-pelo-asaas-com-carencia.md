# ADR 007: Cobrança pelo Asaas, com carência na inadimplência

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

Assinatura no Brasil precisa de PIX. Cartão sozinho perde uma fatia grande do
público, e boleto ainda aparece. Precisava também de assinatura recorrente com
webhook, e de custo fixo zero enquanto não houver cliente.

## Decisão

**Asaas** como implementação de partida, atrás da porta `GatewayDePagamento`.

O critério que decidiu foi comercial antes de técnico: conta sem mensalidade e
sem taxa de adesão, cobrando só por transação recebida. Enquanto o produto não
vende, o gateway não cobra.

Decisões que acompanham:

- **Nenhum dado de cartão passa pelo Outorga TV.** O que sai é um pedido de
  cobrança; o que volta é uma URL de fatura e uma referência
- **`billingType: UNDEFINED`**, deixando o pagador escolher PIX, boleto ou cartão
  na própria fatura. Fixar a forma só reduz conversão
- **A assinatura nasce antes da cobrança.** Se o gateway cair no meio, sobra um
  registro inadimplente que o webhook conserta quando o pagamento entrar. O
  contrário seria pagamento no gateway sem contraparte aqui, e isso só aparece
  na conciliação do fim do mês
- **Webhook idempotente por natureza:** confirmar duas vezes estende o ciclo a
  partir do fim vigente, não a partir de hoje. Reentrega de webhook não vira mês
  de graça
- **Autenticidade conferida antes de ler o corpo**, com comparação em tempo
  constante
- **Carência de três dias** depois de uma cobrança falhar

## Sobre a carência

Cartão recusado por limite volta a passar em dois dias na maioria das vezes.
Derrubar o acesso no mesmo minuto gera cancelamento que não precisava acontecer,
e às vezes chargeback. Três dias é o intervalo em que a recuperação acontece sem
que o assinante sinta o corte.

O mesmo raciocínio vale para o cancelamento: quem cancela mantém acesso até o
fim do ciclo já pago. Cobrar o mês e cortar no dia 3 rende reclamação pública.

## Consequências positivas

- Custo fixo zero até a primeira venda
- PIX, boleto e cartão sem escrever integração para cada um
- Menos exposição: dado de cartão não entra no sistema
- Trocar de gateway é implementar uma interface de quatro métodos

## Consequências negativas

- Taxa por transação é maior que a de gateway grande com contrato de volume
- A API do Asaas exige duas chamadas para obter a URL da fatura, e ela pode
  ainda não existir no instante seguinte à criação da assinatura
- Sem PIX automático, o débito recorrente do Banco Central. É melhoria mapeada

## Alternativas consideradas

**Stripe.** Melhor documentação e melhor sandbox do mercado. Suporte a PIX no
Brasil é mais limitado e a taxa é maior.

**Mercado Pago.** Alcance enorme e PIX nativo. A documentação tem lacunas em
assinatura recorrente e o sandbox é menos previsível.

**Pagar.me.** Boa API. Perde no ponto que decidiu: estrutura de custo pensada
para volume, não para quem ainda não vendeu.
