# ADR 005: Entrega de vídeo terceirizada atrás de uma porta

**Data:** 2026-08-24
**Status:** Aceito

## Contexto

O blueprint previa um pipeline de vídeo próprio: ingest, transcodificação,
empacotamento, DRM, CDN. Cada uma dessas etapas é um produto inteiro. Construir
tudo isso adiaria o lançamento em meses e traria um custo de saída de dados que
inviabiliza qualquer teste sem cliente pagante.

## Decisão

A entrega de vídeo fica atrás da porta `EntregaDeVideo`. O domínio guarda apenas
uma **referência** do ativo; virar endereço assinado é trabalho da
infraestrutura.

Três implementações:

- `EntregaEmObjectStorage`: URL pré-assinada em bucket compatível com S3, com a
  assinatura SigV4 escrita à mão em vez de trazer o SDK da AWS. A escolha de
  partida é o Cloudflare R2, porque não cobra saída de dados
- `EntregaDeDemonstracao`: devolve um HLS público de teste. Toda a cadeia de
  decisão continua valendo; só muda o arquivo que toca no fim
- qualquer outra: trocar a classe que implementa a interface

Transcodificação **não** faz parte da plataforma. O cliente envia o material já
empacotado em HLS, seguindo a convenção de chave documentada no
[DEPLOY](../DEPLOY.md).

## Consequências positivas

- O produto sai do papel sem pipeline de vídeo
- Custo de saída de dados zerado no R2, que é o item que estoura orçamento em
  streaming
- URL de vida curta: link copiado do inspetor do navegador vira lixo em cinco
  minutos
- O limite de qualidade do plano vale de verdade, porque o servidor assina a
  renditura daquele teto em vez do manifesto mestre
- SigV4 à mão evita dezenas de megabytes de SDK numa instância de 512 MB

## Consequências negativas

- O cliente precisa entregar o vídeo já transcodificado, o que exige orientação
  na implantação
- Sem DRM. URL assinada de vida curta segura o compartilhamento casual, não um
  ataque dedicado. Para catálogo premium seria preciso Widevine ou FairPlay, e
  isso é fase de escala
- Assinatura escrita à mão é código de segurança de manutenção própria

## Alternativas consideradas

**Bunny Stream.** Resolve transcodificação, player e token de autenticação, com
armazenamento a US$ 0,01 por GB e entrega a US$ 0,005 por GB. É barato e é a
troca mais provável quando o volume crescer, mas não tem camada gratuita, e a
exigência do momento é custo zero.

**Cloudflare Stream.** Bom produto, cobrança por minuto armazenado e por minuto
assistido. Sem camada gratuita.

**Pipeline próprio com FFmpeg.** Custo de operação e de banda alto demais para o
estágio, e reinventaria o que já existe pronto.
