# HTTP security headers

The response-header posture of the application, as of Spring Boot 4.1 /
Spring Security 7, is recorded here against the
[OWASP HTTP Security Response Headers Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html).

The effective headers at the public edge can also be changed by a reverse proxy,
load balancer, CDN, or servlet container; this table records the application
configuration, not an assertion about infrastructure configuration. Review
this matrix whenever `WebSecurityConfiguration`, an application endpoint, the
browser UI, or edge infrastructure changes, confirming both authenticated and
unauthenticated responses, redirects, errors, downloads, and the externally
deployed HTTPS endpoint.

## OWASP HTTP headers control implementation

This section follows the [HTTP Security Response Headers Cheat
Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html)'s
own "Security Headers" subsection headings, in its own order. "Introduction"
is skipped as motivational rather than actionable. "Adding HTTP Headers in
Different Technologies" (PHP, Apache, IIS, HAProxy, Nginx, Express),
"Testing Proper Implementation of Security Headers" (Mozilla Observatory,
SmartScanner), and "References" are also skipped: none of the listed
technologies is this Java/Spring application, and the testing/references
sections are tooling pointers and a bibliography, not requirements to map
against.

### Status meanings

| Status | Meaning |
| --- | --- |
| Implemented | The application explicitly sets this header. |
| Verify framework behavior | The header is supplied by a Spring Security default rather than an application setting. Where the header is observable on a response, keep an integration test that would catch a regression on upgrade. Where it is a language, JVM, or container guarantee with no accessible seam to test through, cite the framework's or JDK's documented behavior instead and recheck it after upgrades. |
| Verification required | The header is relevant, but the base template has no sufficient product-specific evidence. The adopter must design, implement, and test it. |
| Deployment decision required | The effective value depends on infrastructure or an operational choice the template cannot safely make. |
| Not applicable | The template does not have the capability, use case, or role the header addresses. Reassess before introducing one. |

### X-Frame-Options

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Prevents a page from being embedded in a frame, mitigating clickjacking in older clients. | Prefer CSP `frame-ancestors`; also deny framing where applicable. | `DENY` | Verify framework behavior | Spring Security's default `XFrameOptionsHeaderWriter` (`DENY`); the application does not override it. CSP also contains `frame-ancestors 'none'` (see Content-Security-Policy (CSP) below), which is the primary modern control. Primarily relevant to the generated HTML login page, not JSON responses. Covered by `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. |

### X-XSS-Protection

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Controls legacy browser XSS filters. | Do not enable it; omit it or send `0`, because legacy filters can introduce vulnerabilities. | `0` | Verify framework behavior | Spring Security default `XXssProtectionHeaderWriter` sends `0`; not explicitly configured by the application. CSP is the applicable modern mitigation. Covered by `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. |

### X-Content-Type-Options

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Prevents MIME-type sniffing. | `nosniff`, with correct `Content-Type` on every response. | `nosniff` | Verify framework behavior | Spring Security default `ContentTypeOptionsHeaderWriter`; not explicitly configured by the application. Covered by `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. Correct `Content-Type` itself is tracked separately below. |

### Referrer-Policy

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Limits referrer information sent by browsers. | `strict-origin-when-cross-origin`. | `strict-origin-when-cross-origin` | Implemented | Explicitly set by `WebSecurityConfiguration.securityFilterChain()` using `ReferrerPolicyHeaderWriter`. Covered by `WebSecurityConfigurationTest.responseHasReferrerAndPermissionsPolicies()`. |

### Content-Type

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Ensures the browser interprets the body as the type the server intends, rather than sniffing it. | Every response must declare an accurate, specific `Content-Type` (for a JSON API, `application/json`). | `application/json` on every API response. | Implemented | Every REST endpoint declares `produces = MediaType.APPLICATION_JSON_VALUE` (for example `JwksController.jwks()`, `LoginUserController.loginUser()`, `AccountController.account()`); Spring MVC's message converter sets the header from that declaration. `X-Content-Type-Options: nosniff` above prevents the browser from second-guessing it. |

