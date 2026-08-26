# Mirante

*[Read this in English](README.en.md)*

Plataforma de streaming white-label. O cliente traz o catálogo e o direito de
exibição; o Mirante entrega a tecnologia com a marca dele.

A peça central não é o player: é o **gate de conteúdo**. Nenhum título vai ao ar
sem uma licença vigente vinculada, e quando a licença vence o sistema tira do ar
sozinho, sem depender de alguém lembrar.

## Por que existe

Montar um agregador de streaming com catálogo próprio esbarra numa parede que
não é técnica: licenciamento. Comprar direito de exibição de filme e série custa
caro, leva meses e exige estrutura jurídica.

Existe muita gente que já tem conteúdo e já tem o direito sobre ele: produtora
regional com acervo parado, escola com aulas gravadas, academia com treinos,
igreja com culto, canal de TV do interior, curso online que hoje mora no
YouTube. O que essa gente não tem é plataforma.

O Mirante é para essa gente. E o gate de conteúdo é o que separa o produto de um
serviço pirata: sem contrato cadastrado, não existe exibição.

## O que já funciona

- Multi-tenant: um servidor atende vários clientes, cada um com marca, catálogo,
  planos e domínio próprios
- Catálogo VOD com filmes, séries, temporadas e episódios
- Canais ao vivo com grade de programação
- **Licenças** com titular, contrato, território, janela, dispositivos
  autorizados e comprovação anexada
- **Varredura horária de direitos**: bloqueia o que perdeu licença e devolve ao
  ar o que foi renovado
- Assinatura com plano, período de teste, cupom, carência por inadimplência e
  cancelamento com acesso até o fim do ciclo pago
- Cobrança por PIX, boleto e cartão, com webhook idempotente
- Reprodução com token curto, controle de telas simultâneas, limite de
  aparelhos, controle parental por classificação indicativa e bloqueio por
  território
- Contas com perfis, PIN e RBAC por papel
- Trilha de auditoria em todas as ações que importam
- LGPD: exportação e exclusão dos dados do titular
- Painel do operador e painel da plataforma

## Como rodar

Precisa de Java 21, Maven e Node 22. Docker é opcional.

```bash
git clone <repositorio> mirante
cd mirante

# banco local
docker compose up -d banco

# backend
cd backend
mvn spring-boot:run
```

Em outro terminal:

```bash
cd web
cp .env.example .env.local
npm install
npm run dev
```

A vitrine abre em `http://localhost:3000` e a API em `http://localhost:8080`.

Sem Docker, aponte `DATABASE_URL` para qualquer PostgreSQL 14 ou mais novo. O
Flyway cria o esquema na primeira subida.

### Contas da demonstração

Com `MIRANTE_MODO=DEMONSTRACAO` e o banco vazio, a aplicação cria sozinha um
cliente de exemplo:

| Quem | E-mail | O que enxerga |
|---|---|---|
| Espectador | `espectador@exemplo.com` | catálogo, player, minha conta |
| Dono do serviço | `dono@cineserra.com.br` | painel de catálogo, licenças e planos |
| Operação da plataforma | `plataforma@mirante.app` | abertura e suspensão de clientes |

Senha de todas: `demonstracao2026`.

A carga só roda com o banco vazio. Se já existe cliente cadastrado, ela sai
calada, e nunca apaga nada.

## Modo demonstração

Sem chave de fornecedor configurada, o sistema roda inteiro por conta própria:

- vídeo: devolve um HLS público de teste, mantendo toda a cadeia de decisão
- cobrança: abre um checkout simulado que dispara o mesmo webhook que o gateway
  real dispararia

Em `MIRANTE_MODO=PRODUCAO` sem fornecedor configurado, a aplicação **recusa
subir**. É deliberado: subir em produção com cobrança simulada significaria dar
assinatura de graça para quem clicasse, e o erro só apareceria na conciliação do
fim do mês.

## Stack

| Camada | Escolha |
|---|---|
| Backend | Java 21, Spring Boot 3.5 |
| Persistência | PostgreSQL com SQL explícito (JdbcClient) e Flyway |
| Web e painel | Next.js 16, React 19, TypeScript |
| Player | HLS via hls.js |
| Vídeo | Object storage compatível com S3, URL assinada de vida curta |
| Cobrança | Asaas (PIX, boleto, cartão, assinatura recorrente) |
| Observabilidade | Actuator, Micrometer, Prometheus |

As decisões e o motivo de cada uma estão em [docs/adr](docs/adr).

## Arquitetura

```
domain/         regra de negócio pura, sem Spring, sem banco, sem HTTP
application/    casos de uso e portas
infrastructure/ persistência, segurança, fornecedores externos, rotinas
api/            controllers finos que traduzem Result em HTTP
```

A regra de dependência é estrita: `domain` não importa nada das camadas de fora.
Casos de uso são classes comuns, instanciadas à mão em
[ComposicaoDaAplicacao](backend/src/main/java/br/com/mirante/infrastructure/config/ComposicaoDaAplicacao.java).
O efeito prático é que qualquer caso de uso roda em teste com dublês, sem subir
contexto.

O coração do produto está em dois arquivos que vale a pena ler primeiro:

- [Titulo.publicar](backend/src/main/java/br/com/mirante/domain/catalog/Titulo.java) —
  única porta para o ar, e ela exige licença vigente
- [PoliticaDeReproducao](backend/src/main/java/br/com/mirante/domain/playback/PoliticaDeReproducao.java) —
  a decisão de deixar alguém dar play, com um motivo distinto para cada recusa

## Testes

```bash
cd backend && mvn verify
cd web && npm run typecheck && npm run build
```

260 testes. Os de persistência e o de ponta a ponta sobem um PostgreSQL de
verdade pelo próprio teste, sem precisar de Docker. O build falha se a cobertura
de linhas do domínio cair abaixo de 80%.

## Documentação

- [Como publicar de graça](docs/DEPLOY.md)
- [Gate de conteúdo e licenciamento](docs/LICENCIAMENTO.md)
- [Operação do dia a dia](docs/RUNBOOK.md)
- [Segurança](SECURITY.md)
- [LGPD](LGPD.md)
- [Decisões de arquitetura](docs/adr)

Com a aplicação de pé, a referência da API fica em `/swagger-ui.html`.

## Aviso

Este repositório entrega tecnologia, não acervo. O Mirante não obtém, não
extrai e não redistribui conteúdo de terceiros. Cada título e cada canal só vão
ao ar com o direito de distribuição documentado no próprio sistema, e a
responsabilidade por esse direito é de quem cadastra.
