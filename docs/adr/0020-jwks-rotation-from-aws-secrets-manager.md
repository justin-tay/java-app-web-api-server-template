# ADR 0020: JWKS rotation from AWS Secrets Manager

## Status

Accepted

## Context

ADR 0007 has the application authenticate to Keycloak with `private_key_jwt`,
with its private JWKS supplied by the deployment, and leaves rotation of the
client keys to be coordinated with Keycloak by hand. ADR 0018 keeps any JWKS
out of the artifact, so a deployment must name its own. Until now the JWKS was
one resource read once at startup, so a new key needed a restart, and the key
that signs was simply the first `sig` key.

On AWS, the JWKS is kept in Secrets Manager by
[`cdk-jwks-secret`](https://github.com/justin-tay/cdk-jwks-secret), a CDK
construct whose rotation Lambda rotates it every 28 days by default. Its
rotation keeps two parties' caches safe: each new key is published for a whole
interval before it is used, a retired `sig` key keeps only its public part for
one more interval, and an `enc` key is unpublished one interval before it is
deleted. That only works if the application reads the secret's
`AWSCURRENT` version again well within the interval, signs with the first
`sig` key that still has its private part, and publishes each key as the rules
say. The construct keeps `sig` and `enc` keys in separate secrets, and a secret
is empty until its first rotation, shortly after the stack is deployed.

Keycloak can also encrypt ID tokens to a client's `enc` key, which it reads
from the same JWKS URL. Keycloak 26.0 added the `ECDH-ES` key management
algorithms, the construct's default for `enc` keys.

Spring Cloud AWS's `spring.config.import=aws-secretsmanager:` loads a secret
as configuration properties, flattening a JSON secret's top-level members. A
JWKS has one top-level member, an array of keys, so that import does not fit,
and its refresh rebinds configuration properties rather than the beans that
hold the keys.

## Decision

The private JWKS is refreshed at runtime rather than read once:

- `commons.security.oauth2.jwks` becomes a list of resource locations, with no
  default. With more than one location, each holds the keys of one use, and
  each use comes from one location, so a deployment names its `sig` secret
  and its `enc` secret. A single location may hold both, as the development
  JWKS does.
- `RefreshingJwks` reads the locations at startup, failing startup if one
  cannot be read or is invalid, and then every
  `commons.security.oauth2.jwks-refresh-interval` (default 1 hour, between
  1 minute and 1 day). A read that fails keeps the last good keys. When there
  is no signing key, or no `enc` key matches an ID token's `kid`, it reads again
  at once, at most once every 30 seconds.
- The signing key is the first `sig` key with a private part. Every `sig` key is
  published; every `enc` key is published except the first when there are
  three. An ID token encrypted to an `enc` key is decrypted with the key its
  `kid` names, using the key's own algorithm and any RFC 7518 content
  encryption. While there are `enc` keys, an ID token that is not encrypted is
  rejected.
- A `jwks` health contributor is DOWN until there is a signing key and while
  any location has no keys. The commons defaults turn on the liveness and
  readiness probe groups, and for a `private_key_jwt` application add `jwks` to
  the readiness group, so an instance takes no traffic before its secrets are
  initialised, without failing liveness.

AWS support is a new optional module, `commons-aws`, with Spring Cloud AWS's
Secrets Manager starter for the `SecretsManagerClient`, its region, and its
credentials. It adds a protocol resolver so that a resource location
`aws-secretsmanager:<secret name or ARN>` reads the raw `AWSCURRENT` value of
the secret. The prefix is the same as Spring Cloud AWS's config data import,
but it is unrelated: in a resource location it always means the secret's value,
as is. The module uses the AWS SDK's `url-connection-client` and excludes
`apache5-client` and `netty-nio-client`, because Apache HttpClient 5 on the
classpath would become the client Spring's `RestClient` uses for every HTTP
call the application makes. Credentials come from the AWS SDK default chain,
such as an ECS task role or EKS Pod Identity, never from committed
configuration.

The reference application depends on `commons-aws`. Its `local` and `test`
profiles keep the development JWKS file and set
`spring.cloud.aws.secretsmanager.enabled=false`.

## Consequences

Keys rotate without a restart and without coordinating with Keycloak by hand,
superseding that part of ADR 0007. The application follows the
`cdk-jwks-secret` rotation rules, so it depends on that layout: a JWKS
produced some other way must keep the same key order.

A deployment must name one secret per use, grant the application read access
to each (`jwksSecret.grantRead(role)`), and point the load balancer at the
readiness group and the orchestrator's liveness check at the liveness group.
A refresh interval longer than a fraction of the rotation interval, or a
rotation within Keycloak's JWKS cache lifetime, breaks the rotation rules.

Keycloak must be 26.0 or later for `ECDH-ES` ID token encryption, and a client
with an `enc` key must have ID token encryption turned on, or every login
fails.

`commons-aws` has not been verified in a GraalVM native image; see
[Section 11](../system-design/11-risks-and-technical-debt.md).
