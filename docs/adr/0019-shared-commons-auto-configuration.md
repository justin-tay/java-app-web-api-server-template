# ADR 0019: Shared commons auto-configuration

## Status

Accepted

## Context

The template's security and logging controls (the CIS Tomcat hardening, the
request correlation and logging filters, RFC 9457 Problem Details, and the
secure configuration values) lived inside the one application. A team running
several API backends had to copy those classes and `application.yaml` settings
into each backend and keep the copies in step by hand, which does not scale
and lets a backend quietly drift from the baseline.

The template is not published as a library. Teams clone it and own their
copy, so whatever is shared must be reusable inside one team's repository
without a release process.

Spring Boot's auto-configuration applies configuration because a jar is on
the classpath, and backs off through conditions when an application opts out
or supplies its own bean. A class that an application's component scan finds
is registered as ordinary configuration instead, which bypasses those
conditions.

## Decision

The repository is a Maven multi-module build. The root `pom.xml` inherits
`spring-boot-starter-parent`, so every module uses the same Spring Boot
version, and lists the modules:

- `commons` holds the shared, secure-by-default auto-configuration, in
  package `com.example.commons`, with one subpackage and property prefix per
  concern: `commons.logging`, `commons.security`, and `commons.web` (for the
  servlet container and Spring MVC: `commons.web.tomcat`, `commons.web.problem`).
- `app-web-api-server` is the reference application, in package
  `com.example.app.web.server`. Each further backend is another `app-<name>`
  module in package `com.example.app.<name>` that depends on `commons`.

Shared (`com.example.commons.*`) and application (`com.example.app.*`)
packages are siblings, never parent and child, so no application's component
scan picks up a commons class.

`commons` applies itself when it is on the classpath:

- Each group is an `@AutoConfiguration` that is on unless its property is
  `false`: `commons.web.tomcat.enabled` (`TomcatHardeningAutoConfiguration`),
  `commons.logging.enabled` (`LoggingAutoConfiguration`),
  `commons.web.problem-details.enabled` (`ProblemDetailsAutoConfiguration`), and
  `commons.security.enabled` (`WebSecurityAutoConfiguration`, plus
  `PrivateKeyJwtAutoConfiguration`, which applies only when a client registration
  uses `private_key_jwt`).
- A replaceable strategy, such as `ClientIpResolver`, `RequestIdResolver`, the
  web server factory, the `ErrorController`, or the
  `ResponseEntityExceptionHandler`, is `@ConditionalOnMissingBean`, so an
  application swaps one implementation without turning its group off.
- Filters and settings that must sit inside Spring Security's filter chain
  (the logging filters, then the security baseline) are added by ordered
  `Customizer<HttpSecurity>` beans. Spring Security applies such
  beans to every `HttpSecurity` before the application's own
  `SecurityFilterChain` bean method runs, so each application's chain keeps
  only its own concerns and can still position its filters relative to the
  commons ones.
- The security baseline reaches an application's user model only through
  `LocalAuthorityLookup`, which each application implements; startup fails when
  there is none, rather than falling back to identity provider roles. Because
  Spring Security denies any request that matches no authorization rule once any
  rule is configured, and commons always configures one, an application's
  chain that omits its final `anyRequest().authenticated()` rule fails closed.
- Secure configuration values (TLS protocols and cipher suites, the session
  cookie and timeouts, the Actuator management port and exposure, the ECS log
  format, and trace correlation) are
  in `commons-defaults.yaml`. `CommonsDefaultsEnvironmentPostProcessor`
  adds it below every application configuration source, so an application
  overrides a single key by setting it. It is not an `application.yaml` inside
  the jar, which would collide with the application's own file on the
  classpath.
- `TomcatApplicationContextInitializer` stays registered through
  `META-INF/spring.factories`, because it must run before the embedded server
  exists, and honours `commons.web.tomcat.enabled`.

The move is phased, with each phase leaving every test passing: first the
Tomcat hardening, logging, and Problem Details groups; then the security
filter chain, sessions, JWKS handling, and a `LocalAuthorityLookup`
interface for local authorities; then an optional `commons-accounts` module
for local users, groups, roles, and the administration API.

The control implementations under `docs/system-design/08-crosscutting-concepts/`
stay at the repository root and describe the whole system, citing module paths
as evidence.

## Consequences

A new backend inherits the whole baseline by depending on `commons`, and
a fix to a shared control is made once per team repository. Teams still own
and may edit their copy of `commons`; the template gives no mechanism for
pulling later template changes into a clone.

Turning a group off, replacing one of its beans, or overriding one of its
defaults is visible in the application's own code or configuration, and a
control implementation row that relies on it must be reassessed for that
application.

Because the defaults rank below the application's configuration, an
application that sets a list property such as `server.ssl.ciphers` replaces
the whole default list rather than extending it.

The security group assumes the template's architecture: an application on
`commons` needs a JDBC `DataSource` for Spring Session (ADR 0006), an OIDC
client registration (ADR 0005), and a `LocalAuthorityLookup` bean, or it must set
`commons.security.enabled=false` and supply its own security configuration.

Coverage is aggregated in each application module, since much of
`commons` is exercised only by an application's integration tests. The
coverage report and the CI summary now come from
`app-web-api-server/target/site/jacoco-aggregate`.
