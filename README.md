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
`/oauth2/jwks` endpoint, and have back-channel logout configured. Each registers only
the exact redirect URI `<application URL>/login/oauth2/code/keycloak` and post-logout
redirect URI `<application URL>/login?logout`, and requires PKCE with `S256`. Re-run
the script below to apply this to clients created by an earlier version.

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

Provision the matching development users after the application database has been
started at least once. This creates `admin`, `test-user`, and `multi-group-user` in
Keycloak, each with the development-only password `password`. It intentionally does
not assign Keycloak roles: application access and roles are managed by the local
database migrations. The matching local users are development fixtures that
Liquibase creates only when the `dev` context is requested, which the `local` and
`test` profiles and `bin/start-api-server-tls.sh` do; a production database gets
none (see [Bootstrapping the first administrator](docs/system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md#bootstrapping-the-first-administrator)).
The local username is matched to Keycloak's `preferred_username` claim, so treat
those Keycloak usernames as immutable after a user has been provisioned.

```shell
node bin/seed-test-data.js
```

### Run the application

Both local runs use two separate profiles that share the name `local`:

- The `local` **Maven** profile (`-Plocal`) adds the H2 driver, so the application
  runs against an in-memory H2 database that Liquibase creates on every start.
- The `local` **Spring** profile (`src/main/resources/application-local.yaml`)
  disables TLS, marks the session cookie as non-secure, loads the development-only
  JWKS from `src/test/resources/jwks.json`, and requests the Liquibase `dev` context
  that creates the development users. Outside the `local` and `test` profiles
  `app.jwks` has no default and must be set to the deployment's own private JWKS,
  or the application fails to start.

`-Plocal` does not activate the Spring profile, so for local HTTP development
activate both:

```shell
mvn -Plocal spring-boot:run -Dspring-boot.run.profiles=local
```

For local TLS development, use the helper instead. It creates development-only
certificates under `.local/certs`, configures the HTTPS Keycloak client, and starts
the application with TLS enabled. It activates only the `local` Maven profile and
keeps the default Spring profile, so TLS and the secure session cookie stay on; it
supplies the development JWKS and the Liquibase `dev` context itself through the
`APP_JWKS` and `SPRING_LIQUIBASE_CONTEXTS` environment variables.

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

See the [documentation index](docs/README.md) for the system design document,
architecture decisions, specifications, and retained standards. The primary
implementation reference is the [security documentation](docs/system-design/08-crosscutting-concepts/02-security-and-authentication/README.md).
