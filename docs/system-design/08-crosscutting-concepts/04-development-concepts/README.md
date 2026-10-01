<!-- arc42-generated -->
# Development concepts

Build, formatting, testing, and native-image conventions that apply to every
module rather than to one feature.

## Build

Maven, via the wrapper (`mvnw`/`mvnw.cmd`), on the `spring-boot-starter-parent`
4.1.1 BOM, targeting Java 17. There is a single module; no multi-module
structure exists to document. A Maven `local` profile adds the H2 dependency
at `runtime` scope, paired with the Spring `local` profile
(`application-local.yaml`) that disables TLS and the `Secure` cookie
attribute for local development. `-Plocal` does not activate the Spring
profile, so a local HTTP run passes both
(`mvn -Plocal spring-boot:run -Dspring-boot.run.profiles=local`); see
[Configuration management](../05-operational-concepts/README.md#configuration-management).

## Code formatting

`spring-javaformat-maven-plugin`'s `apply` goal is bound to the default build
lifecycle in `pom.xml`, so every local `mvn` build reformats the source tree
to Spring's house style automatically; there is no separate lint step to run
by hand. CI enforces the same formatting without reformatting silently: after
`verify`, the `build-and-test.yml` workflow runs `git diff --exit-code` and
fails the job if formatting (or anything else) changed a tracked file,
turning an un-applied formatting difference into a build failure instead of
a merged inconsistency.

## Testing strategy

Test classes fall into two groups distinguished by suffix and by what they
stand up:

| Kind | Suffix | What it exercises |
| --- | --- | --- |
| Unit / slice test | `*Test` | A single filter, handler, service, or configuration class in isolation, often with `@WebMvcTest` or plain construction; for example `ApiResponseEntityExceptionHandlerTest`, `SessionRevocationServiceTest`. A commons auto-configuration is tested with `ApplicationContextRunner`, covering that it applies by default, backs off when its `enabled` property is `false`, and backs off when the application supplies its own bean; for example `TomcatHardeningAutoConfigurationTest`. |
| Integration test | `*IntegrationTest` | A running (or sliced) Spring context exercising a full request path, such as `WebSecurityConfigurationSessionManagementIntegrationTest`, `AbsoluteSessionTimeoutIntegrationTest`, and `TomcatHardeningIntegrationTest`, which confirms the commons hardening reaches the application's running Tomcat. |

Two shared support base classes remove duplication from integration tests:
`MockMvcITSupport` and `RestTestClientITSupport`
(`app-web-api-server/src/test/java/com/example/app/web/server/test`), giving MockMvc-based and
`RestTestClient`-based integration tests a common setup rather than each
test class configuring its own. `OAuth2ClientTestConfiguration` supplies a
stand-in OAuth2 client registration so security-chain tests do not depend on
a live Keycloak instance; `IdentityProviderDownIntegrationTest` turns it off
with `test.oauth2-client.static-registration=false` to start the application
against an `issuer-uri` that is not listening, and then brings a stub provider
up ([ADR 0029](../../../adr/0029-lazy-oidc-discovery.md)). The `test` Spring profile
(`application-test.yaml`) disables TLS, disables the `Secure` cookie
attribute, and binds the management port to `0` (an OS-assigned ephemeral
port) so parallel test runs never collide on the fixed `8082` management
port used outside tests.

Code coverage is measured by `jacoco-maven-plugin`, bound to `prepare-agent`
and a `verify`-phase `report` goal producing HTML, XML, and CSV reports.
`build-and-test.yml` parses the CSV to post an instruction/line/branch
coverage summary to the GitHub Actions job summary on every run, and uploads
the HTML/XML report and, on failure, the Surefire reports, as workflow
artifacts.

## Continuous integration

`.github/workflows/build-and-test.yml` runs on every pull request and on push
to `main`, with `concurrency` cancelling a superseded run for the same ref.
The job: checks out the source, sets up Temurin Java 17 with Maven dependency
caching, runs `mvnw -B -Dstyle.color=always verify` (compiling, testing, and
applying/verifying formatting in one invocation), summarizes coverage, and
enforces formatting via `git diff --exit-code`, all within a 15-minute
timeout. There is no separate deploy or release job in this repository; the
template ships as a starting point, not a deployed service.

<!-- arc42-manual: Document the release/versioning process (tagging, publishing) once this template is consumed as a real, released service rather than cloned as a starting point. -->

## GraalVM native image and Spring AOT

`org.graalvm.buildtools:native-maven-plugin` is on the build, enabling
`mvn -Pnative native:compile`/`native:build` to produce a native image
through Spring Boot's ahead-of-time (AOT) processing. Three things in the
codebase exist specifically to keep that processing correct:

* [`ApplicationRuntimeHints`](../../../../app-web-api-server/src/main/java/com/example/app/web/server/ApplicationRuntimeHints.java)
  implements `RuntimeHintsRegistrar`, the place to register any reflection or
  resource hint that AOT's static analysis cannot discover. It currently
  registers none: the private JWKS is read from the external location in
  `commons.security.oauth2.jwks`, not from a classpath resource that a native image would need a
  hint to include
  ([ADR 0018](../../../adr/0018-development-fixtures-kept-out-of-production.md)).
* [`CommonsRuntimeHints`](../../../../commons/src/main/java/com/example/commons/CommonsRuntimeHints.java),
  registered in `commons`' `META-INF/spring/aot.factories` so every application
  on `commons` gets it without importing it, holds the hints for `commons`
  itself: the `META-INF/commons-defaults.yaml` resource, which
  `CommonsDefaultsEnvironmentPostProcessor` reads before the application
  context exists, and the constructor of `AbstractAuthenticationFailureEvent`,
  which Spring Security's `DefaultAuthenticationEventPublisher` looks up
  reflectively. Without either, the native image fails to start.
* `TomcatHardeningAutoConfiguration`'s nested
  `JreMemoryLeakPreventionTomcatServletWebServerFactory` was changed from
  `private static final class` to package-private `static final class`
  (commit `eca529b`, "fix for spring boot aot"). Spring's AOT-generated
  initialization code lives in a separate generated class in the same
  package and must be able to reference and instantiate this nested class
  directly; a `private` nested class is only accessible through
  synthetic accessor methods the compiler generates for the *enclosing*
  class's own use, which AOT-generated code in a different class cannot
  call, so the build failed under AOT processing until visibility was
  widened to package-private.

<!-- arc42-manual: No CI job currently builds or tests the native image; add one (or note that native-image support is best-effort only) once native deployment is adopted. -->
<!-- /arc42-generated -->
