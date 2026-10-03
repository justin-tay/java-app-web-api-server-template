# ADR 0034: Authorized clients in the session

## Status

Accepted

## Context

After an authorization code login the application holds an
`OAuth2AuthorizedClient` for the user: the access token, and the refresh token
if Keycloak issued one, which `GET /account` and the `RestClient` use to call
Keycloak on the user's behalf. Spring Boot's default keeps them in an
`InMemoryOAuth2AuthorizedClientService`, a map in the instance that handled the
login. The application runs as a cluster behind a load balancer and its login
state is otherwise in the database (ADR 0006), so a request that reaches
another instance, or an instance that restarted, finds no authorized client and
sends the user through authorization again although the session is valid.

The other login state that is not the session itself, the link between a
Keycloak session and the local session, is already in the database
(`JdbcOidcSessionRegistry`). The OAuth2 authorization request, and with it the
`state` and the ID token `nonce`, is kept by Spring Security's default
`HttpSessionOAuth2AuthorizationRequestRepository` in the HTTP session, so it is
in the database already.

Two stores are shared across instances:

- `HttpSessionOAuth2AuthorizedClientRepository` keeps the clients in the HTTP
  session, which Spring Session holds in `SPRING_SESSION_ATTRIBUTES`.
- `JdbcOAuth2AuthorizedClientService` keeps them in its own
  `oauth2_authorized_client` table, keyed by registration ID and principal
  name.

## Decision

`commons` defines an `OAuth2AuthorizedClientRepository` bean that is an
`HttpSessionOAuth2AuthorizedClientRepository`, replacing Spring Boot's
in-memory default, unless the application defines its own.

- The authorized client ends with the session, however it ends: idle and
  absolute timeout, logout, back-channel logout, and an administrator ending
  the sessions of a user. No cleanup job and no table are needed, and no token
  outlives the session that obtained it.
- It adds no table, migration or code beyond the bean, and it stores nothing
  that the session does not already store.
- The client is part of one session. A user with two sessions has two
  authorized clients, and signing in again does not replace the other session's.

`JdbcOAuth2AuthorizedClientService` is not the default because its rows are not
tied to a session. Nothing deletes a row when a session expires or is revoked,
so a refresh token outlives the login that earned it until a job removes it,
and a second login of the same user overwrites the first one's row.

An application should opt for `JdbcOAuth2AuthorizedClientService` (by defining
an `OAuth2AuthorizedClientService` and an `OAuth2AuthorizedClientRepository`
bean over it, which replaces this one) when it must use the user's tokens
without that user's session, for example a background job or a message consumer
that calls an API on a user's behalf, or when it wants the tokens encrypted
separately from the session table. It then owns the table's migration, the
removal of rows for ended sessions, and the encryption of the token columns,
which the service does not do itself.

## Consequences

An instance can use an authorized client that another instance obtained, and a
restart keeps it. The tokens are stored in the session table, serialized with
the rest of the session, so the database protection already required for
session rows (encryption at rest, access limited to the application account)
covers them too; there is no separate encryption of the token values. A
deployment whose threat model includes reading the session table needs a
serializer or store that encrypts it.

Spring Boot still creates its `InMemoryOAuth2AuthorizedClientService` bean,
because it backs off only for a service of its own type, but nothing uses it
while this repository is in place.
