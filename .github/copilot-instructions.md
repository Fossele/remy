# Copilot instructions

## Project overview

This repository is a Java 21 Spring Boot backend for a neighborhood messaging platform. It provides authenticated REST endpoints and SockJS/STOMP real-time chat, with MySQL persistence through Spring Data JPA. The same neighborhood membership model governs chat access and community project funds.

## Build, run, and test

Use the Maven Wrapper from the repository root:

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw -Dtest=AuthServiceTest test
./mvnw -Dtest=AuthServiceTest#loginRejectsInvalidCredentials test
./mvnw -DskipTests package
```

There is no separate lint or formatter plugin configured in `pom.xml`. Tests use JUnit 5, AssertJ, and Mockito; test packages mirror production packages. `docker compose up --build` starts the local app and MySQL stack.

Local configuration is in `src/main/resources/application.yaml`. The finance integration uses `FAPSHI_BASE_URL`, `FAPSHI_COLLECTION_API_USER`, `FAPSHI_COLLECTION_API_KEY`, `FAPSHI_PAYOUT_API_USER`, `FAPSHI_PAYOUT_API_KEY`, `FAPSHI_WEBHOOK_SECRET`, `FAPSHI_WEBHOOK_URL`, and `FAPSHI_REDIRECT_URL`. Fapshi defaults to its sandbox URL; credentials and webhook secrets must remain outside source control.

## Architecture

- `controller/` contains REST and STOMP entry points. REST controllers are under `/api`; `MessageController` handles `/app` destinations and broadcasts to `/topic`.
- `service/` owns business rules and authorization. `NeighborhoodService` enforces neighborhood membership and moderation rules; `CommunityFinanceService` owns fund, contribution, ledger, and withdrawal transitions.
- `repository/` contains Spring Data JPA interfaces; `entity/` contains UUID-based persisted models and enums; `dto/` contains validated request records and response mappers.
- `config/` wires stateless JWT security, STOMP broker configuration and authentication, and scheduled reconciliation.
- JWT subjects are user UUIDs. The STOMP CONNECT interceptor authenticates the bearer token and message handlers derive the actor from `Principal`, not client-provided sender identifiers.
- `payment/PaymentProvider` and `PayoutProvider` are provider boundaries. `FapshiApiClient` implements collection and payout operations; contribution and payout services handle initiation, webhook verification, and reconciliation.
- Fapshi collection confirmations credit reported net `revenue`, not the member's gross amount. Payouts require unanimous approval, use separate payout credentials/balance, and only debit the ledger after provider confirmation. Keep external manual payout recording distinct from provider-confirmed `PAID` status.

## Repository-specific conventions

- Preserve the `com.example.messaging` package layout and constructor injection pattern using Lombok `@RequiredArgsConstructor`.
- Use `UUID` identifiers across entities and API routes, and use ISO-8601 `Instant` for timestamps.
- Validate REST request records with Jakarta Validation and `@Valid`. Follow existing error handling with `ResponseStatusException` and explicit HTTP statuses.
- Keep authorization in service guard methods so REST and WebSocket entry points enforce the same neighborhood membership rules.
- Only verified members can access neighborhood funds. Moderators/admins create funds; every verified neighborhood administrator captured when a withdrawal is requested must approve before it can be completed.
- Treat financial records as append-only: contribution amounts are credited only after confirmed provider status, ledger entries cannot be updated/deleted, and unique source IDs prevent duplicate credits/debits.
- Validate payment callbacks against the configured `x-wh-secret` and the provider's authoritative transaction status. Keep payment credentials and tokens out of logs.
- Use response DTOs at API boundaries where existing mappers are provided; do not expose entities as a convenience.
- The STOMP endpoint is `/chatt`; preserve its spelling and documented destinations as client contracts.
