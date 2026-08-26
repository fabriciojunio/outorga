# Mirante

*[Versão em português](README.md)*

A white-label streaming platform. The customer brings the catalogue and the
distribution rights; Mirante supplies the technology under their brand.

The centrepiece is not the player. It is the **content gate**: no title reaches
the air without a current licence attached to it, and when a licence expires the
system takes the title down on its own, without anyone having to remember.

## Why it exists

Building a streaming aggregator with your own catalogue runs into a wall that is
not technical: licensing. Buying distribution rights to films and series is
expensive, takes months, and needs a legal structure behind it.

But plenty of people already own content and already hold the rights to it: a
regional producer with a dormant archive, a school with recorded classes, a gym
with workouts, a church with services, a small-town TV channel, an online course
currently living on YouTube. What those people do not have is a platform.

Mirante is for them, and the content gate is what separates the product from a
piracy service: no contract on file, no exhibition.

## The interesting part

Two files carry the whole idea, and they are the ones worth opening first.

[`Titulo.publicar`](backend/src/main/java/br/com/mirante/domain/catalog/Titulo.java)
is the only path to `PUBLICADO`, and it takes the licence as a parameter rather
than looking it up. That is deliberate: the caller is forced to hold the licence,
so no code path exists that can publish without one. The compiler enforces the
business rule.

[`PoliticaDeReproducao`](backend/src/main/java/br/com/mirante/domain/playback/PoliticaDeReproducao.java)
decides whether someone may press play. Eight checks in sequence — subscription,
territory, device, concurrent screens, parental rating, rights block, licence
window, title status — each with its own distinct refusal code, because "you
cannot watch this" is useless to a support agent.

An hourly sweep blocks whatever lost its licence and puts back whatever was
renewed. Both directions are tested, and the second one exists because a channel
taken down by hand once came back on air by itself.

## Architecture

```
domain/         business rules, no Spring, no database, no HTTP
application/    use cases and ports
infrastructure/ persistence, security, external providers, scheduled work
api/            thin controllers that translate Result into HTTP
```

The dependency rule is strict and checked by test: `domain` imports nothing from
the outer layers. Use cases are ordinary classes wired by hand in
[`ComposicaoDaAplicacao`](backend/src/main/java/br/com/mirante/infrastructure/config/ComposicaoDaAplicacao.java),
so any use case runs in a test with stubs, without starting a context.

Persistence is explicit SQL through `JdbcClient`, not an ORM. The reasoning is
in [ADR 003](docs/adr): the domain model is rich enough to fight Hibernate, and
every repository method takes the tenant in its signature so that forgetting to
filter by tenant is a compile error rather than a data leak.

## Running it

Needs Java 21, Maven and Node 22. Docker is optional.

```bash
cd backend && ./mvnw spring-boot:run
cd web && npm install && npm run dev
```

With no provider keys configured the system runs end to end on its own: video
returns a public HLS test stream while keeping the entire decision chain intact,
and billing opens a simulated checkout that fires the same webhook the real
gateway would fire.

With `MIRANTE_MODO=PRODUCAO` and no provider configured, the application
**refuses to start**. That is deliberate. Booting production with simulated
billing would hand out free subscriptions to anyone who clicked, and the mistake
would only surface at month-end reconciliation.

## Tests

```bash
cd backend && mvn verify
```

260 tests. The persistence and end-to-end tests start a real PostgreSQL from
within the test itself, with no Docker required, because a test that only runs
when someone remembers to start the infrastructure is a test that does not run.
The build fails if line coverage of the domain drops below 80%.

That choice paid for itself. Four defects were caught only because the database
was real: a `reconstituir` method silently dropping the suspension reason, a live
channel coming back on air during the rights sweep, an accent-insensitive search
that could not work, and a licence-expiry test that was passing for the wrong
reason.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.5 |
| Persistence | PostgreSQL with explicit SQL (JdbcClient) and Flyway |
| Web and admin | Next.js 16, React 19, TypeScript |
| Player | HLS via hls.js |
| Video | S3-compatible object storage, short-lived signed URLs |
| Billing | Asaas (PIX, boleto, card, recurring) |
| Observability | Actuator, Micrometer, Prometheus |

Each decision and its trade-off is written down in [docs/adr](docs/adr).

## Notice

This repository ships technology, not a library of content. Mirante does not
obtain, extract or redistribute third-party material. Every title and every
channel reaches the air only with its distribution right documented inside the
system, and responsibility for that right belongs to whoever registers it.
