# Java App Web API Server Template

This is an opinionated template for a Java Application Web API Server used to service browser clients.

[Keycloak](https://github.com/keycloak/keycloak) is used as the public Identity Provider for testing purposes

## Quick Start

### Configure Keycloak as the Identity Provider

The application has two Keycloak clients:

| Client ID | Application URL | Purpose |
| --- | --- | --- |
| `java-app-web-api-server` | `http://localhost:8081` | Local HTTP development |
| `java-app-web-api-server-secure` | `https://localhost:8081` | Local TLS development |

Both clients use `private_key_jwt`, publish their public keys at the application's
`/oauth2/jwks` endpoint, and have back-channel logout configured.

Start Keycloak

```shell
export KC_HOME=/path/to/keycloak
./bin/start-keycloak-server.sh
```

In another terminal, provision the `test` realm and both clients. The script uses
`admin` / `admin` by default; set `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD`
if you use different credentials.

```shell
node bin/configure-keycloak.js
```

The script is idempotent and enables user registration for the realm. It requires a
Node.js version that provides the global `fetch` API.

### Run the application

For local HTTP development, activate the `local` Maven profile. It disables TLS and
marks the session cookie as non-secure.

```shell
mvn -Plocal spring-boot:run
```

For local TLS development, use the helper instead. It creates development-only
certificates under `.local/certs`, configures the HTTPS Keycloak client, and starts
the application with TLS enabled.

```shell
./bin/start-api-server-tls.sh
```

Trust `.local/certs/local-ca.pem` in your browser or operating system before using
the HTTPS endpoint.

### Testing the example Relying Party

| Description                   | Endpoint
|-------------------------------|-----------------------------------------------------
| Access the application        | http://localhost:8081/login-user
| Call the Keycloak account API | http://localhost:8081/account
| Logout from the application   | http://localhost:8081/logout
| View the public keys          | http://localhost:8081/oauth2/jwks
| Access Keycloak user account  | http://localhost:8080/realms/test/account
| Logout from Keycloak          | http://localhost:8080/realms/test/protocol/openid-connect/logout

## Integration Details

See [HTTP security headers](docs/security-headers.md) for the configured headers,
Spring Security defaults, OWASP guidance, and deployment checks.
See [security logging](docs/security-logging.md) for the OWASP logging-control
matrix, and [the logging schema](docs/security-logging-schema.md) for the ECS
field contract and project extensions.
See [security authentication](docs/security-authentication.md) for the OIDC
relying-party design, client authentication, token validation, key management,
logout, and authentication flow.
