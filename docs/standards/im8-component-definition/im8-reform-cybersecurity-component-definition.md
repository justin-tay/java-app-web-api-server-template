---
title: IM8 Reform Cybersecurity Control Catalog Component Definition
component-definition:
  id: im8-reform-cybersecurity-component-definition
  imports:
    - ../im8-reform-cybersecurity-control-catalog.md
  component:
    name: java-app-web-api-server
    type: software
    title: Spring Boot Web API Server Template
---

# IM8 Reform Cybersecurity Control Catalog Component Definition

This is a [control implementation](../../adr/0003-control-implementation-terminology.md) for the [IM8 Reform Cybersecurity Control Catalog](../im8-reform-cybersecurity-control-catalog.md), styled after OSCAL's Component Definition model: for each control in the imported catalog, it records this template's implementation status and, where relevant, an implementation statement. As with the template's other control implementations, this remains a Markdown document rather than an OSCAL JSON/XML serialization; see [ADR 0003](../../adr/0003-control-implementation-terminology.md) for why the term is borrowed without adopting the format.

This template is one reusable software component, not a deployed system. Many catalog controls describe organisational processes (security programme management, human resource, incident response), physical or cloud infrastructure (datacentre, network, container orchestration, backup and recovery), or choices that belong to whichever identity provider, database, and hosting platform an adopter selects. This document only covers the Cybersecurity Control Catalog; the Digital Service Standards Control Catalog is not yet covered.

## Scope and status meanings

The five statuses below carry the same meaning as OSCAL's `implementation-status` vocabulary (`implemented`, `alternative`, `partial`, `planned`, `not-applicable`), written as prose here rather than as the standard's literal enum tokens.

| Status | Meaning |
| --- | --- |
| Implemented | The template's own code or configuration satisfies the control, including behaviour inherited from Spring Security, the JDK, or another dependency that has been verified and is not template-configurable. |
| Alternative | The control is satisfied through a different mechanism than the one the control statement describes, typically by requiring and integrating with an external identity provider (Keycloak) that this template does not implement itself but treats as a mandatory dependency. |
| Partial | The template provides part of what the control asks for; the remainder depends on a deployment or repository-configuration choice outside the template's code. |
| Planned | The control is one this template's own codebase or CI pipeline could reasonably satisfy, but does not yet; treated as a gap rather than a permanent exclusion. |
| Not applicable | The control addresses a concern outside this component's boundary: an organisational process, physical or cloud infrastructure, or a choice that belongs entirely to the adopter's deployment, identity provider, or repository configuration. |

For the underlying evidence, see [Authentication](../../system-design/08-crosscutting-concepts/02-security-and-authentication/authentication.md), [Authorization](../../system-design/08-crosscutting-concepts/02-security-and-authentication/authorization.md), [Sessions](../../system-design/08-crosscutting-concepts/02-security-and-authentication/sessions.md), [Headers](../../system-design/08-crosscutting-concepts/02-security-and-authentication/headers.md), [CIS Tomcat Benchmark control implementation](../../system-design/08-crosscutting-concepts/02-security-and-authentication/hardening.md), [Logging](../../system-design/08-crosscutting-concepts/06-logging-and-monitoring/README.md), [Error responses](../../system-design/08-crosscutting-concepts/02-security-and-authentication/error-responses.md), and [ASVS control implementation](../../system-design/08-crosscutting-concepts/02-security-and-authentication/asvs.md).

