# Security hardening crosswalk

This document crosswalks the template against the [CIS Apache Tomcat 11 Benchmark
v1.1.0](https://www.cisecurity.org/benchmark/apache_tomcat). It is a deployment
planning aid, not a CIS conformance claim.

Its scope is Tomcat hardening. Generic platform concerns—such as runtime-image
minimization, operating-system permissions, process identity, mount layout, and log
collector protection—are outside this crosswalk unless needed to explain a residual
risk after a Tomcat-specific control is not applicable.

## Scope and status meanings

The benchmark targets standalone Apache Tomcat 11 installed from Linux tar packages.
This template instead runs Spring Boot 4 with embedded Tomcat 11. As a
result, controls referring to `$CATALINA_HOME`, `$CATALINA_BASE`, `server.xml`,
Tomcat Manager, Catalina scripts, or Tomcat Realms cannot be assessed literally here.
The supplied CIS benchmark workbook and document are the source for the control set;
control names below are concise labels to make this crosswalk navigable.

| Status | Meaning |
| --- | --- |
| Configured | The template explicitly implements the control's intent. Evidence is cited in the comment. |
| Not applicable to embedded Tomcat | The benchmark mechanism is absent from this embedded-server application. This does not mean the underlying risk is always absent. |
| Deployment decision required | The template cannot safely choose the value; the deployer must configure it in the image, runtime, ingress, or reverse proxy. |
| Verify runtime default | No template setting overrides the embedded-server default. Verify the effective version and deployment configuration before relying on it. |

The CIS Apache Tomcat 11 Benchmark v1.1.0 contains 59 recommendations in sections 1
through 9. Use the benchmark that matches the deployed Tomcat major version and an
image/runtime scan for the actual deployed version.

## 1. Remove extraneous resources

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 1.1 | Remove extraneous files and directories | Not applicable to embedded Tomcat | Embedded Tomcat does not ship the standalone `docs`, `examples`, Manager, or Host Manager web applications that this control targets. Runtime-image minimization is a separate platform-hardening concern. |
| 1.2 | Disable unused connectors | Configured | The template configures one embedded HTTPS connector through `server.ssl` and does not add HTTP, AJP, or additional Tomcat connectors. Local and test profiles deliberately disable TLS for development. Expose only the required listener port at the container, firewall, and ingress. |

## 2. Limit server-platform information leaks

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 2.1 | Alter advertised `server.info` string | Not applicable to embedded Tomcat | The client-facing risks are Tomcat's default HTML error-page footer and `X-Powered-By`, which incorporates this metadata when enabled. The template disables `X-Powered-By` (2.4) and replaces Tomcat's error page with a generic Problem Details response (2.5), so the metadata is not exposed. Embedded Tomcat packages `ServerInfo.properties` in its dependency JAR; do not edit it or other dependency-JAR contents, because this breaks artifact integrity, reproducible builds, checksums/SBOM provenance, and will be undone when dependencies are patched. |
| 2.2 | Alter advertised `server.number` string | Not applicable to embedded Tomcat | See 2.1. |
| 2.3 | Alter advertised `server.built` date | Not applicable to embedded Tomcat | See 2.1. |
| 2.4 | Disable `X-Powered-By` and replace connector server value | Configured | `TomcatConfiguration` explicitly disables `X-Powered-By`, leaves the connector `server` value unset so Tomcat emits no `Server` header, and sets `serverRemoveAppProvidedValues=true` to remove any `Server` header added by application code. `TomcatConfigurationTest.doesNotDiscloseTomcatOrApplicationServerInformation()` verifies the effective connector configuration. |
| 2.5 | Disable client-facing stack traces | Configured | `TomcatConfiguration` installs `TomcatProblemDetailErrorReportValve` for Tomcat-level errors, and `ApiResponseEntityExceptionHandler` returns generic RFC 9457 responses for MVC errors. Do not replace these with debug error pages in production. |
| 2.6 | Turn off TRACE | Configured | `TomcatConfiguration` explicitly sets `Connector.allowTrace=false`, so Tomcat rejects TRACE requests before they reach the application. `TomcatConfigurationTest.rejectsTraceRequests()` verifies the effective connector configuration. |
| 2.7 | Remove the Server header to prevent information disclosure | Configured | The template takes the stricter approach of emitting no Tomcat `Server` header rather than substituting a generic value; the connector configuration and its regression test are documented in 2.4. |

## 3. Protect the shutdown port

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 3.1 | Set a nondeterministic shutdown command | Not applicable to embedded Tomcat | Spring Boot does not use the standalone Catalina shutdown command. Restrict process and orchestration-control-plane access instead. |
| 3.2 | Disable the shutdown port | Not applicable to embedded Tomcat | There is no standalone Tomcat shutdown port. Do not expose an application-management port unless it is authenticated and network-restricted. |

## 4. Protect Tomcat configurations

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 4.1 | Restrict access to `$CATALINA_HOME` | Not applicable to embedded Tomcat | There is no unpacked standalone installation or `$CATALINA_HOME`. Apply the corresponding safeguard by running as a non-root user and making application/dependency layers read-only where the platform supports it. |
| 4.2 | Restrict access to `$CATALINA_BASE` | Not applicable to embedded Tomcat | There is no standalone `$CATALINA_BASE`. Protection of runtime writable directories is a separate platform-hardening concern. |
| 4.3 | Restrict access to the Tomcat configuration directory | Not applicable to embedded Tomcat | There is no standalone Tomcat configuration directory. Spring configuration and secret material are deployment inputs; restrict configuration mounts and environment-secret access to the service identity. |
| 4.4 | Restrict access to the Tomcat logs directory | Not applicable to embedded Tomcat | There is no Tomcat `logs` directory: the template emits ECS JSON to standard output. Protect the platform log sink, retention, and operator access instead. |
| 4.5 | Restrict access to the Tomcat temp directory | Deployment decision required | Set a dedicated, non-shared writable temporary directory with appropriate ownership if the runtime needs one. |
| 4.6 | Restrict access to the Tomcat binaries directory | Not applicable to embedded Tomcat | There is no Tomcat binaries directory: embedded Tomcat is packaged in dependency/application artifacts. Protect the container image and dependency supply chain; do not permit runtime modification of application binaries. |
| 4.7 | Restrict access to the Tomcat web application directory | Not applicable to embedded Tomcat | There is no separate Tomcat web application directory. Package the application immutably; do not mount a writable application directory or permit runtime deployment of artifacts. |
| 4.8 | Restrict access to `catalina.properties` | Not applicable to embedded Tomcat | The template has no standalone `catalina.properties`. Treat external Spring configuration and JVM options as protected deployment configuration. |
| 4.9 | Restrict access to `context.xml` | Not applicable to embedded Tomcat | The template configures embedded Tomcat in Java and Spring properties, not a standalone `context.xml`. Protect the source repository and runtime configuration. |
| 4.10 | Restrict access to `logging.properties` | Not applicable to embedded Tomcat | Logging is configured by Spring Boot, not Tomcat JULI `logging.properties`. Protect logging configuration and the log pipeline. |
| 4.11 | Restrict access to `server.xml` | Not applicable to embedded Tomcat | There is no standalone `server.xml`; connector configuration is via Spring Boot properties and `TomcatConfiguration`. |
| 4.12 | Restrict access to `tomcat-users.xml` | Not applicable to embedded Tomcat | The template has no Tomcat users database or Manager application. Authentication uses OIDC. |
| 4.13 | Restrict access to `web.xml` | Not applicable to embedded Tomcat | The application is Spring Boot Java configuration; it does not supply a standalone global `web.xml`. |
| 4.14 | Restrict access to `jaspic-providers.xml` | Not applicable to embedded Tomcat | JASPIC provider configuration is not used. Do not add unreviewed container authentication providers. |

## 5. Configure Realms

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 5.1 | Use secure Realms | Not applicable to embedded Tomcat | The application does not use Tomcat Realms. `WebSecurityConfiguration` uses OAuth 2.0/OIDC with Keycloak; secure the identity-provider configuration and issuer/JWKS trust. |
| 5.2 | Use LockOut Realms | Not applicable to embedded Tomcat | Account lockout is an identity-provider policy, not a Tomcat Realm. Configure brute-force protection, MFA, and recovery policy in the production identity provider. |

## 6. Connector security

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 6.1 | Set up client-certificate authentication | Deployment decision required | Mutual TLS is not enabled. Decide whether machine/API-client authentication requires mTLS; configure it at the proxy or with a Spring SSL truststore and client-auth policy. The `CA_BUNDLE_PEM` input is not itself evidence that mTLS is enabled. |
| 6.2 | Enable SSL for sensitive connectors | Configured | The default profile enables `server.ssl` and supplies its PEM bundle through `CERTIFICATE_PEM` and `PRIVATE_KEY_PEM`; `local` and `test` deliberately disable TLS. Production must provide valid key material and never activate those development profiles. |
| 6.3 | Set connector scheme accurately | Configured | Spring Boot configures the direct TLS connector with the correct scheme. If TLS terminates at a proxy instead, configure and restrict forwarded-header handling so redirects, cookies, and OIDC callback URLs see the correct external scheme. |
| 6.4 | Set `secure` only for SSL-enabled connectors | Configured | Spring Boot manages `secure` correctly for the direct TLS connector. Review the proxy/Tomcat topology whenever TLS termination changes. |
| 6.5 | Configure secure connector TLS protocol | Configured | `application.yaml` permits only TLS 1.2 and TLS 1.3 and lists explicit AEAD cipher suites. Reassess cipher policy when the JDK, embedded Tomcat, or organizational TLS baseline changes. |

## 7. Establish and protect logging facilities

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 7.1 | Application-specific logging | Configured | ECS structured logging, request lifecycle logging, authentication/authorization events, and safe error logging are documented in [security logging](security-logging.md) and [the logging schema](security-logging-schema.md). Configure durable centralized collection and alerting in production. |
| 7.2 | Specify file handlers in `logging.properties` | Not applicable to embedded Tomcat | The template intentionally logs to standard output for platform collection rather than Tomcat JULI file handlers. Ensure the container/orchestrator exports logs durably and protects access. |
| 7.3 | Set `className` correctly in `context.xml` | Not applicable to embedded Tomcat | This is a Tomcat access-log-valve control. Request logging is provided by `RequestLoggingFilter`; configure ingress/proxy access logs as a complementary boundary record. |
| 7.4 | Use a secure logging directory in `context.xml` | Not applicable to embedded Tomcat | No Tomcat access-log valve or `context.xml` is configured. Protect the platform log collector, bucket/index, and credentials. |
| 7.5 | Use a correct access-log pattern in `context.xml` | Not applicable to embedded Tomcat | The filter emits the documented ECS fields instead. Do not log credentials, tokens, cookies, or request bodies in an ingress access-log format. |
| 7.6 | Use a secure log directory in `logging.properties` | Not applicable to embedded Tomcat | No JULI file logging is used. Set platform log retention, encryption, access control, and loss monitoring. |

## 8. Application deployment

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 8.1 | Disable automatic deployment of applications | Not applicable to embedded Tomcat | Embedded Spring Boot does not watch a `webapps` directory for runtime deployment. Deliver immutable application images and restrict deployment permissions. |
| 8.2 | Disable deployment on startup of applications | Not applicable to embedded Tomcat | The application starts the packaged artifact only; it does not deploy arbitrary WARs from a Tomcat deployment directory. |

## 9. Miscellaneous configuration settings

| CIS ID | CIS intent | Status | Template and deployment comment |
| --- | --- | --- | --- |
| 9.1 | Put web content on a separate partition from Tomcat system files | Not applicable to embedded Tomcat | Embedded Spring Boot has neither a standalone Tomcat system directory nor a separate Tomcat `webapps` directory. Mount and volume layout is a separate platform-hardening concern. |
| 9.2 | Restrict access to the web administration application | Not applicable to embedded Tomcat | Tomcat Manager is not packaged. Restrict cloud/orchestrator administration interfaces separately. |
| 9.3 | Restrict the Manager application | Not applicable to embedded Tomcat | No Manager application or `manager-*` roles exist. |
| 9.4 | Force SSL for the Manager application | Not applicable to embedded Tomcat | No Manager application exists. Ensure administration consoles elsewhere use strong authentication, network restriction, and TLS. |
| 9.5 | Rename the Manager application | Not applicable to embedded Tomcat | No Manager application is present. Do not regard path renaming as an access-control substitute for other administrative interfaces. |
| 9.6 | Enable strict servlet compliance | Configured | The `spring.factories`-registered `TomcatApplicationContextInitializer` sets `org.apache.catalina.STRICT_SERVLET_COMPLIANCE=true` before Spring creates embedded Tomcat; `TomcatConfigurationTest.enablesStrictServletComplianceBeforeEmbeddedTomcatStarts()` guards the setting in the live-server context. Retain integration coverage for redirects, sessions, filters, and added servlet libraries. |
| 9.7 | Turn off session facade recycling | Configured | `TomcatConfiguration` explicitly sets the embedded connector's `discardFacades=true`, so Tomcat discards request/response facade objects after every request; `TomcatConfigurationTest.discardsRequestAndResponseFacadesAfterEachRequest()` guards it. The template also declares `spring-session-jdbc`, so Spring Boot replaces servlet `HttpSession` with JDBC-backed Spring Session. Session persistence and Tomcat facade lifecycle are separate controls. |
| 9.8 | Disallow additional path delimiters | Configured | The CIS audit names legacy JVM flags `CoyoteAdapter.ALLOW_BACKSLASH=false` and `UDecoder.ALLOW_ENCODED_SLASH=false`. Tomcat 11 replaces the latter mechanism with connector settings, so `TomcatConfiguration` explicitly sets `allowBackslash=false`, `encodedSolidusHandling=reject`, and `encodedReverseSolidusHandling=reject`; `TomcatConfigurationTest.rejectsAdditionalPathDelimiters()` guards the effective connector values. Test the deployed proxy and application together after upgrades because an edge can normalize paths before Tomcat receives them. |
| 9.9 | Configure connection timeout | Deployment decision required | No `server.tomcat.connection-timeout` is set. Choose a timeout based on request-body size, slow-client protection, load balancer timeouts, and expected API behavior. |
| 9.10 | Configure maximum HTTP header size | Deployment decision required | No maximum request-header size is configured. Set a bounded `server.max-http-request-header-size` and align proxy/ingress limits with expected OIDC cookies and headers. |
| 9.11 | Force SSL for all applications | Configured | The default profile enables TLS and marks the session cookie secure. Production deployment must also enforce HTTPS at the public edge, redirect/reject HTTP, and use trusted forwarded-header configuration if TLS terminates upstream. |
| 9.12 | Disallow symbolic linking | Configured | `TomcatConfiguration` explicitly sets the web application resources' `allowLinking=false`; `TomcatConfigurationTest.disallowsSymbolicLinksInWebApplicationResources()` verifies the live context. Do not mount application resources through unsafe symbolic links, and review any future custom `WebResourceSet`, which can override the root setting. |
| 9.13 | Do not run applications as privileged | Configured | `TomcatConfiguration` explicitly sets `Context.privileged=false`; `TomcatConfigurationTest.doesNotRunTheWebApplicationAsPrivileged()` verifies the live context. Do not add `privileged=true` contexts or Manager libraries. |
| 9.14 | Disallow cross-context requests | Configured | `TomcatConfiguration` explicitly sets `Context.crossContext=false`; `TomcatConfigurationTest.disallowsCrossContextRequests()` verifies the live context. Reassess this setting if multiple web applications are ever hosted in one JVM. |
| 9.15 | Do not resolve hosts in logging valves | Not applicable to embedded Tomcat | No Tomcat logging valve is configured. `RequestLoggingFilter` records the direct peer only for CSRF failures; configure proxy/IP handling deliberately rather than reverse DNS lookups. |
| 9.16 | Enable the memory-leak listener | Configured | `TomcatConfiguration` adds `JreMemoryLeakPreventionListener` to the embedded Tomcat `Server` before initialization; `TomcatConfigurationTest.enablesJreMemoryLeakPrevention()` verifies its registration. The listener initializes JRE singletons with Tomcat's common class loader to reduce class-loader leaks during web application reloads. |
| 9.17 | Set the Security Lifecycle Listener | Not applicable to embedded Tomcat | This benchmark control modifies standalone `server.xml` and startup `umask` behavior. Set a restrictive container process umask and writable-volume permissions in the image/runtime instead. |
| 9.18 | Use `logEffectiveWebXml` and `metadata-complete` in production | Not applicable to embedded Tomcat | Spring Boot intentionally discovers components and security configuration through application code. Do not set `metadata-complete` without a full compatibility review; control component discovery through dependency and source review. |
| 9.19 | Encrypt Manager application passwords | Not applicable to embedded Tomcat | There is no `tomcat-users.xml` or Manager application. Store all production secrets in the selected secret manager and inject them with least privilege. |

## Deployment checklist

Before production use, the template adopter should at least:

1. Build a minimal, patched, non-root, immutable image; mount only narrowly scoped
   writable paths and protect deployment configuration and secrets.
2. Decide where TLS terminates. Enforce HTTPS at the public edge, configure trusted
   proxy forwarding when applicable, and provide production certificate/key material.
3. Set connector/request limits deliberately: exposed ports, connection timeout,
   request-header size, upload/body limits, and proxy timeouts.
4. Decide whether mTLS is required for machine clients; a CA bundle alone does not
   enable client-certificate authentication.
5. Collect the ECS standard-output logs centrally with protected access, retention,
   alerting, and loss detection; do not add secret-bearing request data to logs.
6. Re-run this crosswalk whenever Spring Boot, the JDK, Tomcat, the container image,
   ingress, or identity-provider topology changes.