### Cache-Control

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Controls browser and intermediary caching of responses. | Use `no-store` for sensitive data; do not rely on default caching behaviour for protected data. | `no-cache, no-store, max-age=0, must-revalidate`, with companion legacy headers `Pragma: no-cache` and `Expires: 0`. | Verify framework behavior | Spring Security default `CacheControlHeadersWriter` sets all three headers together; the application does not override it. It is appropriate for authenticated account and token-adjacent responses. Review any future large public/static responses separately. Covered by `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. |

### Set-Cookie

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Establishes the session cookie; its attributes protect session confidentiality and integrity. | Apply secure cookie attributes; OWASP refers to the Session Management Cheat Sheet for the full guidance. | Session cookie name `id`; `HttpOnly`; `SameSite=Lax`; `Secure=true` outside the `local` and `test` profiles. | Implemented | Set in `src/main/resources/application.yaml` under `server.servlet.session.cookie`. `application-local.yaml` and `application-test.yaml` set `secure: false` so HTTP tests and local development work. Production deployments must use HTTPS. The Spring Session implementation writes the actual `Set-Cookie` header. Full attribute-by-attribute mapping: [Sessions](sessions.md#cookies). |

### Strict-Transport-Security (HSTS)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Tells browsers to use HTTPS for later requests. | `max-age=63072000; includeSubDomains; preload`, after carefully validating HTTPS and the operational consequences. | Spring Security default: `max-age=31536000 ; includeSubDomains`, emitted only for secure requests. | Deployment decision required | Not overridden in `WebSecurityConfiguration`. Spring Security's `HstsHeaderWriter` does not emit the header over HTTP, so it is absent in the `local` and `test` profiles. If TLS terminates at a proxy, ensure forwarded-request handling makes the application see the request as secure, or set HSTS at the edge. Decide whether the OWASP duration, `preload`, and subdomain scope are appropriate; do not enable preload casually. |

### Expect-CT

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Formerly opted into Certificate Transparency enforcement/reporting. | Do not use it. | Not set. | Not applicable | No application configuration writes this header. This is intentional; the header is obsolete and browsers no longer act on it. |

### Content-Security-Policy (CSP)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Restricts sources from which a browser may load or execute content, reducing XSS and injection impact. | Use a CSP tailored to the application; it is most useful for rendered content and can be less meaningful for a JSON-only API. | `base-uri 'none'; default-src 'none'; form-action 'none'; frame-ancestors 'none'` | Implemented | Explicitly set by `WebSecurityConfiguration.securityFilterChain()` through `headers.contentSecurityPolicy(...)`. This restrictive policy fits the API and Spring-generated login/logout pages only while they load no external assets or scripts. Update it before adding a browser UI or third-party assets. Covered by `WebSecurityConfigurationTest.responseHasContentSecurityPolicy()`. |

### Access-Control-Allow-Origin

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Relaxes the browser same-origin policy for a response. | If CORS is needed, allow explicit origins rather than `*` unless the resource is deliberately public. | Not set. | Not applicable | No CORS configuration is enabled in `WebSecurityConfiguration` (no `CorsConfigurationSource` bean, no `.cors(...)` customizer); browsers therefore retain the same-origin default. Add a narrowly scoped Spring MVC/Spring Security CORS policy only for a defined cross-origin client. |

### Cross-Origin-Opener-Policy (COOP)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Isolates the top-level browsing context from cross-origin documents. | `same-origin` when browser isolation is required. | Not set. | Verification required | No application configuration. Evaluate it with the UI and OAuth redirect flow before enabling it; it is generally not useful for JSON API responses. |

### Cross-Origin-Embedder-Policy (COEP)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Requires cross-origin resources to explicitly permit embedding. | `require-corp` when the application needs cross-origin isolation. | Not set. | Verification required | No application configuration. It can block valid third-party resources, so enable only after integration testing a browser UI. |

### Cross-Origin-Resource-Policy (CORP)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Limits which origins can load a resource. | `same-site` for resources that should be limited to the site and subdomains. | Not set. | Not applicable | No application configuration. Assess per resource; this API currently does not serve public browser assets. |

### Permissions-Policy (formerly Feature-Policy)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Limits browser features available to a document or embedded frame. | Set it and disable features not required by the site, or allow them only for authorised origins. | `camera=(), geolocation=(), microphone=(), payment=(), usb=()` | Implemented | Explicitly set by `WebSecurityConfiguration.securityFilterChain()` using `permissionsPolicyHeader(...)`. Add capabilities only after establishing a concrete product requirement and the permitted origins. Covered by `WebSecurityConfigurationTest.responseHasReferrerAndPermissionsPolicies()`. |

### Server

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Can disclose the origin server software. | Remove it or use a non-informative value. | Not configured by application code. | Deployment decision required | `WebSecurityConfiguration` does not write this header, and `TomcatConfiguration` does not set `Server` either. The effective value is container/proxy/CDN dependent and must be removed or normalised at the deployment edge if it is emitted. |

### X-Powered-By

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Can disclose implementation technology. | Remove it. | Not set by the application. | Verify framework behavior | Spring MVC/Spring Security do not add this header. Ensure a reverse proxy, platform, or gateway does not add it. Its absence is covered by `WebSecurityConfigurationTest.responseHasSpringSecurityDefaultHeaders()`. |

### X-AspNet-Version

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Discloses ASP.NET version information. | Disable it. | Not applicable; not set. | Not applicable | This is a Java/Spring application, not ASP.NET; there is no code path that could emit this header. Verify that an upstream component does not introduce it. |

### X-AspNetMvc-Version

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Discloses ASP.NET MVC version information. | Disable it. | Not applicable; not set. | Not applicable | Same as X-AspNet-Version above: this is a Java/Spring application, not ASP.NET MVC. |

### X-Robots-Tag

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Directs compliant search-engine indexing behaviour. | Use `noindex, nofollow` for private content; use `index, follow` for deliberately public content. | Not set. | Not applicable | No application configuration. Authenticated endpoints require authentication, which is the access control; add this header at the application or edge only if crawler-indexing policy is required for an exposed endpoint. It is not an access-control mechanism. |

### X-DNS-Prefetch-Control

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Controls browser DNS prefetching from document links and resources. | Set `off` if uncontrolled links could leak DNS requests; do not rely on it for sensitive security decisions. | Not set. | Not applicable | No application configuration. This API has no application-rendered links; reassess if an HTML UI is introduced. |

### Public-Key-Pins (HPKP)

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Legacy HTTP public-key pinning. | Do not use; rely on Certificate Transparency and CAA instead. | Not set. | Not applicable | No application configuration writes `Public-Key-Pins` or `Public-Key-Pins-Report-Only`. This is intentional because HPKP is obsolete and operationally hazardous. |

### Secure File Download Headers

| Purpose | OWASP cheat sheet recommendation | Current configured value | Status | Implementation Statement |
| --- | --- | --- | --- | --- |
| Forces download rather than browser rendering of user-supplied files; pairs `Content-Disposition: attachment` with `application/octet-stream` and `nosniff` for unknown/binary content. | Use `attachment`; use `application/octet-stream` for unknown/binary content; retain `nosniff`. | Not set globally. | Not applicable | The current controllers (`UserAdminController`, `GroupAdminController`, `RoleAdminController`, `AccountController`, `LoginUserController`, `JwksController`) serve only JSON; none returns a file body. Set this header explicitly on any future download endpoint, based on the file's trust and rendering requirements. |

These headers complement, but do not replace, TLS, session-cookie protection, CSRF
protection, OpenID Connect token validation, authentication, authorisation, input
validation, and security controls configured at the reverse proxy or CDN.

## Required production decisions

Before production use, the template adopter must record and implement
decisions for:

1. `Strict-Transport-Security`: whether the OWASP-recommended `max-age`,
   `preload`, and subdomain scope are appropriate, after carefully validating
   HTTPS and the operational consequences of `preload`.
2. `Server`: removing or normalising it at the deployment edge, since the
   effective value is container/proxy/CDN dependent and not written by the
   application.