## Access Control

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| AC-1 | Principle of Least Privilege | Implemented | Access is deny-by-default (`authorizeHttpRequests`) and `@PreAuthorize` grants only the specific authority a function needs, based on the local `AppUser → AppGroup → AppRole` model. |
| AC-2 | Multi-Factor Authentication (MFA) | Alternative | MFA at login is delegated to Keycloak as the OIDC identity provider; the template has no login form or credential store of its own to apply MFA to. |
| AC-3 | Inactive and Expired Accounts | Partial | An optional scheduled job (`DormantUserDisabler`, [ADR 0028](../../adr/0028-user-last-login-and-dormant-account-disabling.md)) disables local users with no sign-in, creation, or administrative change for longer than `commons.accounts.dormancy.threshold`, ends their sessions, and audits the change. It is off until an adopter sets the threshold, and it does not detect account expiry dates, so the identity provider or governance process remains responsible for those. |
| AC-4 | Access Review | Not applicable | Periodic access review is an organisational process the adopter runs against the account and role data the template exposes through `AdministrationService`; the template does not schedule or perform reviews itself. |
| AC-5 | Endpoint Device Hardening | Not applicable | Endpoint hardening for administrator devices is outside an application component's boundary. |
| AC-6 | Default Credentials | Not applicable | The template ships no default account or credential to production; authentication is delegated to Keycloak. The development users are seeded only when the Liquibase `dev` context is requested, and the dev-only `jwks.json` fixture lives in `app-web-api-server/src/test/resources`, so it is not packaged, and `commons.security.oauth2.jwks` has no default (ADR 0018). |
| AC-7 | Singpass/Corppass for Public Users | Not applicable | The template is an internal/administrative API, not a Public User-facing digital service requiring Singpass/Corppass identity assurance. |
| AC-8 | Automated Account Lifecycle Management | Not applicable | Automated provisioning/deprovisioning tooling (SCIM or similar) is a deployment/identity-governance integration; the template exposes an administration API that such tooling could call, but does not implement the automation itself. |
| AC-9 | Endpoint Device Management | Not applicable | Endpoint device management is outside an application component's boundary. |
| AC-10 | Identity and Device-Based Access Control | Not applicable | Zero Trust network access (SSE/IAP) is a network/deployment layer concern, not something the application implements. |
| AC-11 | Single User Endpoints | Not applicable | Endpoint assignment policy is outside an application component's boundary. |
| AC-12 | Single Sign-On (SSO) for Internal Services and Accounts | Implemented | All access is authenticated through Keycloak via the OIDC authorization-code flow; the template has no independent local login path. |
| AC-13 | Static Credential Expiry and Rotation | Not applicable | The template issues no long-lived static credentials (API keys, access keys, personal access tokens) of its own; sessions and tokens are short-lived and managed by Spring Session and Keycloak. |
| AC-14 | Inventory of Accounts | Implemented | The local `AppUser`/`AppGroup`/`AppRole` tables, queried and managed through `AdministrationService`, are the account and access-rights inventory for this template's authorisation model. |
| AC-15 | Validation Testing of Automated Account Lifecycle Management | Not applicable | No automated account lifecycle tool is integrated by the template for this control to validate. |
| AC-16 | Separation of Duties | Partial | The RBAC model (groups, roles, authorities) supports defining separated administrative roles, but the template ships no predefined separation-of-duties policy; which roles are mutually exclusive is a product decision for the adopter. |

