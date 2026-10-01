# ADR 0029: Lazy OIDC discovery

## Status

Accepted

## Context

The Keycloak client registration is configured by `issuer-uri`. Spring Boot
builds its `ClientRegistrationRepository` while the context starts, and for
every registration with an `issuer-uri` that means fetching the provider's
`/.well-known/openid-configuration` at that moment. With Keycloak not
listening, the application exits at once, although it can serve everything
that needs no login, and although an orchestrator restarting it in a loop
only adds load to a provider that is already down. Keycloak is also often
started after, or restarted independently of, the applications that use it.

Spring Security's `ClientRegistrations` does the fetch with a static
`RestTemplate` whose connect and read timeouts are fixed at 30 seconds, and
whose HTTP client cannot be configured; the requests to make it configurable
(spring-security#14176 and #14777, the second one asking for an `SSLContext`)
were declined. A Keycloak behind a private CA therefore cannot be discovered
through it at all. Spring Security also iterates the repository at startup to
build the default login page, so a repository that is merely wrapped still
resolves every registration when the filter chain is built.

## Decision

`commons` replaces Boot's repository with `LazyClientRegistrationRepository`
when a client provider has an `issuer-uri`:

- Registration IDs are known from the properties alone. A registration is
  resolved from the provider's metadata on first use, and a resolved
  registration is kept for the life of the process. A registration without an
  `issuer-uri` needs no network and is built at once.
- Boot's own `OAuth2ClientPropertiesMapper` still builds the registration,
  so the properties keep their meaning: discovered endpoints fill only what
  the configuration leaves unset. The discovery metadata is kept on the
  registration, so the OIDC logout handler still finds `end_session_endpoint`.
- The metadata is fetched by `OidcDiscoveryClient`, with
  `commons.security.oauth2.discovery.connect-timeout` (default 2 seconds) and
  `read-timeout` (default 5 seconds), and its `issuer` must equal the
  configured `issuer-uri`.
- A provider whose certificate is issued by a CA the JVM does not trust names
  an SSL bundle in `commons.security.oauth2.client.provider.<id>.ssl-bundle`,
  keyed by the same provider ID as `spring.security.oauth2.client.provider`.
  The bundle's trust material applies to every call the application makes to
  that provider, not only discovery: the discovery fetch, the token endpoint
  (a token client for the authorization code grant that picks the client by
  registration, so it works for `private_key_jwt` and for a client secret
  alike), the ID token's JWK Set and the user info endpoint. A provider without
  a bundle keeps Spring Security's own client for each of these, and so the
  JVM's default trust. The bundle is a Spring Boot `spring.ssl.bundle`, so a PEM
  CA supplied through an environment variable or a secrets manager needs no
  file on disk and no JVM truststore setup. A provider ID that matches no
  provider, or a bundle that does not exist, fails startup. These calls speak
  HTTP/1.1 and do not follow redirects: a provider's endpoints answer where
  they are configured, so a redirect is reported as a misconfiguration instead
  of being followed.
- Startup tries every registration once. A definitive failure (a 4xx response
  other than 408 and 429, a document that is not JSON, an issuer mismatch, or a
  TLS certificate the provider presented that is not trusted or does not match
  the host) is a misconfiguration and fails startup. A transient one (no connection, a
  timeout, a 5xx response) does not.
- After a failed attempt, further requests fail at once until
  `commons.security.oauth2.discovery.retry-interval` (default 30 seconds) has
  passed, so a provider that is down is not called on every request. The
  failure is logged once per attempt.
- A login request (`/oauth2/authorization/{id}` and `/login/oauth2/code/{id}`)
  for a registration that cannot be resolved is answered with 503, an RFC 9457
  problem of type `urn:problem:identity-provider-unavailable` (ADR 0013) and a
  `Retry-After` header. A filter ahead of the OAuth2 filters does this,
  because Spring Security's authorization redirect filter turns an exception
  from resolving the registration into a 401. A browser navigation, which is
  how a single-page application starts a login (`window.location` to the
  authorization URL), would display that JSON, so a request that accepts
  `text/html` is instead redirected with `Retry-After` to
  `commons.security.oauth2.discovery.unavailable-redirect-uri` (default
  `/?error=identity_provider_unavailable`, relative so it resolves against the
  address the browser used). The application's login gate reads the `error`
  parameter and says that single sign-on is unavailable, while a passkey
  login, which needs no Keycloak (ADR 0024), stays usable.
- An `oidcDiscovery` health contributor is DOWN until every registration with
  an `issuer-uri` has been resolved once, and UP from then on. The commons
  defaults add it to the readiness group whenever a provider has an
  `issuer-uri`, next to `jwks` (ADR 0020). Each readiness check retries an
  unresolved registration within the retry interval, because a load balancer
  sends no login request to an instance that is not ready. The details name
  registration IDs and times only, never the issuer or the failure text.
- The repository is not `Iterable`, so the default login page lists no
  providers; with one registration a browser is redirected to it directly, as
  before.

## Consequences

The application starts, stays live and answers non-login requests while
Keycloak is down, and logs in again without a restart once it is up. An
instance is not ready until it has reached Keycloak once, so a deployment
must point the load balancer at the readiness group, and must not use it as
the orchestrator's restart check (ADR 0020 already says to use the liveness
group for that).

Discovery metadata is read once per process, so a change to Keycloak's
endpoints needs a restart. A deployment that needs the login page to list
several providers, or that wants startup to fail while Keycloak is down,
defines its own `ClientRegistrationRepository`, which replaces this one.

A mistyped `issuer-uri` still fails startup, but only when the provider
answers; while it is down the typo shows as a 503 and as `oidcDiscovery`
staying DOWN.
