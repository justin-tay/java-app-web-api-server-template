# Hardening

The CIS Apache Tomcat 11 Benchmark control implementation for the template maps
against the [CIS Apache Tomcat 11 Benchmark v1.1.0](https://www.cisecurity.org/benchmark/apache_tomcat).
It is a deployment planning aid, not a CIS conformance claim.

## Scope

The benchmark targets standalone Apache Tomcat 11 installed from Linux tar packages.
The template instead runs Spring Boot 4 with embedded Tomcat 11, which Tomcat ships as
a library inside the application jar. There is no `$CATALINA_HOME` or `$CATALINA_BASE`,
no `server.xml`, `context.xml`, or global `web.xml`, no Manager or Host Manager
application, no Catalina scripts, and no Tomcat Realm, so recommendations that audit
those files or applications cannot be assessed literally. Where embedded Tomcat keeps the
same setting on its `Connector`, `Context`, or `Server` objects, the row assesses that
setting instead.

Generic platform concerns such as runtime image minimization, operating-system
permissions, process identity, mount layout, and log collector protection are outside
this control implementation, except where a row needs them to name the route by which a
risk remains.

The template's Tomcat controls live in the shared commons module:
`TomcatHardeningAutoConfiguration` and `TomcatApplicationContextInitializer` apply the
connector, context, and server settings, and `commons-defaults.yaml` supplies the TLS and
Actuator settings. Every `app-*` module that depends on commons therefore inherits the
Implemented rows below. An application that sets `commons.web.tomcat.enabled=false`,
supplies its own `ServletWebServerFactory`, or overrides one of those defaults must
reassess the rows it affects.

The v1.1.0 benchmark has 59 recommendations in sections 1 through 9. Use the benchmark
that matches the deployed Tomcat major version, and an image or runtime scan for the
actual deployed version.

## Status meanings

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| Status | Meaning |
| --- | --- |
| Implemented | The template's own code or configuration fulfils the recommendation. |
| Inherited from framework | Met by an unmodified Spring Boot or embedded Tomcat default. A test that would catch a regression on upgrade is cited where one exists; otherwise the framework's behaviour is cited. |
| Alternative | A deliberate, documented approach used instead of the one the recommendation asks for; the row says what it gives up compared with the recommendation, so an adopter can decide whether that is acceptable for their system. |
| Not implemented | Relevant, and nothing in the template meets it yet: a gap. A pending decision is this status, named in the row. |
| Not applicable | The benchmark mechanism is absent from this embedded-Tomcat deployment shape. The underlying risk may not be; the row names any route by which it remains. Reassess before adding the mechanism. |
| Deployment responsibility | Belongs to whoever deploys the template: the container image, runtime, ingress, or an operational choice. |
<!-- /ocsv:generated -->

## 1. Remove extraneous resources

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 1.1 | Remove extraneous files and directories | Not applicable | Embedded Tomcat has no installation directory, so there are no `docs`, `examples`, `ROOT`, Manager, or Host Manager applications to remove. Extraneous content in the runtime image is the remaining route, covered by item 1 of the [Required production decisions](#required-production-decisions). |
| 1.2 | Disable unused connectors | Implemented | The application port has a single connector, HTTPS through `server.ssl`; the template adds no AJP or other connector. Spring Boot Actuator adds a second embedded server with its own plain-HTTP connector for the management port, deliberately (see [Actuator management port](#actuator-management-port)). The `local` and `test` profiles disable TLS for development only.<br><br>**Application configuration:** `server.port` and `server.ssl.bundle` in `application.yaml`, `server.ssl` and `management.server.port` in `commons-defaults.yaml`; **Deployment:** expose only the application and management ports at the container, firewall, and ingress. |
<!-- /ocsv:generated -->

## 2. Limit server-platform information leaks

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 2.1 | Alter the advertised `server.info` string | Alternative | Embedded Tomcat reads `server.info` from `ServerInfo.properties` inside the `tomcat-embed-core` jar. Instead of altering it, which means modifying a dependency jar, breaking its checksum and SBOM provenance, and repeating the change on every Tomcat patch, the template keeps the value from reaching clients: `X-Powered-By` is off (2.4), no `Server` header is sent (2.7), and Tomcat's HTML error report, whose footer shows the version, is replaced by a generic Problem Details response (2.5). Compared with altering the string, it gives up nothing on the HTTP surface, but the real version still appears in the startup log, so restrict who can read the logs.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotDiscloseTomcatOrApplicationServerInformation()`, `TomcatHardeningIntegrationTest.oversizedRequestHeaderDoesNotRevealTomcatHtmlErrorPage()`. |
| 2.2 | Alter the advertised `server.number` string | Alternative | The same approach as 2.1, for the `server.number` value in the same `ServerInfo.properties`, with the same trade-off.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotDiscloseTomcatOrApplicationServerInformation()`. |
| 2.3 | Alter the advertised `server.built` date | Alternative | The same approach as 2.1, for the `server.built` value in the same `ServerInfo.properties`, with the same trade-off.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotDiscloseTomcatOrApplicationServerInformation()`. |
| 2.4 | Disable `X-Powered-By` and replace the connector `server` value | Implemented | The connector has `X-Powered-By` disabled, leaves its `server` value unset so Tomcat emits no `Server` header, and sets `serverRemoveAppProvidedValues=true` to remove any `Server` header added by application code. The management port sends no `Server` header either.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotDiscloseTomcatOrApplicationServerInformation()`, `ActuatorManagementPortTest.doesNotDiscloseServerInformationOnTheManagementPort()`. |
| 2.5 | Disable client-facing stack traces | Implemented | Instead of a `web.xml` `<error-page>` for `java.lang.Throwable`, every error path returns a generic RFC 9457 Problem Details response with no stack trace: `TomcatProblemDetailErrorReportValve` for errors raised inside Tomcat, `ApiResponseEntityExceptionHandler` for Spring MVC errors, and `ProblemDetailErrorController` for any dispatch that bypasses both (see [ASVS V16.5.1](asvs.md#v165-error-handling) and [Error responses](error-responses.md)).<br><br>**Application code:** `TomcatHardeningAutoConfiguration.TomcatProblemDetailErrorReportValve`, `ApiResponseEntityExceptionHandler`, `ProblemDetailErrorController`; **Test code:** `TomcatHardeningIntegrationTest.oversizedRequestHeaderDoesNotRevealTomcatHtmlErrorPage()`, `ApiResponseEntityExceptionHandlerTest.unexpectedExceptionReturnsGenericProblemDetailAndLogsTheFailure()`. |
| 2.6 | Turn off TRACE | Implemented | The connector sets `allowTrace=false`, so Tomcat rejects TRACE requests before they reach the application.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.rejectsTraceRequests()`. |
| 2.7 | Remove the `Server` header | Implemented | Tomcat emits no `Server` header, rather than a substituted generic value, through the connector settings in 2.4.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotDiscloseTomcatOrApplicationServerInformation()`. |
<!-- /ocsv:generated -->

## 3. Protect the shutdown port

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 3.1 | Set a nondeterministic shutdown command | Not applicable | The shutdown command is only read on the shutdown port, which embedded Tomcat disables (see 3.2), so there is no command string to harden. |
| 3.2 | Disable the shutdown port | Inherited from framework | Embedded Tomcat creates its `Server` with the shutdown port set to `-1`, which disables it, and Spring Boot does not change it; the application stops when its process is signalled, not on a network command. No test asserts the port. The Actuator management port is a separate HTTP surface, not a shutdown port (see [Actuator management port](#actuator-management-port)).<br><br>**Framework default:** Apache Tomcat `Tomcat.getServer()`. |
<!-- /ocsv:generated -->

## 4. Protect Tomcat configurations

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 4.1 | Restrict access to `$CATALINA_HOME` | Not applicable | There is no Tomcat installation or `$CATALINA_HOME`. The remaining route is modification of the application jar and its dependencies in the runtime image; run as a non-root user with read-only application layers (item 1 of the [Required production decisions](#required-production-decisions)). |
| 4.2 | Restrict access to `$CATALINA_BASE` | Not applicable | There is no `$CATALINA_BASE`. Embedded Tomcat's only writable location is its temporary base directory (see 4.5). |
| 4.3 | Restrict access to the Tomcat configuration directory | Not applicable | There is no Tomcat configuration directory. Spring configuration and secrets are deployment inputs; restrict configuration mounts and environment-secret access to the service identity. |
| 4.4 | Restrict access to the Tomcat logs directory | Not applicable | There is no Tomcat logs directory: the template writes ECS JSON to standard output. Protecting the platform log sink is the remaining route (see [ASVS V16.4.2](asvs.md#v164-log-protection)). |
| 4.5 | Restrict access to the Tomcat temp directory | Deployment responsibility | Embedded Tomcat creates its base and work directories under `java.io.tmpdir` at startup, or under `server.tomcat.basedir` when that is set; the template sets neither. Ownership and permissions of that location belong to the container image or host.<br><br>**Deployment:** give the process a dedicated, non-shared writable temporary directory owned by its non-root user. |
| 4.6 | Restrict access to the Tomcat binaries directory | Not applicable | There is no Tomcat binaries directory: Tomcat is a dependency jar inside the application jar. Protect the container image and dependency supply chain, and do not permit runtime modification of application binaries. |
| 4.7 | Restrict access to the Tomcat web application directory | Not applicable | There is no web application directory. Package the application immutably, and do not mount a writable application directory. |
| 4.8 | Restrict access to `catalina.properties` | Not applicable | There is no `catalina.properties`. Treat external Spring configuration and JVM options as protected deployment configuration. |
| 4.9 | Restrict access to `context.xml` | Not applicable | There is no `context.xml`; the context is configured in code by `TomcatHardeningAutoConfiguration`. |
| 4.10 | Restrict access to `logging.properties` | Not applicable | There is no Tomcat JULI `logging.properties`; Spring Boot configures logging. |
| 4.11 | Restrict access to `server.xml` | Not applicable | There is no `server.xml`; the connector is configured through Spring Boot properties and `TomcatHardeningAutoConfiguration`. |
| 4.12 | Restrict access to `tomcat-users.xml` | Not applicable | There is no Tomcat user database or Manager application; users authenticate through OIDC (see 5.1). |
| 4.13 | Restrict access to `web.xml` | Not applicable | There is no `web.xml`; the application is configured in Java. |
| 4.14 | Restrict access to `jaspic-providers.xml` | Not applicable | There is no `jaspic-providers.xml`, and no JASPIC provider is registered. |
<!-- /ocsv:generated -->

## 5. Configure Realms

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 5.1 | Use secure Realms | Not applicable | The template configures no Tomcat Realm; Spring Security authenticates users through OIDC with Keycloak, as mapped in [Authentication](authentication.md).<br><br>**Application code:** `WebSecurityAutoConfiguration.securityFilterChainCustomizer()`. |
| 5.2 | Use LockOut Realms | Not applicable | No Tomcat Realm exists to wrap in a `LockOutRealm`. Credentials are checked by Keycloak, whose brute-force detection is the equivalent control; the development realm does not enable it (see [ASVS V6.3.1](asvs.md#v63-general-authentication-security)). |
<!-- /ocsv:generated -->

## 6. Connector security

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 6.1 | Set up client-certificate authentication | Not implemented | Client-certificate authentication is not enabled: `server.ssl.client-auth` is unset, and the `CA_BUNDLE_PEM` trust store alone requests no client certificate. Whether machine clients need mTLS, at the application or at the proxy, is a pending decision, item 4 of the [Required production decisions](#required-production-decisions).<br><br>**Application configuration:** `spring.ssl.bundle.pem.server.truststore` in `application.yaml`. |
| 6.2 | Enable SSL for sensitive connectors | Implemented | The application port's connector has TLS enabled, with the certificate and key from `CERTIFICATE_PEM` and `PRIVATE_KEY_PEM`. The `local` and `test` profiles set `server.ssl.enabled: false` for development and must not be deployed. The management port's connector is plain HTTP by design (see 9.11).<br><br>**Application configuration:** `server.ssl.bundle` and `spring.ssl.bundle.pem.server` in `application.yaml`, `server.ssl` in `commons-defaults.yaml`; **Deployment:** supply production key material and activate neither development profile. |
| 6.3 | Set the connector `scheme` accurately | Inherited from framework | When TLS is enabled, Spring Boot sets the connector's `scheme` to `https`; the plain-HTTP management connector keeps Tomcat's default `http`. No test asserts the value.<br><br>**Framework default:** Spring Boot `SslConnectorCustomizer`; **Deployment:** if TLS terminates at a proxy, configure and restrict forwarded-header handling so redirects, cookies, and the OIDC callback URL see the external `https` scheme. |
| 6.4 | Set `secure` only on SSL-enabled connectors | Inherited from framework | When TLS is enabled, Spring Boot sets the connector's `secure` flag to `true`; the plain-HTTP management connector keeps Tomcat's default `false`. No test asserts the value.<br><br>**Framework default:** Spring Boot `SslConnectorCustomizer`; **Deployment:** review the flag whenever TLS termination moves to a proxy. |
| 6.5 | Configure the TLS protocol for secure connectors | Implemented | The connector enables only TLS 1.2 and TLS 1.3, with an explicit list of AES-GCM cipher suites (see [ASVS V12.1](asvs.md#v121-general-tls-security-guidance)). Reassess the list when the JDK, embedded Tomcat, or the organization's TLS baseline changes.<br><br>**Application configuration:** `server.ssl.enabled-protocols` and `server.ssl.ciphers` in `commons-defaults.yaml`. |
<!-- /ocsv:generated -->

## 7. Establish and protect logging facilities

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 7.1 | Application-specific logging | Implemented | The application has its own logging configuration, Spring Boot structured logging in Elastic Common Schema JSON, instead of a per-application JULI `logging.properties`. Its events are documented in [Logging and Monitoring](../06-logging-and-monitoring/README.md) and the [Logging schema](../06-logging-and-monitoring/schema.md).<br><br>**Application configuration:** `logging.structured` in `commons-defaults.yaml`; **Decision:** [ADR 0010](../../../adr/0010-ecs-structured-logging.md). |
| 7.2 | Specify file handlers in `logging.properties` | Not applicable | There is no JULI `logging.properties`, and the template writes no log file: logs go to standard output for platform collection (see [ASVS V16.2.3](asvs.md#v162-general-logging)). |
| 7.3 | Set the access-log valve `className` in `context.xml` | Alternative | Access logging comes from `RequestLoggingFilter`, which writes `receive_request` and `complete_request` ECS events, instead of Tomcat's `AccessLogValve`. Compared with the valve, it gives up a record of requests Tomcat rejects before the filter chain runs, such as TRACE, an oversized header, or a malformed request line, which the valve would still log. Configure access logs at the ingress or proxy to keep that record.<br><br>**Application code:** `RequestLoggingFilter`, `LoggingAutoConfiguration`; **Test code:** `RequestLoggingFilterTest.logsRequestLifecycleUsingSanitizedUrlAndAuthenticatedUser()`. |
| 7.4 | Use a secure access-log directory in `context.xml` | Not applicable | No access-log valve writes a directory; request events go to standard output with the rest of the log (see 7.3). |
| 7.5 | Use the required access-log pattern in `context.xml` | Alternative | The request events carry the equivalent of the benchmark's pattern fields: `source.ip`, `@timestamp`, `http.request.method`, `url.path`, the redacted `url.query`, and `http.response.status_code`. They deliberately omit the session ID cookie and attribute that the pattern asks for, because session IDs are never logged; session events carry a separate random audit identifier instead (see [ASVS V16.2.5](asvs.md#v162-general-logging)). Compared with the pattern, it gives up the protocol version and correlation of requests by session ID.<br><br>**Application code:** `RequestLoggingFilter`, `RequestCorrelationContextFilter`, `SessionLifecycleAuditLogger`; **Decision:** [ADR 0008](../../../adr/0008-session-lifecycle-audit-identifiers.md). |
| 7.6 | Use a secure log directory in `logging.properties` | Not applicable | There is no JULI file logging and no log directory (see 7.2). Log retention, encryption, and access control belong to the platform log pipeline (see [ASVS V16.4.2](asvs.md#v164-log-protection)). |
<!-- /ocsv:generated -->

## 8. Application deployment

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 8.1 | Disable automatic deployment of applications | Inherited from framework | Spring Boot sets the embedded host's `autoDeploy` to `false`, and the template's `TomcatServletWebServerFactory` subclass keeps that behaviour; Spring Boot adds the application's one context in code. No test asserts the flag.<br><br>**Framework default:** Spring Boot `TomcatWebServerFactory`; **Deployment:** deliver immutable application images and restrict who can deploy them. |
| 8.2 | Disable deployment of applications on startup | Not applicable | `deployOnStartup` is read only by the host deployer that standalone Tomcat's `server.xml` registers, which embedded Tomcat does not create, so nothing is deployed from a directory at startup. The application jar carries its only context. |
<!-- /ocsv:generated -->

## 9. Miscellaneous configuration settings

<!-- ocsv:generated source="cis:apache-tomcat-11:1.1.0" source-ref="1.1.0" code-ref="029cc18" -->
| CIS ID | CIS intent | Status | Implementation Statement |
| --- | --- | --- | --- |
| 9.1 | Put web content on a separate partition from Tomcat system files | Not applicable | There are neither Tomcat system files nor a web content directory to separate. Mount and volume layout is a platform concern. |
| 9.2 | Restrict access to the web administration application | Not applicable | The Tomcat Manager is not packaged. Restrict cloud and orchestrator administration interfaces separately. |
| 9.3 | Restrict the Manager application | Not applicable | There is no Manager application and no `manager-*` role. |
| 9.4 | Force SSL for the Manager application | Not applicable | There is no Manager application. |
| 9.5 | Rename the Manager application | Not applicable | There is no Manager application. Renaming a path is not an access control for any other administration interface. |
| 9.6 | Enable strict servlet compliance | Implemented | `TomcatApplicationContextInitializer`, registered in the commons `spring.factories`, sets `org.apache.catalina.STRICT_SERVLET_COMPLIANCE=true` before Spring creates embedded Tomcat. Keep integration coverage for redirects, sessions, and filters when adding servlet libraries, which strict compliance can affect.<br><br>**Application code:** `TomcatApplicationContextInitializer`; **Test code:** `TomcatHardeningIntegrationTest.enablesStrictServletComplianceBeforeEmbeddedTomcatStarts()`, `TomcatHardeningAutoConfigurationTest.initializerEnablesStrictServletComplianceByDefault()`. |
| 9.7 | Turn off session facade recycling | Implemented | The connector sets `discardFacades=true`, so Tomcat discards request and response facade objects after every request.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.discardsRequestAndResponseFacadesAfterEachRequest()`. |
| 9.8 | Disallow additional path delimiters | Implemented | The benchmark's audit names the JVM flags `CoyoteAdapter.ALLOW_BACKSLASH` and `UDecoder.ALLOW_ENCODED_SLASH`, which Tomcat 11 replaces with connector settings; the connector sets `allowBackslash=false`, `encodedSolidusHandling=reject`, and `encodedReverseSolidusHandling=reject`. After an upgrade, test the deployed proxy and application together, because an edge can normalize paths before Tomcat sees them.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.rejectsAdditionalPathDelimiters()`. |
| 9.9 | Configure `connectionTimeout` | Deployment responsibility | `server.tomcat.connection-timeout` is unset, so the connector keeps Tomcat's default of 60 seconds. The benchmark asks for a value chosen for the hardware, load, and concurrent connections, which only the deployment knows; align it with the load balancer's idle timeout.<br><br>**Framework default:** Apache Tomcat `connectionTimeout`; **Deployment:** set `server.tomcat.connection-timeout` (item 3 of the [Required production decisions](#required-production-decisions)). |
| 9.10 | Configure `maxHttpHeaderSize` | Inherited from framework | Spring Boot's default `server.max-http-request-header-size` of 8 KB sets the connector's `maxHttpHeaderSize` to 8192 bytes, the benchmark's value; the template does not override it. A request with an oversized header is rejected with a generic 400 Problem Details response.<br><br>**Framework default:** Spring Boot `ServerProperties`; **Test code:** `TomcatHardeningIntegrationTest.oversizedRequestHeaderDoesNotRevealTomcatHtmlErrorPage()`; **Deployment:** keep proxy and ingress header limits consistent with it. |
| 9.11 | Force SSL for all applications | Alternative | Instead of a `transport-guarantee` of `CONFIDENTIAL`, which redirects plain HTTP to HTTPS, the application port has no plain-HTTP listener at all and the session cookie is `Secure`. The Actuator management port deliberately serves its health check over plain HTTP, relying on network restriction to the load balancer and internal operations network. Compared with the recommendation, it gives up transport encryption for health-check traffic, which carries only `{"status":"UP"}` (see [Actuator management port](#actuator-management-port) and [ASVS V12.3.1](asvs.md#v123-general-service-to-service-communication-security)).<br><br>**Application configuration:** `server.ssl`, `server.servlet.session.cookie.secure`, and `management.server.port` in `commons-defaults.yaml`; **Decision:** [ADR 0014](../../../adr/0014-actuator-management-port.md); **Deployment:** enforce HTTPS at the public edge, and configure trusted forwarded headers if TLS terminates upstream. |
| 9.12 | Disallow symbolic linking | Implemented | The context's web resources set `allowLinking=false`. Review any custom `WebResourceSet` added later, which can override the root setting.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.disallowsSymbolicLinksInWebApplicationResources()`. |
| 9.13 | Do not run applications as privileged | Implemented | The context sets `privileged=false`.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.doesNotRunTheWebApplicationAsPrivileged()`. |
| 9.14 | Disallow cross-context requests | Implemented | The context sets `crossContext=false`. Reassess if several web applications are ever hosted in one JVM.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatSecurityHardening()`; **Test code:** `TomcatHardeningIntegrationTest.disallowsCrossContextRequests()`. |
| 9.15 | Do not resolve host names in logging | Inherited from framework | The connector's `enableLookups` keeps Tomcat's default of `false`, which Spring Boot does not change, so the container performs no reverse DNS lookup. The template's own request logging records `source.ip` and `client.ip` as literal addresses, and its client-IP resolvers reject any value that is not a literal IP before parsing it, so a header value never triggers a DNS lookup. No test asserts `enableLookups`.<br><br>**Framework default:** Apache Tomcat `Connector` `enableLookups`; **Application code:** `RequestCorrelationContextFilter`, `TrustedProxyMatcher`; **Test code:** `TrustedProxyMatcherTest`. |
| 9.16 | Enable the memory-leak listener | Implemented | `JreMemoryLeakPreventionListener` is added to the embedded Tomcat `Server` before it is initialized.<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatServletWebServerFactory()`; **Test code:** `TomcatHardeningIntegrationTest.enablesJreMemoryLeakPrevention()`. |
| 9.17 | Set the Security Lifecycle Listener | Not implemented | `SecurityListener` ships in embedded Tomcat and could be added to the `Server` the way 9.16's listener is, but the template does not add it. Its check that the process is not running as a forbidden OS user, `root` by default, would work embedded; its `umask` check reads a system property that only the Catalina startup scripts set, so it would only log a warning. Whether to add it, or rely on the image's non-root user and `umask` alone, is a pending decision, item 1 of the [Required production decisions](#required-production-decisions).<br><br>**Application code:** `TomcatHardeningAutoConfiguration.tomcatServletWebServerFactory()`. |
| 9.18 | Use `logEffectiveWebXml` and `metadata-complete` in production | Not applicable | There is no `web.xml` to mark `metadata-complete` or to log: Spring Boot registers the application's servlets and filters in code. Control component discovery through dependency and source review. |
| 9.19 | Encrypt Manager application passwords | Not applicable | There is no `tomcat-users.xml` or Manager application. |
<!-- /ocsv:generated -->

## Actuator management port

Spring Boot Actuator runs its own embedded server on a separate port
(`management.server.port: 8082`, set in `commons-defaults.yaml`), distinct from the
application port (`8081`), so an ALB or equivalent load balancer has an HTTP health
check without reaching the rest of the application. It is a Spring Boot HTTP surface,
not Tomcat's shutdown port (see 3.2), and the benchmark has no recommendation for it.

Only `health` is exposed (`management.endpoints.web.exposure.include: health`) and it
hides all detail (`show-details` and `show-components: never`), so an unauthenticated
request returns only `{"status":"UP"}`. Endpoints are served under `/app` rather than
the default `/actuator` base path, so an unauthenticated scan does not immediately
fingerprint the framework; this is obscurity, not a control, and does not reduce the
network-restriction requirement below. `/app/health` is the only Actuator path
permitted by `WebSecurityAutoConfiguration`; the port stays plain HTTP because network
restriction, not TLS, is its security boundary (see 9.11).

The template cannot guarantee that restriction: the management port must be
network-restricted (security group or NACL) to the load balancer's health-check path and
any internal operations network, and must never share a listener or target group with
the application port. Adding any other endpoint (`metrics`, `info`, `env`, and so on)
needs its own authentication mechanism for the management port first; see
[ADR 0014](../../../adr/0014-actuator-management-port.md) for the full rationale, including
two easy-to-get-backwards behaviours this port has because it shares Spring Security's
filter chain with the application while running as a separate embedded server.

## Required production decisions

Before production use, the template adopter must record and implement
decisions for:

1. Build a minimal, patched, non-root, immutable image; mount only narrowly scoped
   writable paths, protect deployment configuration and secrets, and decide whether to
   add Tomcat's `SecurityListener` (9.17) on top of the image's non-root user.
2. Decide where TLS terminates. Enforce HTTPS at the public edge, configure trusted
   proxy forwarding when applicable, and provide production certificate and key material.
3. Set connector and request limits deliberately: exposed ports, connection timeout,
   request-header size, upload and body limits, and proxy timeouts.
4. Decide whether mTLS is required for machine clients; a CA bundle alone does not
   enable client-certificate authentication.
5. Collect the ECS standard-output logs centrally with protected access, retention,
   alerting, and loss detection, and keep ingress access logs for requests rejected
   before the application (7.3); do not add secret-bearing request data to logs.
6. Re-run this control implementation whenever Spring Boot, the JDK, Tomcat, the
   container image, ingress, or identity-provider topology changes.
7. Restrict the Actuator management port (`8082`) to the load balancer's health-check
   path and any internal ops network only; never route it through the same listener
   or target group as the application port. See
   [ADR 0014](../../../adr/0014-actuator-management-port.md).