## Application Security

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| AS-1 | Input Validation | Implemented | Bean Validation constraints are applied to all admin request DTOs and entities; see ASVS V2/V5 rows for detail. |
| AS-2 | Parameterised Interfaces | Implemented | All queries use Spring Data JPA repository methods or the Criteria API via `Specification`; no native or concatenated query string is built from user input. |
| AS-3 | Output Sanitisation | Not applicable | The template renders no HTML document from user-controlled data; its one HTML surface (Spring Security's default login page) takes no user input, and all API responses are JSON serialised by Jackson. |
| AS-4 | Authentication Mechanism Rate-Limiting | Alternative | The template has no login form of its own to rate-limit; brute-force protection for the authentication mechanism is Keycloak's responsibility as the OIDC provider. |
| AS-5 | Password Requirements | Alternative | The template stores no passwords; password policy is enforced by Keycloak. |
| AS-6 | Password Salting and Hashing | Alternative | The template stores no passwords, so it performs no hashing; credential storage and hashing are Keycloak's responsibility. |
| AS-7 | Access Control Check Enforcement | Implemented | `authorizeHttpRequests` and `@PreAuthorize` apply to every authenticated request, and authorities are re-evaluated per request via `LocalAuthorityRefreshFilter` rather than cached from login. |
| AS-8 | Secrets Management | Partial | Secrets (TLS certificate, private key, CA bundle) are supplied via environment variables rather than hardcoded in source or configuration files, and the template does not integrate a secrets manager for them; choosing and operating one is a deployment decision. The private JWKS can be read from AWS Secrets Manager through the `commons-aws` module (`aws-secretsmanager:` locations in `commons.security.oauth2.jwks`). |
| AS-9 | Content Security Policy (CSP) | Implemented | A minimally permissive CSP (`default-src 'none'` plus explicit directives) is configured and verified by `WebSecurityConfigurationTest`. |
| AS-10 | HTTP Strict Transport Security (HSTS) | Implemented | Spring Security's default HSTS header (`max-age=31536000`, `includeSubDomains`), which meets the catalog's one-year minimum, is emitted over HTTPS and left unmodified. |
| AS-11 | Session Management | Implemented | Sessions enforce a 15-minute idle timeout, a 12-hour absolute timeout (`AbsoluteSessionTimeoutFilter`), and a single concurrent session per user (`maximumSessions(1)`), consistent with NIST SP 800-63B's re-authentication guidance. |
| AS-12 | Malware Scanning of Uploaded Files | Not applicable | The template has no file upload feature. |
| AS-13 | Exposure of Internal System Details | Implemented | Errors are returned as RFC 9457 Problem Details without stack traces or internal identifiers; see [Error responses](../../system-design/08-crosscutting-concepts/02-security-and-authentication/error-responses.md). |
| AS-14 | Secure Cryptographic Libraries | Implemented | Cryptographic operations use Nimbus JOSE+JWT for JWT/JWKS handling and the JDK's TLS stack; the template implements no custom cryptographic primitives. |
| AS-15 | Password Change | Not applicable | The template has no password management surface; enforcing a password change on suspected compromise is Keycloak's responsibility. |

## Backup and Recovery

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| BR-1 | Backup | Not applicable | Backing up data and systems is an operational/infrastructure responsibility of the deployment, not something an application component performs. |
| BR-2 | Recovery Testing | Not applicable | Deployment/operational responsibility. |
| BR-3 | Backup Retention | Not applicable | Deployment/operational responsibility. |
| BR-4 | Disaster Recovery Plan | Not applicable | Organisational/operational responsibility. |
| BR-5 | Business Continuity Plan | Not applicable | Organisational responsibility. |
| BR-6 | Business Continuity Exercise | Not applicable | Organisational responsibility. |

## Container Security

There is no Dockerfile or container image build in this repository, so the entire Container Security family is out of this component's current scope.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| CS-1 | Unique Base Container Image Tags | Not applicable | No Dockerfile exists in this repository. |
| CS-2 | Minimal Base Container Images | Not applicable | No Dockerfile exists in this repository. |
| CS-3 | Runtime Container Secrets | Not applicable | No Dockerfile exists in this repository. |
| CS-4 | Non-Privileged Container User | Not applicable | No Dockerfile exists in this repository. |
| CS-5 | Dockerfile Linting | Not applicable | No Dockerfile exists in this repository. |
| CS-6 | Read-Only Container Root Filesystem | Not applicable | No Dockerfile exists in this repository. |
| CS-7 | Container Image Scanning | Not applicable | No container image is built by this repository. |
| CS-8 | Private Container Image Registries | Not applicable | No container image is built or published by this repository. |
| CS-9 | Container Orchestrator API Access Control | Not applicable | Container orchestration is a deployment concern outside this repository. |
| CS-10 | Container Workload Segmentation | Not applicable | Deployment concern outside this repository. |
| CS-11 | Container Runtime Security | Not applicable | Deployment concern outside this repository. |

## Cryptography, Encryption and Key Management

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| CK-1 | Cryptographic Key Establishment | Implemented | TLS key establishment uses the JDK's TLS implementation restricted to TLS 1.2/1.3 with AEAD-only cipher suites (`TomcatHardeningAutoConfiguration`, `commons-defaults.yaml`); JWT operations use Nimbus JOSE+JWT's standard RS256/ES512/ES256 signature and `ECDH-ES+A128KW` key management algorithms. |
| CK-2 | Cryptographic Key Rotation | Partial | The JWKS signing and encryption keys are rotated by the `cdk-jwks-secret` Secrets Manager rotation (every 28 days by default), and `RefreshingJwks` reads the rotated keys again on a schedule without a restart ([ADR 0020](../../adr/0020-jwks-rotation-from-aws-secrets-manager.md)); deploying that rotation is a deployment decision. The TLS private key (`PRIVATE_KEY_PEM`) is operator-supplied and its rotation is not automated. |
| CK-3 | Cryptographic Key Management | Not applicable | Key lifecycle management (generation, storage, revocation) belongs to whichever KMS or keystore the deployment chooses; the template only consumes keys supplied to it. |
| CK-4 | Cryptographic Key Storage | Not applicable | The template reads key material from environment variables at startup; securely storing that material (e.g. in a KMS or vault) before it reaches the environment is a deployment decision. |

## Data Protection

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| DP-1 | Data Residency | Not applicable | Data residency is determined by the deployment's chosen hosting region, not the application code. |
| DP-2 | Data at Rest Encryption | Not applicable | The template's only persistent store is the Spring Session JDBC schema in an operator-supplied database; encrypting that database at rest is a deployment decision. |
| DP-3 | Data in Transit Encryption | Implemented | TLS 1.2/1.3 with AEAD-only cipher suites is enforced for all traffic (`TomcatHardeningAutoConfiguration`, `commons-defaults.yaml`). |
| DP-4 | Central Cloud Tenant Management | Not applicable | Cloud tenant structure is a deployment/organisational decision. |
| DP-5 | Sanitisation | Not applicable | Physical media sanitisation is an infrastructure/operational responsibility. |
| DP-6 | Witness Sanitisation and Destruction of Storage Devices | Not applicable | Infrastructure/operational responsibility. |
| DP-7 | Data Loss Prevention | Not applicable | DLP tooling operates at the organisation/network layer, outside this application component. |
| DP-8 | Data Classification Disclosure | Not applicable | The template has no user-facing input interface (it is an API), so there is no input field to annotate with a data classification notice. |

## Datacentre

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| DC-1 | Separate hosting | Not applicable | Physical hosting arrangement is outside an application component's boundary. |
| DC-2 | Physical Access Controls | Not applicable | Physical access control is outside an application component's boundary. |

## Generative AI

This template has no generative AI or large language model integration; the entire Generative AI family is not applicable.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| GA-1 | Overseas-hosted GenAI API services | Not applicable | The template has no GenAI integration. |
| GA-2 | Singapore-hosted GenAI API services | Not applicable | The template has no GenAI integration. |
| GA-3 | Non-logging and non-training Agreement | Not applicable | The template has no GenAI integration. |
| GA-4 | Data classification for self-hosted GenAI models | Not applicable | The template has no GenAI integration. |
| GA-5 | GenAI model formats and loaders | Not applicable | The template has no GenAI integration. |
| GA-6 | File upload safeguards | Not applicable | The template has no GenAI integration and no file upload feature. |
| GA-7 | Evaluation of GenAI accuracy, safety, and output quality | Not applicable | The template has no GenAI integration. |
| GA-8 | Inform users about GenAI risks and limitations | Not applicable | The template has no GenAI integration. |

## Human Resource

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| HR-1 | Security Awareness Training | Not applicable | Organisational/personnel process, outside an application component's boundary. |
| HR-2 | Security Screening of Employees | Not applicable | Organisational/personnel process. |
| HR-3 | Employee Termination Process | Not applicable | Organisational/personnel process. |

## Infrastructure Security

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| IS-1 | Management Agents | Not applicable | Host management is a deployment/infrastructure responsibility. |
| IS-2 | Automated Patch Management Tools | Not applicable | OS/host patching is a deployment/infrastructure responsibility; application dependency currency is covered separately under SD-5. |
| IS-3 | Restricted Administrator Privileges | Not applicable | Host administrator account configuration is outside an application component's boundary. |
| IS-4 | Least Functionality | Not applicable | Host port/protocol/service configuration is a deployment responsibility. |
| IS-5 | Host System Hardening | Not applicable | Host hardening is a deployment responsibility; the template's own embedded Tomcat hardening is covered by the [CIS Tomcat Benchmark control implementation](../../system-design/08-crosscutting-concepts/02-security-and-authentication/hardening.md). |
| IS-6 | Remote Administration | Not applicable | Host remote-administration tooling is a deployment responsibility. |
| IS-7 | Malware Protection | Not applicable | Host anti-malware tooling is a deployment responsibility. |
| IS-8 | Endpoint Detection and Response (EDR) | Not applicable | Host EDR tooling is a deployment responsibility. |
| IS-9 | End-of-Support (EOS) Assets | Not applicable | Tracking host/OS end-of-support is a deployment responsibility; keeping the template's own dependencies current is covered separately under SD-5. |
| IS-10 | Synchronise time clocks | Not applicable | Host clock synchronisation is a deployment/infrastructure responsibility. |
| IS-11 | Central Domain Name Registration | Not applicable | Domain registration is an organisational/deployment responsibility. |
| IS-12 | DNS Security Extensions (DNSSEC) | Not applicable | DNS configuration is a deployment responsibility. |
| IS-13 | Defensive Domain Name Registration | Not applicable | Domain registration is an organisational/deployment responsibility. |
| IS-14 | Singapore SMS Sender ID Registry Registration | Not applicable | The template has no SMS-sending feature. |

## Logging and Monitoring

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| LM-1 | Separate Log Storage | Not applicable | The template writes structured logs to stdout only; routing them to a separate storage system is a deployment/log-pipeline decision. |
| LM-2 | Tamper-Resistant Log Storage | Not applicable | Log storage protection is a deployment/log-pipeline decision. |
| LM-3 | Network Flow Logging | Not applicable | Network flow logging is an infrastructure responsibility. |
| LM-4 | Audit Logging | Implemented | Authentication, authorisation, and session lifecycle events, and every change to local accounts and access (users, groups, roles, and their memberships), are logged as structured audit events (`SecurityAuditEventLogger`, `SessionLifecycleAuditLogger`, `AdministrationAuditLogger`). |
| LM-5 | Database Logging | Not applicable | Database audit logging is owned by the operator-supplied database/infrastructure. |
| LM-6 | Access Logging | Implemented | Every request's lifecycle is logged via `RequestLoggingFilter`. |
| LM-7 | Host Security Event Logging | Not applicable | Host-level security event logging is an infrastructure responsibility. |
| LM-8 | Security Log Retention | Not applicable | The template does not persist logs itself; retention is a deployment/log-pipeline decision. |
| LM-9 | Security Monitoring and Alerting | Not applicable | Security monitoring/alerting tooling (e.g. a SIEM) is a deployment decision that consumes the template's structured logs. |
| LM-10 | Resource Usage Monitoring and Alerting | Not applicable | Resource usage monitoring/alerting is a deployment decision; the template exposes an Actuator health endpoint that such tooling can consume. |
| LM-11 | Service Level Monitoring and Alerting | Not applicable | SLO/SLI definition and alerting is a deployment decision; the template exposes an Actuator health endpoint (`/actuator/health` on a separate management port) as a building block. |
| LM-12 | Central Security Log Management and Monitoring | Not applicable | Centralised log management is a deployment/organisational decision. |
| LM-13 | Anomalous Database Activity Monitoring | Not applicable | Database activity monitoring is owned by the operator-supplied database/infrastructure. |
| LM-14 | Web Defacement Monitoring | Not applicable | The template serves no static or public-facing HTML content to deface. |
| LM-15 | Structured Log Formatting | Implemented | Logs are emitted as structured ECS (Elastic Common Schema) JSON; see the [logging schema](../../system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md). |
| LM-16 | Key Signals Monitoring | Not applicable | Golden-signal (latency, traffic, errors, saturation) monitoring is a deployment decision; the template's Actuator health endpoint and structured request logs are inputs such tooling can consume. |
| LM-17 | Software delivery performance monitoring | Not applicable | DORA metric tracking is an organisational/process decision; the template's CI workflow (`build-and-test.yml`) does not currently measure it. |
| LM-18 | Whole of Government Application Analytics (WOGAA) | Not applicable | The template is an internal/administrative API template, not a registered public-facing digital service. |
| LM-19 | Log Sanitisation | Implemented | Query parameters matching a redaction list are masked before logging, and request/response bodies, cookies, authentication headers, and passwords are never logged (`LoggingAutoConfiguration`). |
| LM-20 | User and Entity Behaviour Analytics | Not applicable | UEBA tooling is a deployment/SIEM decision. |
| LM-21 | Detection Updates | Not applicable | Malware/IOC detection signature updates are an infrastructure/EDR responsibility. |

## Network Security

Network boundary controls belong to the deployment's network and infrastructure layer; the entire family is not applicable to this application component, apart from the two rows below.

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| NS-1 | Network and System Component Segmentation | Not applicable | Network segmentation is an infrastructure/deployment responsibility. |
| NS-2 | Access Restrictions on CSP Resources Outside Virtual Network | Not applicable | Cloud resource access restriction is a deployment responsibility. |
| NS-3 | Deny by Default - Allow by Exception | Not applicable | Network ACL/security group configuration is a deployment responsibility. |
| NS-4 | Inter-Private Network Connectivity | Not applicable | Network topology is a deployment responsibility. |
| NS-5 | Network and Application Layer Filtering | Not applicable | WAF/DDoS/CDN placement is a deployment responsibility. |
| NS-6 | Valid and Trusted SSL/TLS Certificates | Not applicable | The template consumes an operator-supplied certificate, private key, and CA bundle (`server.ssl.bundle.pem`) and enforces TLS 1.2/1.3 with AEAD-only ciphers; obtaining, validating, and renewing a trusted certificate is a deployment responsibility. |
| NS-7 | Secure Inter-Service Communication | Partial | The template's one outbound call, from `AccountController` to Keycloak's account endpoint, is authenticated (bearer token) and encrypted (HTTPS) against a fixed, application-configured issuer URI; there is no other inter-service communication in the template to evaluate. |
| NS-8 | Secure Cloud and On-Premises Connectivity | Not applicable | Cloud/on-premises connectivity is a deployment responsibility. |
| NS-9 | Intrusion Prevention System (IPS)/Intrusion Detection System (IDS) | Not applicable | Network IPS/IDS is a deployment responsibility. |
| NS-10 | Private Network Connectivity | Not applicable | Remote access to private network resources is a deployment/infrastructure responsibility. |
| NS-11 | Alerts on Firewall Configuration Changes | Not applicable | Firewall configuration and alerting is a deployment responsibility. |

## Resiliency

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| RS-1 | Multi-AZ Deployment | Not applicable | Availability zone placement is a deployment decision. |
| RS-2 | Dynamic Resource Scaling | Not applicable | Autoscaling configuration is a deployment decision; the template externalises session state to a JDBC store, which supports horizontal scaling but does not itself configure it. |
| RS-3 | Load Testing | Not applicable | Load testing is a deployment/operational activity outside this component's own test suite. |

## Secure Development

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| SD-1 | Push Protection for Secrets | Not applicable | GitHub push protection is a repository-configuration setting outside the codebase; adopters are recommended to enable it on their fork. |
| SD-2 | Default Branch Push Permissions | Not applicable | Branch protection is a repository-configuration setting outside the codebase. |
| SD-3 | Continuous Integration (CI) Tests | Partial | `.github/workflows/build-and-test.yml` runs the full test suite (`mvnw verify`) on every pull request and push to `main`; requiring that check to pass before merge is a repository branch-protection setting outside this codebase. |
| SD-4 | Static Analysis | Planned | No SAST tool (e.g. SpotBugs, Checkstyle, SonarQube) is configured in `build-and-test.yml`; only automated formatting checks (`spring-javaformat-maven-plugin`) currently run. |
| SD-5 | Dependency Scanning | Planned | No dependency vulnerability scanner (e.g. OWASP Dependency-Check, Dependabot, Snyk) is configured for this repository. |
| SD-6 | Secret Detection | Planned | No secret-scanning tool (e.g. gitleaks, trufflehog) is configured in CI. |
| SD-7 | CI Environment Variable Secrets Management | Not applicable | The current CI workflow uses no secrets; if secrets are added, GitHub Actions' encrypted secrets with the default log-masking behaviour should be used. |
| SD-8 | Deployment Environment Segregation | Not applicable | Environment segregation is a deployment/infrastructure decision outside this repository. |
| SD-9 | Dynamic Analysis | Planned | No DAST/IAST tool is configured in CI. |
| SD-10 | Secure Software Development Lifecycle (SSDLC) | Partial | The repository documents security-relevant decisions through ADRs and control implementations (this document, [ASVS](../../system-design/08-crosscutting-concepts/02-security-and-authentication/asvs.md), [CIS Tomcat Benchmark](../../system-design/08-crosscutting-concepts/02-security-and-authentication/hardening.md)) as part of its development process, but does not name an adopted SSDLC framework such as NIST SSDF or OWASP SAMM. |

## Security Programme Management

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| PM-1 | Cybersecurity Incident Management Plan | Not applicable | Organisational/programme-level responsibility of the adopting agency, not the template. |
| PM-2 | Risk Assessment | Not applicable | Organisational/programme-level responsibility. |
| PM-3 | System Security Plan (SSP) Development | Not applicable | An SSP is system-specific; the template is a reusable component, not a deployed system with an SSP of its own. |
| PM-4 | Approval of Residual Risks | Not applicable | Organisational/programme-level responsibility. |
| PM-5 | Central Submission of Approved System Security Plan (SSP) | Not applicable | Organisational/programme-level responsibility. |
| PM-6 | System Documentation | Not applicable | Documentation of the deployed system as a whole is the adopter's responsibility; the template's own architecture is documented in its system design document. |
| PM-7 | Certification | Not applicable | The template is not a SaaS offering. |
| PM-8 | SaaS Whitelisting | Not applicable | Organisational/programme-level responsibility. |
| PM-9 | Cybersecurity Incident Response Testing | Not applicable | Organisational/programme-level responsibility. |
| PM-10 | Cybersecurity Leadership and Oversight | Not applicable | Organisational governance, outside an application component's boundary. |

## Security Testing

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| ST-1 | Vulnerability Assessment | Not applicable | Host/infrastructure vulnerability scanning is a deployment responsibility. |
| ST-2 | Cloud Security Posture Management | Not applicable | Cloud configuration scanning is a deployment responsibility. |
| ST-3 | Public Vulnerability Disclosure Programme | Planned | The repository does not yet publish a `security.txt` or `SECURITY.md` vulnerability reporting channel. |
| ST-4 | Security Testing Programme | Not applicable | Independent penetration testing is an organisational/programme-level activity, not something the template's own test suite performs. |
| ST-5 | Vulnerability Management | Not applicable | Vulnerability triage and remediation SLAs are an organisational process that presupposes a vulnerability scanning capability the template does not yet have (see SD-4, SD-5). |

## Software Supply Chain

| Control ID | Title | Status | Implementation Statement |
| --- | --- | --- | --- |
| SC-1 | Code Repository | Implemented | The codebase is managed in Git with full version history. |
| SC-2 | Commit Signing | Not applicable | Enforcing signed commits is a repository-configuration setting outside the codebase. |
| SC-3 | Peer Review | Not applicable | Enforcing peer review before merge is a repository branch-protection setting outside the codebase. |
| SC-4 | Dependency Manifest Version Pinning | Implemented | `pom.xml` pins exact dependency versions (via the Spring Boot parent BOM and explicit `<version>` elements); no version ranges are used. |
| SC-5 | Build and Release Process | Partial | `build-and-test.yml` provides a consistent, recorded CI build (Maven, JDK 17, coverage report) for every push and pull request, but the repository has no release/publish job producing a versioned, signed artefact. |
| SC-6 | Dependency Installation during Deployment | Implemented | Maven resolves the exact versions pinned in `pom.xml` on every build; the Maven Wrapper (`mvnw`) additionally pins the build tool's own version. |
| SC-7 | Software Artefact Signing | Planned | No artefact-signing step (e.g. Cosign, AWS Signer) exists, and the repository has no artefact-publishing pipeline yet. |
| SC-8 | Software Artefact Signature Verification | Not applicable | With no artefact signing in place (SC-7), there is no signature to verify. |
| SC-9 | Internal Code Collaboration and Sharing | Not applicable | This is a publicly published open-source template rather than an internal agency repository, so InnerSource practice does not apply in the same way. |
