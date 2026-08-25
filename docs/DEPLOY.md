# Colocar no ar sem gastar nada

Este roteiro põe o Mirante em produção usando só camada gratuita. Custo mensal:
zero. Leva por volta de 40 minutos na primeira vez.

O que sai de graça e o que não sai está no fim do documento, sem enfeite.

## O que você vai usar

| Peça | Serviço | Limite gratuito |
|---|---|---|
| Banco | Neon | 0,5 GB, sem prazo para expirar |
| API | Render | 512 MB de RAM, dorme após 15 min parada |
| Site | Vercel | 100 GB de banda por mês |
| Vídeo | Cloudflare R2 | 10 GB guardados, saída de dados sem cobrança |
| Cobrança | Asaas | sem mensalidade, cobra só por transação recebida |

Nenhum deles pede cartão de crédito para começar, com a exceção do Asaas, que
pede documento de PJ na abertura da conta.

## 1. Banco (Neon)

1. Crie a conta em neon.tech e um projeto novo
2. Copie a string de conexão
3. Guarde as três partes separadas, que é como a aplicação pede:

```
DATABASE_URL=jdbc:postgresql://ep-xxx.sa-east-1.aws.neon.tech/neondb?sslmode=require
DATABASE_USER=neondb_owner
DATABASE_PASSWORD=xxxxx
```

Note o prefixo `jdbc:` e o `sslmode=require`. O Neon recusa conexão sem TLS, e a
mensagem de erro quando falta esse parâmetro não ajuda em nada.

O Flyway cria todas as tabelas na primeira subida da API. Não rode nada à mão.

## 2. API (Render)

1. Suba o repositório para o GitHub
2. Em render.com, escolha New, Blueprint, e aponte para o repositório
3. O Render lê o [render.yaml](../render.yaml) e monta o serviço sozinho
4. Preencha no painel as três variáveis do banco, que ficaram marcadas como
   `sync: false` de propósito para não irem parar no repositório

O `JWT_SEGREDO` é gerado pelo próprio Render e mantido entre deploys. Não
preencha à mão e não copie de outro ambiente.

A primeira subida leva alguns minutos porque compila o projeto dentro do
contêiner. Quando terminar, confira:

```
https://SEU-SERVICO.onrender.com/actuator/health
```

Deve responder `{"status":"UP"}`. Se responder erro de banco, revise o
`sslmode`.

**Sobre o plano gratuito do Render:** a instância dorme depois de 15 minutos sem
acesso, e a primeira requisição depois disso leva de 30 a 60 segundos. Para
demonstração comercial, abra o link cinco minutos antes da reunião. Não tente
resolver isso com um robô batendo no endereço de tempos em tempos: além de ser
contra os termos, gasta as horas gratuitas do mês.

## 3. Site (Vercel)

1. Em vercel.com, importe o mesmo repositório
2. Em Root Directory, escolha `web`
3. Configure as variáveis:

```
NEXT_PUBLIC_API_URL=https://SEU-SERVICO.onrender.com
NEXT_PUBLIC_SERVICO=cineserra
```

4. Depois do deploy, volte ao Render e ajuste `ORIGENS_PERMITIDAS` para o
   endereço da Vercel. Sem isso o navegador bloqueia toda chamada por CORS, e o
   site fica em branco sem dizer por quê.

## 4. Vídeo (Cloudflare R2), quando sair da demonstração

Enquanto `MIRANTE_MODO=DEMONSTRACAO`, o player toca um arquivo público de teste e
não é preciso configurar nada. Para servir conteúdo de verdade:

1. Crie um bucket no R2
2. Gere um token de API do tipo S3 com permissão de leitura e escrita
3. Configure no Render:

```
MIRANTE_MODO=PRODUCAO
VIDEO_ENDPOINT=https://SEU-ID.r2.cloudflarestorage.com
VIDEO_BUCKET=mirante
VIDEO_CHAVE=...
VIDEO_SEGREDO=...
VIDEO_REGIAO=auto
```

A convenção de chave que o sistema espera é:

```
<referencia>/mestre.m3u8
<referencia>/1080p/playlist.m3u8
<referencia>/720p/playlist.m3u8
<referencia>/480p/playlist.m3u8
```

A `referencia` é o texto gravado no título ou no episódio. Quando o plano do
assinante limita a qualidade, o servidor assina a URL da renditura daquele teto
em vez do mestre. Assim o limite de plano vale de verdade: não adianta o player
pedir 4K se o manifesto entregue não tem 4K dentro.

Transcodificação não faz parte da plataforma. Para poucos títulos, o FFmpeg na
sua máquina resolve:

```bash
ffmpeg -i entrada.mp4 \
  -vf scale=-2:720 -c:v libx264 -b:v 2800k -c:a aac -b:a 128k \
  -hls_time 6 -hls_playlist_type vod \
  -hls_segment_filename '720p/seg_%03d.ts' 720p/playlist.m3u8
```

## 5. Cobrança (Asaas), quando for cobrar de verdade

1. Abra a conta em asaas.com com o CNPJ
2. Comece pelo ambiente de testes: `COBRANCA_URL=https://api-sandbox.asaas.com/v3`
3. Gere a chave de API e configure `COBRANCA_CHAVE`
4. Escolha um texto secreto qualquer, configure em `COBRANCA_WEBHOOK_SEGREDO` e
   cadastre o mesmo texto no painel do Asaas, em Integrações, Webhooks
5. Aponte o webhook para `https://SEU-SERVICO.onrender.com/api/v1/webhooks/pagamento`
6. Marque os eventos `PAYMENT_CONFIRMED`, `PAYMENT_RECEIVED`, `PAYMENT_OVERDUE`,
   `PAYMENT_REFUNDED`

O webhook confere a assinatura antes de ler o corpo. Segredo errado devolve 401
e nada acontece, que é o comportamento certo para um endereço público.

Só troque para `https://api.asaas.com/v3` depois de ver um pagamento de teste
percorrer o caminho inteiro no sandbox.

## Quando o gratuito acaba

Vale saber de antemão, para não descobrir com o cliente na linha:

- **Neon, 0,5 GB.** Só metadado, nunca vídeo. Dá para muitos milhares de
  títulos e assinantes. O que cresce rápido é a tabela de auditoria; o expurgo
  está no [RUNBOOK](RUNBOOK.md).
- **R2, 10 GB.** Cabem por volta de 6 a 10 horas de vídeo em 720p. Acima disso,
  US$ 0,015 por GB por mês. Cem gigabytes custam cerca de US$ 1,50 por mês, e a
  saída de dados continua sem cobrança, que é o ponto do R2.
- **Render, dorme quando fica parado.** Isso é aceitável para demonstração e
  para os primeiros clientes. Para um serviço com público, o plano pago começa
  em US$ 7 por mês e não dorme.
- **Vercel, 100 GB de banda.** O site é leve porque o vídeo não passa por ele.

Traduzindo: dá para operar de graça até o primeiro cliente pagante, e o primeiro
cliente pagante cobre a infraestrutura dos próximos dez.
