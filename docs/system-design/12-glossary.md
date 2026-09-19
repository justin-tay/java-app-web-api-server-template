# 12. Glossary

Terms this document uses that a reader needs defined. This is a reader's
glossary of domain and technical concepts, built only from what the rest of
this document already says about them.

For the project's canonical wording on concepts that were worded
inconsistently across the documentation (for example, the different flavors
of "deployment decision"), see [CONTEXT.md](../../CONTEXT.md) at the repo
root instead — that document exists for a different purpose than this one
and is not duplicated here.

| Term | Definition |
| --- | --- |
| Relying party | The role this application plays in OIDC: it delegates authentication to Keycloak and consumes the resulting identity, rather than collecting credentials itself. See [Authentication](08-crosscutting-concepts/security/authentication.md). |
| Authorization-code flow | The OIDC login sequence the application uses: the browser is redirected to Keycloak, authenticates there, and returns with a code the application exchanges for tokens. See [Runtime View](06-runtime-view.md#oidc-authorization-code-flow). |
| `private_key_jwt` | The client-authentication method the application uses when exchanging an authorization code with Keycloak: it signs a JWT assertion with its own private key instead of sending a shared client secret. See [Authentication](08-crosscutting-concepts/security/authentication.md#client-authentication). |
| JWKS | JSON Web Key Set: the published set of public keys (`/oauth2/jwks`) Keycloak uses to verify the application's signed client assertions and, if enabled, encrypt ID tokens to it. See [Authentication](08-crosscutting-concepts/security/authentication.md#application-jwks-and-key-handling). |
| Authority | A Spring Security permission string (for example `ROLE_USER_MANAGE`), derived from a local user's group and role memberships and reloaded on every request rather than cached at login. See [Authorization](08-crosscutting-concepts/security/authorization.md). |
| Audit identifier (`session.id` in logs) | A random, server-side identifier used only in session lifecycle log events, deliberately distinct from the actual session cookie or Spring Session ID so log output never carries a value that could be replayed. See [Sessions](08-crosscutting-concepts/security/sessions.md) and [ADR 0008](../adr/0008-session-lifecycle-audit-identifiers.md). |
| Correlation ID (`http.request.id`) | A per-request identifier attached to every log event for that request, generated as a UUID by default or supplied by an ingress-specific resolver. See [Logging](08-crosscutting-concepts/logging/README.md). |
| ECS | Elastic Common Schema: the structured JSON log field schema this application emits to stdout. See [Logging](08-crosscutting-concepts/logging/README.md) and [ADR 0010](../adr/0010-ecs-structured-logging.md). |
| Problem Details | The RFC 9457 (`application/problem+json`) error-response format this application's API uses for every error, identified by a stable `type` URI. See [Error responses](08-crosscutting-concepts/security/error-responses.md) and [ADR 0013](../adr/0013-rfc-9457-problem-details.md). |
| ASVS | OWASP's Application Security Verification Standard: the requirement catalog [ASVS](08-crosscutting-concepts/security/asvs.md) maps against, at requirement granularity. |
| CIS Benchmark | Here, the CIS Apache Tomcat 11 Benchmark: the control catalog [Hardening](08-crosscutting-concepts/security/hardening.md) maps against. |
