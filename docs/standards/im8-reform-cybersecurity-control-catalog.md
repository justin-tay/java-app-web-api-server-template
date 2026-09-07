---
title: IM8 Reform Cybersecurity Control Catalog
catalog:
  id: im8-reform-cybersecurity-control-catalog
  version: 2026-03-26
  source: https://info.standards.tech.gov.sg/control-catalog/cybersecurity/
---

# IM8 Reform Cybersecurity Control Catalog

> Source: [Singapore Government ICT&SS Policy Reform — Cybersecurity Control Catalog](https://info.standards.tech.gov.sg/control-catalog/cybersecurity/). Extracted 7 September 2026. The source catalog was last updated 26 March 2026; individual control families may have different update dates.

This single-file representation follows an OSCAL/Trestle-inspired hierarchy: catalog, control group, control, statement, recommendations, risk statement, and parameters. It preserves the source wording and parameter placeholders.

## Access Control

Controls to protect against unauthorised access to agency systems.

### AC-1: Principle of Least Privilege

**Group:** Access Control

#### Control Statement

Deny access by default and grant only the minimum permissions required for authorised accounts or processes to perform a specific function based on the account inventory implemented.

#### Control Recommendations

Consider attribute- or feature-based access control for greater customisability and granularity. Use automated tools such as AWS IAM Access Advisor or Azure AD Access Review to assist with granular permission management.

#### Risk Statement

Violating the principle of least privileges increases the risk of unauthorised access, privilege escalation, and potential security breaches due to unnecessary permissions, compromising the overall security posture.

### AC-2: Multi-Factor Authentication (MFA)

**Group:** Access Control

#### Control Statement

Require MFA for privileged accounts at login.

#### Control Recommendations

Ensure that the authentication factors are different and independent of the accessing device. For additional security, consider MFA for privileged actions at the application level (such as step-up MFA challenges via PIM tools).

#### Risk Statement

Without requiring phishing-resistant Multi-Factor Authentication (MFA) for remote access, there is an increased risk of unauthorised access, credential theft, and potential compromise of sensitive systems, especially for accounts with elevated privileges.

### AC-3: Inactive and Expired Accounts

**Group:** Access Control

#### Control Statement

Disable or remove [ insert: param, ac-3_prm_3 ] accounts within [ insert: param, ac-3_prm_1 ] day(s) from last day of authorised use or have not been used for [ insert: param, ac-3_prm_2 ] day(s).

#### Control Recommendations

Use automated checks to identify accounts and credentials that should be disabled. Consider using automated workflows such as System for Cross-domain Identity Management (SCIM) or identity lifecycle management tools. For cloud service provider accounts, use tools such as AWS Config iam-user-unused-credentials-check to manage Identity and Access Management (IAM) Users.

#### Risk Statement

Failure to disable or remove unused accounts or credentials with elevated access increases the risk of unauthorised access, as dormant accounts may become targets for exploitation, compromising the security of the system.

#### Parameters

AC-3 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ac-3_prm_1 | time period (days) (int) | The time period in days after account expiry. |
| ac-3_prm_2 | time period (days) (int) | The time period in days of account inactivity. |
| ac-3_prm_3 | type (str) | The type of accounts. |

### AC-4: Access Review

**Group:** Access Control

#### Control Statement

Perform an access review [ insert: param, ac-4_prm_1 ] and remove unauthorised or unnecessary access rights within [ insert: param, ac-4_prm_2 ] day(s).

#### Control Recommendations

For application accounts, implement automated review workflows or reports. For cloud service provider accounts and roles, use tools such as AWS IAM Access Advisor or Azure AD Access Review to facilitate and manage access reviews.

#### Risk Statement

Without regular access reviews and prompt removal of unauthorised or unnecessary access rights, there is an increased risk of lingering access, potential misuse of privileges, and compromised security, impacting the confidentiality and integrity of sensitive data.

#### Parameters

AC-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ac-4_prm_1 | organisation-defined frequency (str) | The access review frequency. |
| ac-4_prm_2 | time period (days) (int) | The time period in days for access removal. |

### AC-5: Endpoint Device Hardening

**Group:** Access Control

#### Control Statement

Require hardened endpoint devices for remote developer, maintainer, or administrator access.

#### Control Recommendations

Use Endpoint Management platforms to continuously check and enforce device security posture and deny access if the hardening requirements are not met.

#### Risk Statement

Without requiring hardened endpoint devices for remote access, there's an increased risk of compromised endpoints, potential malware infections, and security breaches, which could lead to unauthorised access and compromise the integrity of systems.

### AC-6: Default Credentials

**Group:** Access Control

#### Control Statement

Change default credentials prior to first use.

#### Control Recommendations

Identify any default credentials used in any system components before deploying and change them. Configure end-user systems to prompt for password change on first login after account creation or reset.

#### Risk Statement

Failure to change default credentials prior to first use increases the risk of unauthorised access, as default credentials are often well-known and targeted by attackers, compromising the security of the system or device.

### AC-7: Singpass/Corppass for Public Users

**Group:** Access Control

#### Control Statement

Use Singpass or Corppass MFA for digital services that require high level of identity assurance for Public Users.

#### Control Recommendations

For high impact or high risk transactions, use Singpass/Corppass to identify Public Users (e.g. citizens). Agency or internal Users should use Government managed Single Sign-on (SSO) solutions (such as WOG AAD).

#### Risk Statement

Leverage on Singpass or Corppass to reduce duplication of effort and provide consistent end user experience.

### AC-8: Automated Account Lifecycle Management

**Group:** Access Control

#### Control Statement

Automate account [ insert: param, ac-8_prm_1 ] for [ insert: param, ac-8_prm_3 ] using an account lifecycle management tool.

#### Control Recommendations

Consider adopting Single Sign-On (SSO) with just-in-time provisioning or account lifecycle management protocols or tools such as [ insert: param, ac-8_prm_2 ]. Perform validation testing of the integration between systems and tools to ensure that accounts are provisioned and/or deprovisioned in a timely manner. Where applicable, configure the system to enhance management capabilities via automation.

#### Risk Statement

Manual account and access lifecycle management can introduce errors and weaknesses, thus making access control measures ineffective and unreliable.

#### Parameters

AC-8 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ac-8_prm_1 | process (str) | The account lifecycle management processes to automate. |
| ac-8_prm_2 | tool (str) | Recommended account lifecycle management tool. |
| ac-8_prm_3 | type (str) | The type of accounts. |

### AC-9: Endpoint Device Management

**Group:** Access Control

#### Control Statement

Implement and maintain an endpoint device management solution to ensure the security and integrity of endpoint devices used within the organisation.

#### Control Recommendations

Mobile Device Management (MDM) platforms enable management, monitoring, and secure configuration of endpoint devices. This includes enforcing disk encryption, managing configuration, ensuring regular updates, and providing the ability to remotely wipe data in case of device loss or theft.

#### Risk Statement

Unmanaged endpoint devices increase the risk of unauthorised access and potential loss of sensitive information due to the compromise of devices.

### AC-10: Identity and Device-Based Access Control

**Group:** Access Control

#### Control Statement

Adopt Identity and Device-Based Access Control for secure and context-aware connectivity to private organisational resources.

#### Control Recommendations

Use solutions such as Secure Service Edge (SSE), Identity Aware Proxies (IAP) or other Zero Trust services (Entra ID Conditional Access, Okta Device Trust, etc) that integrate identity and device management systems to provide granular access control to resources based on user identity and device posture.

#### Risk Statement

Relying on direct connections or traditional VPNs for remote access can lead to vulnerabilities, as they do not always incorporate strong identity and device-based security measures. This increases the risk of unauthorised access and potential data breaches.

### AC-11: Single User Endpoints

**Group:** Access Control

#### Control Statement

Assign each endpoint device to a single designated primary [ insert: param, ac-11_prm_1 ] and enforce the assignment to ensure accountability and enhance security monitoring.

#### Control Recommendations

Implement measures such as user authentication and endpoint management with device enrolment to enforce the single primary user per endpoint. If secondary accounts for local device support or maintenance activities are used, consider securing them with endpoint privilege management tools.

#### Risk Statement

Allowing multiple users to access a single endpoint device can lead to security risks such as data leakage, difficulty in tracking user activities, and increased vulnerability to insider threats.

#### Parameters

AC-11 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ac-11_prm_1 | type (str) | The type of user or identity. |

### AC-12: Single Sign-On (SSO) for Internal Services and Accounts

**Group:** Access Control

#### Control Statement

Use Single Sign-On (SSO) for internal services and accounts.

#### Control Recommendations

Configure multi-factor authentication (MFA) at the Single-Sign On (SSO) identity provider (IdP) and ensure that access to the system is only granted after the IdP authenticates the user.

#### Risk Statement

Without Single Sign-On (SSO), there is an increased risk of unauthorised access and compromised user credentials, as users may resort to using weak passwords or reusing credentials across multiple systems, thereby exposing sensitive information to potential security breaches.

### AC-13: Static Credential Expiry and Rotation

**Group:** Access Control

#### Control Statement

Rotate long-lived static credentials such as API keys, access keys, and personal access tokens every [ insert: param, ac-13_prm_1 ] day(s) or use time-restricted credentials.

#### Control Recommendations

Automate credential rotation where possible. Consider time-restricted alternatives to long-lived static credentials, such as AWS Security Token Service and IAM Identity Center authentication instead of IAM user access keys.

#### Risk Statement

Failure to regularly rotate long-lived credentials or use time-restricted credentials increases the risk of unauthorised access from stolen or unrevoked credentials.

#### Parameters

AC-13 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ac-13_prm_1 | time period (days) (int) | The time period in days for credential rotation. |

### AC-14: Inventory of Accounts

**Group:** Access Control

#### Control Statement

Establish and maintain an inventory of all accounts and their access rights managed within the system.

#### Control Recommendations

Regularly review and update the account inventory to ensure accuracy and completeness. Implement automated tools where feasible to assist in tracking and managing accounts and their access rights.

#### Risk Statement

Failure to maintain an accurate inventory of managed accounts increases the risk of unauthorised access, account misuse, and security breaches due to unmonitored or orphaned accounts.

### AC-15: Validation Testing of Automated Account Lifecycle Management

**Group:** Access Control

#### Control Statement

Conduct validation tests on the system integrated with account management tools to ensure secure integration.

#### Control Recommendations

Where possible, test cases should include verifying that: account provisioning occurs solely through the account management tool(s), not directly on the SaaS; accounts are deactivated on the final day of authorised use; accounts are provisioned only after validating that access is permitted to the defined boundaries; and access rights match the account's assigned role and functions.

#### Risk Statement

Failure to conduct validation tests on the integration of account management tools with SaaS platforms increases the risk of unauthorised access, improper account provisioning, and potential security breaches.

### AC-16: Separation of Duties

**Group:** Access Control

#### Control Statement

Implement and maintain separation of duties for access and privileges to prevent any single individual from having excessive control over key processes or systems.

#### Control Recommendations

Implement multiple complementary controls to enforce separation of duties, including but not limited to role-based access control (RBAC), Privileged Identity Management (PIM), and Just-In-Time (JIT) access. Where perfect separation is not feasible, use compensating controls such as audit logging, alerting, and rate limits. Consider using tools like Azure AD Privileged Identity Management or AWS Organizations for managing separation of duties in cloud environments.

#### Risk Statement

Failure to implement separation of duties increases the risk of fraud, errors, and misuse of systems due to excessive concentration of privileges, potentially compromising the integrity and security of critical operations.

## Application Security

Controls to prevent application vulnerabilities caused by insecure coding.

### AS-1: Input Validation

**Group:** Application Security

#### Control Statement

Validate all application inputs to ensure that they match the expected type, structure, or format.

#### Control Recommendations

Strictly validating inputs against a comprehensive schema prevents injection attacks caused by inserting special characters or content that would cause the application to perform incorrect operations.

#### Risk Statement

Without input validation, there's a heightened risk of injection attacks, data manipulation, or system crashes due to unexpected input, potentially leading to unauthorised access or disruption of services.

### AS-2: Parameterised Interfaces

**Group:** Application Security

#### Control Statement

Use parameterised interfaces for database queries or system commands.

#### Control Recommendations

Parameterised interfaces such Object-Relational Mapping (ORM) libraries ensure that parameters used in database queries or system commands are properly sanitised and prevent injection attacks.

#### Risk Statement

Failure to use parameterised interfaces increases the vulnerability to SQL injection or command injection attacks, posing a significant risk of unauthorised access, data manipulation, or even potential system compromise.

### AS-3: Output Sanitisation

**Group:** Application Security

#### Control Statement

Sanitise all application outputs that will be used to render a HTML document.

#### Control Recommendations

Any application outputs that are returned to the requester and used to render a HTML document can lead to cross-site scripting (XSS) attacks if they contain special characters that change the rendering of the HTML document by the browser.

#### Risk Statement

Lack of sanitisation for application outputs used in rendering HTML documents exposes the system to the risk of cross-site scripting (XSS) attacks, allowing malicious code execution in browsers.

### AS-4: Authentication Mechanism Rate-Limiting

**Group:** Application Security

#### Control Statement

Apply rate-limiting on all authentication mechanisms to deter brute-force attacks.

#### Control Recommendations

Consider rate-limiting to a maximum of 3 consecutive failed authentication attempts within 15 minutes or other reasonable rate limits. Time delays between log-on attempts reduce the risk of successful brute-forcing attacks. Bot mitigation tools such as CAPTCHA can further reduce this risk.

#### Risk Statement

Without rate-limiting, there's an increased risk of unauthorised access as attackers may exploit weak credentials through repeated login attempts.

### AS-5: Password Requirements

**Group:** Application Security

#### Control Statement

Where SSO or passwordless is not supported, verify that [ insert: param, as-5_prm_3 ] passwords are at least [ insert: param, as-5_prm_1 ] characters in length and [ insert: param, as-5_prm_2 ].

#### Control Recommendations

Latest NIST [SP 800-63B](https://doi.org/10.6028/NIST.SP.800-63b) guidelines found that password length is a primary factor in determining the strength of a password while composition and complexity rules provide marginal security benefits.

#### Risk Statement

Short or commonly used passwords increase the vulnerability to unauthorised access, potentially leading to compromised accounts and unauthorised activities on the system.

#### Parameters

AS-5 Parameters

| ID | Type | Description |
| --- | --- | --- |
| as-5_prm_1 | number of characters (int) | The minimum length of a password. |
| as-5_prm_2 | policy (str) | The password policy. |
| as-5_prm_3 | type (str) | The type of password. |

### AS-6: Password Salting and Hashing

**Group:** Application Security

#### Control Statement

Store passwords as salted hashes using a password hashing scheme that is resistant to offline attacks such as those described in NIST [SP 800-63b](https://doi.org/10.6028/NIST.SP.800-63b) . The salt should be:

- Generated using a cryptographically secure pseudo-random number generator in accordance with industry standards;
- At least 32 bits long; and
- Randomly generated for each account.

#### Control Recommendations

Refer to NIST [SP 800-90Ar1](https://doi.org/10.6028/NIST.SP.800-90Ar1) for suitable pseudo-random number generators. Refer to NIST [SP 800-63b](https://doi.org/10.6028/NIST.SP.800-63b) Memorized Secret Verifiers section for suitable hashing schemes, including Argon2, scrypt, and PBKDF2. For application source code, use a cryptographically secure pseudo-random number generator function instead of an insecure one, such as crypto.randomBytes instead of Math.random in Node.js and [java.security](http://java.security) .SecureRandom.nextBytes instead of java.util.Random in Java.

#### Risk Statement

Without salting and hashing, in case of a data breach, exposed passwords can be easily extracted, leading to potential compromise of accounts and sensitive information.

### AS-7: Access Control Check Enforcement

**Group:** Application Security

#### Control Statement

Perform access control checks on all authenticated requests.

#### Control Recommendations

Utilise authorisation filters or middleware to force all authenticated requests to undergo access control checks.

#### Risk Statement

Failure to perform access control checks on authenticated requests increases the risk of unauthorised access to sensitive data or functionalities, potentially leading to data breaches and misuse of system resources.

### AS-8: Secrets Management

**Group:** Application Security

#### Control Statement

Securely store secrets in an appropriate secrets management solution with access control enforcement, encryption, and monitoring.

#### Control Recommendations

Secrets include API keys, access keys, and other static credentials. Do not store secrets unencrypted in source code or configuration files. Store secrets in cloud-native solutions like AWS Secrets Manager and Azure Key Vault or cloud-agnostic solutions like HashiCorp Vault and CyberArk Conjur. For SaaS or platforms, ensure that secrets are stored in an appropriate solution. For example, use GitHub Actions secrets instead of variables.

#### Risk Statement

Exposure of sensitive information and unauthorised access to system credentials may occur if application secrets are stored insecurely or hard-coded in source code.

### AS-9: Content Security Policy (CSP)

**Group:** Application Security

#### Control Statement

Set minimally permissive CSP response headers to mitigate cross-site scripting attacks.

#### Control Recommendations

Utilise the relevant fetch directives such as default-src, script-src, style-src, connect-src, img-src, media-src and object-src to prevent loading of scripts from malicious sources. Refer to the [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers) Best Practices for recommended header values.

#### Risk Statement

Without minimally permissive Content Security Policy (CSP) headers, the risk of cross-site scripting attacks, leading to unauthorised script execution and potential data theft, is increased.

### AS-10: HTTP Strict Transport Security (HSTS)

**Group:** Application Security

#### Control Statement

Set HTTP Strict Transport Security (HSTS) response headers with a maximum age value of at least 1 year (31536000 seconds) to mitigate protocol downgrade attacks.

#### Control Recommendations

Refer to the [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers) Best Practices for recommended header values.

#### Risk Statement

Failure to implement HTTP Strict Transport Security (HSTS) with a sufficient maximum age may expose the system to protocol downgrade attacks, compromising the security of communication channels.

### AS-11: Session Management

**Group:** Application Security

#### Control Statement

Require [ insert: param, as-11_prm_2 ] to re-authenticate after their session exceeds [ insert: param, as-11_prm_1 ] hour(s) or terminate the session.

#### Control Recommendations

NIST SP 800-63B recommends re-authentication once per 30 days for Authenticator Assurance Level 1, 12 hours or 30 minutes inactivity for Authenticator Assurance Level 2, and 12 hours or 15 minutes inactivity for Authenticator Assurance Level 3. In addition to time period, system can consider re-authentication when roles, authenticators or credentials change or when the execution of privileged functions occurs.

#### Risk Statement

Not re-authenticating regularly and at suitable checkpoints could allow someone who has access to the account to carry out unauthorised actions.

#### Parameters

AS-11 Parameters

| ID | Type | Description |
| --- | --- | --- |
| as-11_prm_1 | time period (hours) (int) | The maximum time period in hours of a user's session. |
| as-11_prm_2 | type (str) | The type of session. |

### AS-12: Malware Scanning of Uploaded Files

**Group:** Application Security

#### Control Statement

Scan file uploads for malware before further processing by the system.

#### Control Recommendations

Consider uploading the files to temporary storage for malware scanning on ephemeral compute like serverless functions before moving safe files to another storage for further processing or unsafe files to quarantine storage.

#### Risk Statement

Without scanning uploaded files for malware, there's an increased risk of exploits or infection for consumers of the files.

### AS-13: Exposure of Internal System Details

**Group:** Application Security

#### Control Statement

Prevent the unnecessary disclosure of internal system details to Public Users.

#### Control Recommendations

Ensure all system messages and notifications are informative yet secure. These messages should be contextually appropriate, providing relevant information without exposing internal system details such as debug information, stack traces, or software versioning.

#### Risk Statement

Disclosure of internal system details or debug stack traces can expose vulnerabilities, software versions, and system architecture, potentially leading to targeted attacks, exploitation of known vulnerabilities, and unauthorised access to sensitive systems or data.

### AS-14: Secure Cryptographic Libraries

**Group:** Application Security

#### Control Statement

Use reputable and secure cryptographic libraries and functions to handle cryptographic operations.

#### Control Recommendations

Follow the OWASP Cryptographic Storage Cheat Sheet for best practices in securely implementing cryptographic operations. Regularly update libraries and prefer widely recognised ones such as OpenSSL. Consider using libraries that are FIPS 140-2 or FIPS 140-3 compliant for enhanced security assurance.

#### Risk Statement

Using insecure cryptographic libraries and functions can expose applications to significant security risks, such as data breaches and unauthorised access, compromising sensitive information.

### AS-15: Password Change

**Group:** Application Security

#### Control Statement

Enforce password change upon suspected account compromise.

#### Control Recommendations

Implement mechanisms to detect signs of account compromise, such as unusual login activity or multiple failed login attempts. Notify users promptly to change their passwords and provide guidance on creating strong, unique passwords. Consider integrating multi-factor authentication to enhance security.

#### Risk Statement

Failure to enforce password changes upon suspected account compromise increases the risk of prolonged unauthorised access, leading to potential data breaches and exploitation of systems.

## Backup and Recovery

Controls to support backup and disaster recovery.

### BR-1: Backup

**Group:** Backup and Recovery

#### Control Statement

Backup all important data and systems at least every [ insert: param, br-1_prm_1 ] day(s), and store backups in a secure and separate location.

#### Control Recommendations

Use default CSP-managed backup services (e.g., AWS Backup, Azure Backup, GCP Backup and DR Service). Consider alternative backup services only when default CSP services cannot be used. Store backups and snapshots separately to primary data storage with data encrypted-at-rest. For SaaS, ensure that SaaS provider is able to restore last backup.

#### Risk Statement

Without regular backups stored in a secure and separate location, there is an increased risk of data loss, system failures, and extended downtime in the event of accidental deletion, hardware failures, or malicious attacks.

#### Parameters

BR-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| br-1_prm_1 | time period (days) (int) | The time period in days for backup. |

### BR-2: Recovery Testing

**Group:** Backup and Recovery

#### Control Statement

Conduct testing of recovery processes at least every [ insert: param, br-2_prm_1 ] day(s) to ensure their effectiveness.

#### Control Recommendations

Ensure each test verifies the system's ability to fully restore all data and services.

#### Risk Statement

Failure to regularly test recovery processes may result in ineffective response during actual incidents, increasing the risk of prolonged downtime, data loss, and compromised business continuity in the event of a disaster or system failure.

#### Parameters

BR-2 Parameters

| ID | Type | Description |
| --- | --- | --- |
| br-2_prm_1 | time period (days) (int) | The time period in days for recovery testing. |

### BR-3: Backup Retention

**Group:** Backup and Recovery

#### Control Statement

Prevent backups from being modified or deleted for [ insert: param, br-3_prm_1 ] day(s) or as stipulated in the agency's data retention policies.

#### Control Recommendations

Use S3 Object Lock or Azure Blob Storage with immutability enabled to enforce time-based retention policies.

#### Risk Statement

Lack of prevention measures against the modification or deletion of backups for the specified duration increases the risk of data loss, unauthorised alterations, and potential inability to recover from incidents, compromising the integrity and availability of critical information.

#### Parameters

BR-3 Parameters

| ID | Type | Description |
| --- | --- | --- |
| br-3_prm_1 | time period (days) (int) | The time period in days of backup retention. |

### BR-4: Disaster Recovery Plan

**Group:** Backup and Recovery

#### Control Statement

Develop, maintain, and regularly test a disaster recovery plan that ensures critical functions and data can be restored within the Recovery Time Objective (RTO) and Recovery Point Objective (RPO) as determined by business requirements and risk assessments.

#### Control Recommendations

The disaster recovery plan should include clearly defined roles and responsibilities, step-by-step recovery procedures, communication protocols during a disaster, and integration with the overall business continuity plan. Assess the effectiveness of the existing disaster recovery plan during disaster recovery exercises.

#### Risk Statement

Absence of a comprehensive disaster recovery plan increases the risk of prolonged system downtime, data loss, and inability to maintain business continuity in the event of a disaster, potentially leading to significant financial losses and damage to organisational reputation.

### BR-5: Business Continuity Plan

**Group:** Backup and Recovery

#### Control Statement

Develop a business continuity plan.

#### Control Recommendations

Refer to the [Cyber Security Agency's Cybersecurity Toolkit for IT Teams](https://www.csa.gov.sg/our-programmes/support-for-enterprises/sg-cyber-safe-programme/cybersecurity-toolkits/cybersecurity-toolkit-for-it-teams) for a business continuity plan template.

#### Risk Statement

Failure to develop a Business Continuity Plan may result in prolonged system downtime, loss of critical data, and diminished organisational resilience during disruptive events, increasing the risk of financial and reputational damage.

### BR-6: Business Continuity Exercise

**Group:** Backup and Recovery

#### Control Statement

Conduct a business continuity exercise at least every [ insert: param, br-6_prm_1 ] day(s) to test the effectiveness of the business continuity plan.

#### Control Recommendations

Conduct testing based on the business continuity plan which can include checklists, tabletop exercises, and simulations.

#### Risk Statement

Failure to conduct regular business continuity exercises may lead to unpreparedness during an actual disruption, increasing response times, operational impact, and the risk of ineffective recovery, ultimately jeopardising business operations and stakeholder trust.

#### Parameters

BR-6 Parameters

| ID | Type | Description |
| --- | --- | --- |
| br-6_prm_1 | time period (days) (int) | The time period in days of business continuity exercise frequency. |

## Container Security

Controls to secure container building, distribution, and deployment.

### CS-1: Unique Base Container Image Tags

**Group:** Container Security

#### Control Statement

Use unique base container image tags instead of rolling tags.

#### Control Recommendations

Avoid the latest tag or other common rolling tags for base images to minimise unintended changes during subsequent builds using the same instruction. A digest SHA can provide a unique identifier for the image if no tag is assigned during build time.

#### Risk Statement

Using unique base container image tags instead of rolling tags reduces the risk of unintentional updates, inconsistencies, and potential security vulnerabilities in containerised environments, ensuring a more stable and secure deployment process.

### CS-2: Minimal Base Container Images

**Group:** Container Security

#### Control Statement

Build container images with minimal base images.

#### Control Recommendations

Use minimal container images such as alpine, scratch, wolfi, and distroless images as the base image to reduce attack surface.

#### Risk Statement

Building container images with minimal base images reduces the attack surface, potential vulnerabilities, and resource overhead, minimising the risk of security exploits and enhancing the overall security posture of the containerised environment.

### CS-3: Runtime Container Secrets

**Group:** Container Security

#### Control Statement

Provide secrets and sensitive data to the container at runtime instead of image build time.

#### Control Recommendations

Ensure no secrets (e.g., TLS certificate keys, cloud provider credentials, SSH private keys, database passwords) are embedded in the container image by using dedicated features like Docker secrets or podman-secret-create.

#### Risk Statement

Providing secrets and sensitive data to the container at runtime instead of image build time reduces the risk of exposing sensitive information in the image and enhances security by ensuring that secrets are managed and updated independently, minimising the risk of unauthorised access or data compromise.

### CS-4: Non-Privileged Container User

**Group:** Container Security

#### Control Statement

Create a non-root user and set it as the default user in the container image build instructions.

#### Control Recommendations

Ensure the non-root user has the minimal set of permissions required to run the container.

#### Risk Statement

Failure to create a non-root user and set it as the default user in container image build instructions increases the risk of security vulnerabilities, as running containers with root privileges may lead to potential exploitation and compromise of the host system.

### CS-5: Dockerfile Linting

**Group:** Container Security

#### Control Statement

Lint Dockerfiles before building container images.

#### Control Recommendations

Use linters such as Hadolint to check the Dockerfile (or similar build file) instructions and flag any issues that contravene best practices. Ensure Dockerfile linting stage is run as part of the Continuous Integration (CI) pipelines.

#### Risk Statement

Without linting Dockerfiles before building container images, there's an increased risk of syntax errors, misconfigurations, and potential security vulnerabilities, compromising the reliability and security of the resulting containerised applications.

### CS-6: Read-Only Container Root Filesystem

**Group:** Container Security

#### Control Statement

Configure the container root filesystem to be read-only during runtime, except for designated non-persistent storage locations or mounted volumes for stateful storage (e.g., caches, service data).

#### Control Recommendations

Use security policies (e.g., readonlyRootFilesystem for Kubernetes) to prevent any direct writes to the container's root filesystem during runtime and ensure immutable infrastructure. Do not directly apply patches or alter running containers as the containers are ephemeral and patches will disappear upon redeploy. Apply patches by rebuilding and redeploying container images. Use tmpfs mounts to mount a temporary file system.

#### Risk Statement

Failure to configure the container filesystem as read-only increases the risk of unauthorised modifications, potential tampering, and compromise of containerised applications, as attackers may exploit write access to alter the container's state and integrity.

### CS-7: Container Image Scanning

**Group:** Container Security

#### Control Statement

Scan container images in the [ insert: param, cs-7_prm_1 ] for known vulnerabilities.

#### Control Recommendations

Container image scanning tools (e.g., Amazon Inspector, Trivy, Grype) scan the contents of a container image for known vulnerabilities. Configure scans to run automatically and continuously, as well as enable scanning of image on push. Block the deployment of container images that have critical or high severity vulnerabilities detected during the scan (e.g., using Amazon ECR with Security Hub).

#### Risk Statement

Failure to scan container images increases the risk of deploying insecure images, potentially exposing the infrastructure to known exploits and compromising the security of the containerised applications during runtime.

#### Parameters

CS-7 Parameters

| ID | Type | Description |
| --- | --- | --- |
| cs-7_prm_1 | location (str) | The location where container image scanning occurs. |

### CS-8: Private Container Image Registries

**Group:** Container Security

#### Control Statement

Host built container images in private container registries.

#### Control Recommendations

Use only private container registries (e.g., Amazon ECR private registry) to host container images built by the organisation as images may contain proprietary code or sensitive information.

#### Risk Statement

Hosting built container images in private registries enhances security by reducing the exposure of sensitive images, minimising the risk of unauthorised access, and maintaining control over image distribution, ensuring a more secure and controlled container deployment process.

### CS-9: Container Orchestrator API Access Control

**Group:** Container Security

#### Control Statement

Disable public access to Container Orchestrator API endpoints from the internet.

#### Control Recommendations

Restrict access to the Container Orchestrator API endpoints (such as the Kubernetes API Server) to specific address ranges or use CSP provided features such as disabling Endpoint public access and Private Clusters to disable public access.

#### Risk Statement

Failure to disable public access to Container Orchestrator API endpoints from the internet increases the risk of unauthorised access, potential exploitation, and security breaches, as exposing these endpoints publicly may lead to unauthorised control and compromise of the container infrastructure.

### CS-10: Container Workload Segmentation

**Group:** Container Security

#### Control Statement

Segregate container workloads to help contain attacks through isolation.

#### Control Recommendations

Create Kubernetes namespaces or similar container segmentation controls to isolate different workloads, services or projects.

#### Risk Statement

Without separating container workloads into namespaces, there's an increased risk of lateral movement and potential compromise.

### CS-11: Container Runtime Security

**Group:** Container Security

#### Control Statement

Detect and remediate changes to running containers with container runtime protection tools.

#### Control Recommendations

Runtime protection tools, such as AWS EKS Protection, Microsoft Defender for Containers, or Falco, monitor threats and changes to running containers. Vulnerable container instances should be isolated for investigation and replaced with rebuilt and patched images. To avoid persistence if patches do not exist, the container instance should be replaced frequently with an un-compromised image until a patch released. These tools replace Malware Protection (IS-7) and EDR (IS-8) in container environments.

#### Risk Statement

Failure to detect and remediate changes to running containers using container runtime protection tools increases the risk of unnoticed compromises, potential exploitation, and unauthorised alterations to containerised applications, compromising the security and integrity of the runtime environment.

## Cryptography, Encryption and Key Management

Controls to secure cryptographic protocols.

### CK-1: Cryptographic Key Establishment

**Group:** Cryptography, Encryption and Key Management

#### Control Statement

Use industry-standard cryptographic key establishment schemes and key derivation methods.

#### Control Recommendations

Refer to [SP 800-56A](https://doi.org/10.6028/NIST.SP.800-56Ar3) , [SP 800-56B](https://doi.org/10.6028/NIST.SP.800-56Br2) , and [SP 800-56C](https://doi.org/10.6028/NIST.SP.800-56Cr2) for updated industry-standard cryptographic key establishment schemes and key derivation methods. Cloud services such as AWS Key Management Service and Azure Key Vault can be used for generating and managing cryptographic keys.

#### Risk Statement

Insecure cryptographic key establishment can lead to weak or broken encryption.

### CK-2: Cryptographic Key Rotation

**Group:** Cryptography, Encryption and Key Management

#### Control Statement

Rotate cryptographic keys every [ insert: param, ck-2_prm_1 ] days.

#### Control Recommendations

Cloud services such as AWS Key Management Service and Azure Key Vault enable automatic rotation of cryptographic keys.

#### Risk Statement

Failing to rotate cryptographic keys increases the risk of broken encryption.

#### Parameters

CK-2 Parameters

| ID | Type | Description |
| --- | --- | --- |
| ck-2_prm_1 | time period (days) (int) | The time period in days of cryptographic key rotation frequency. |

### CK-3: Cryptographic Key Management

**Group:** Cryptography, Encryption and Key Management

#### Control Statement

Implement cryptographic key management processes to ensure cryptographic keys are well-managed and safeguarded throughout their lifecycle.

#### Control Recommendations

Follow key management practices such as those described in NIST SP 800-57 or ISO/IEC 27017 to cover aspects including generation, registration, storage, distribution, installation, use, rotation, backup, recovery, revocation, suspension, and destruction of keys.

#### Risk Statement

Inadequate key management increase the risk of unauthorised access, data breaches, and compromised cryptographic operations due to poorly managed or safeguarded cryptographic keys.

### CK-4: Cryptographic Key Storage

**Group:** Cryptography, Encryption and Key Management

#### Control Statement

Securely store cryptographic keys and implement strict access controls based on the principle of least privilege.

#### Control Recommendations

Use cloud services such as AWS Key Management Service and Azure Key Vault to securely store cryptographic keys with access controls.

#### Risk Statement

Inadequate key storage and access controls can lead to unauthorised key access and potential data breaches.

## Data Protection

Controls to protect the data of a system.

### DP-1: Data Residency

**Group:** Data Protection

#### Control Statement

Enforce data residency of [ insert: param, dp-1_prm_2 ] data in [ insert: param, dp-1_prm_1 ].

#### Control Recommendations

Data residency in Singapore must be enforced for [ insert: param, dp-1_prm_2 ] data.

#### Risk Statement

Failure to enforce data residency in the appropriate country may lead to legal and regulatory compliance issues, privacy concerns, and potential unauthorised access or storage of sensitive data outside the jurisdiction, increasing the risk of legal consequences and data breaches.

#### Parameters

DP-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| dp-1_prm_1 | country (str) | The country the data resides in. |
| dp-1_prm_2 | type (str) | The type of data that should have enforced data residency. |

### DP-2: Data at Rest Encryption

**Group:** Data Protection

#### Control Statement

Encrypt data at rest.

#### Control Recommendations

Many CSP services encrypt data at rest by default but this should be confirmed and validated depending on service usage.

#### Risk Statement

Without encrypting data at rest, there's an increased risk of unauthorised access and data exposure in the event of physical theft, unauthorised access to storage media, or compromised security controls, compromising the confidentiality of stored information.

### DP-3: Data in Transit Encryption

**Group:** Data Protection

#### Control Statement

Encrypt data in transit.

#### Control Recommendations

While some CSP services transparently encrypt data in transit at the network layer, data at the application layer should be encrypted using protocols such as Transport Layer Security (TLS) and Secure Socket Layer (SSL).

#### Risk Statement

Failure to encrypt data in transit increases the risk of unauthorised interception and eavesdropping, potentially leading to data breaches, unauthorised access, and compromise of sensitive information during transmission.

### DP-4: Central Cloud Tenant Management

**Group:** Data Protection

#### Control Statement

Implement a centralised cloud tenant management structure for [ insert: param, dp-4_prm_1 ] using [ insert: param, dp-4_prm_2 ].

#### Control Recommendations

Consider services like AWS Organizations, GCP Organizations, or Azure Management Groups for centralised management. Implement appropriate landing zone frameworks. Establish consistent policies and guardrails across all accounts or projects. Centralise logging and monitoring for comprehensive visibility. Use tagging strategies for resource organisation.

#### Risk Statement

Lack of centralised cloud tenant management can lead to inconsistent security policies, reduced visibility, and increased difficulty in managing and securing cloud resources across the organisation.

#### Parameters

DP-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| dp-4_prm_1 | systems (str) | The systems to be hosted on a central tenant. |
| dp-4_prm_2 | system (str) | The central tenant management structure. |

### DP-5: Sanitisation

**Group:** Data Protection

#### Control Statement

Sanitise all hardware that stores data at rest. Shred or incinerate data storage meant for retirement.

#### Control Recommendations

Use industry standards such as a) Peter Gutmann Secure Deletion; b) Bruce Schneier Algorithm c) US Department of Defence's Standards (DoD 5220.22-M).

#### Risk Statement

Violating this control can expose government data to unauthorised users.

### DP-6: Witness Sanitisation and Destruction of Storage Devices

**Group:** Data Protection

#### Control Statement

Witness the sanitisation and destruction process to ensure data is removed from storage.

#### Control Recommendations

Establish a SOP to ensure sanitisation and destruction are witnessed by an agency staff.

#### Risk Statement

Ensuring storage devices are sanitised or destroyed will eliminate the possibility of unauthorised or unintended data retention.

### DP-7: Data Loss Prevention

**Group:** Data Protection

#### Control Statement

Implement data loss prevention mechanisms that monitor data flows, detect sensitive data transfers, and block unauthorised sharing of sensitive data.

#### Control Recommendations

Where possible, use built-in solutions such as Microsoft Purview or Google Workspace data loss prevention rules. Regularly review and update data loss prevention policies to adapt to evolving threats and organisational needs.

#### Risk Statement

Failure to implement data loss prevention measures increases the risk of unauthorised data exfiltration, accidental data leaks, and data breaches, compromising sensitive information and organisational integrity.

### DP-8: Data Classification Disclosure

**Group:** Data Protection

#### Control Statement

For internal applications serving public officers, specify the highest permitted security and sensitivity classification for input data, such as text, audio, or multimedia/file uploads, in the system's user interface and guides.

#### Control Recommendations

Indicate with a clear message, either at or near the input field, the relevant data classification that may be used with the system.

#### Risk Statement

Users who are not informed of the maximum allowed data classification may submit sensitive information the system is not permitted to process, risking data breaches, legal violations, and reputational harm.

## Datacentre

Controls to secure datacentre system components.

### DC-1: Separate hosting

**Group:** Datacentre

#### Control Statement

Physically separate Government resources from non-Government resources.

#### Control Recommendations

For on-premise environments, ensure government resources are physically stored and secured separately from non-government resources.

#### Risk Statement

Violating this control can subject government data and systems to access risks leading to compromised systems and data.

### DC-2: Physical Access Controls

**Group:** Datacentre

#### Control Statement

Implement physical access controls to prohibit unauthorised access to the hosting environment or network rooms.

#### Control Recommendations

Measures to consider include:

a) personnel security clearance and checks

b) Continuous monitoring

c) Immediate security response

d) Strong authentication card access system to regulate and log access of employees, visitors and contractors to the facility;

e) Guards deployed to guard the facility 24/7;

f) Restrict items (such as unauthorised computing devices) to be brought into the facility;

g) Intrusion Detection System installed to detect unauthorised access;

h) CCTV installed to monitor the facility.

#### Risk Statement

Violating this control can subject government data and systems to access risks leading to compromised systems and data.

## Generative AI

Controls for the use of generative AI/LLMs within applications.

### GA-1: Overseas-hosted GenAI API services

Group: Generative AI

#### Control Statement

Use only up to RESTRICTED and SENSITIVE NORMAL data with GenAI API services served by models hosted overseas.

#### Control Recommendations

Verify data classification with data owner before using the data with overseas GenAI API services. Ensure that these API services are accessed only via environments authorised for up to RESTRICTED and SENSITIVE NORMAL data.

#### Risk Statement

Primary data is at greater legal, privacy, and exfiltration risk when in transit and processed by GenAI API services hosted in other countries.

### GA-2: Singapore-hosted GenAI API services

Group: Generative AI

#### Control Statement

Use only up to CONFIDENTIAL and SENSITIVE HIGH data with GenAI API services served by models hosted in Singapore.

#### Control Recommendations

Verify data classification with data owner before using the data with Singapore-hosted GenAI API services. Ensure that these API services are accessed only via environments authorised for up to CONFIDENTIAL and SENSITIVE HIGH data. Check documentation for whether cross-region inferencing or overseas processing occurs under certain conditions.

#### Risk Statement

Primary data carries legal, privacy, and exfiltration risk when in transit and processed by GenAI API services hosted in Singapore.

### GA-3: Non-logging and non-training Agreement

Group: Generative AI

#### Control Statement

Obtain a legally-binding commitment from the GenAI API service provider that states that they do not log, store, nor retain input and output data, and that no input and output data are used for training GenAI models. Prompt caching (i.e. temporarily storing responses to frequently used prompts) is exempt from the above restriction on logging, storing, or retaining data, provided the cache time-to-live (TTL) is ≤ 24 hours.

#### Control Recommendations

Ensure that the agreement covers all endpoints, parameters, outputs and models in use. Using GenAI API services provided by GCC 2.0 satisfies this control.

#### Risk Statement

Without a legally binding agreement, the GenAI service provider can access primary data or logs through automated or manual checks that can expose classified data, or they may use the data for further training of their GenAI models.

### GA-4: Data classification for self-hosted GenAI models

Group: Generative AI

#### Control Statement

Ensure that GenAI models are hosted in an environment that can host the highest data classification of government data contained in the system that the model is deployed in.

#### Control Recommendations

Refer to relevant IM8 SSPs for controls that need to be met before hosting government data of a given data classification in that environment (e.g. in a Singapore-hosted GCC environment.)

#### Risk Statement

Using GenAI models with data that is more classified than what its environment is permitted to hold may open up cybersecurity vulnerabilities for attackers to exfiltrate classified data.

### GA-5: GenAI model formats and loaders

Group: Generative AI

#### Control Statement

Use approved formats (such as [ insert: param, ga-5_prm_1 ]) when using any open-weights GenAI models. Use approved loaders (such as [ insert: param, ga-5_prm_2 ]) to load open-weights GenAI models.

#### Control Recommendations

Update GenAI model loaders frequently to hinder attacks.

#### Risk Statement

Insecure model formats or loaders have been shown to be potential threat vectors that may allow for arbitrary code execution. Failure to use more secure formats or loaders may result in potential unauthorised access to the system.

#### Parameters

GA-5 Parameters

| **ID** | **Type** | **Description** |
| --- | --- | --- |
| ga-5_prm_1 | list of model formats (str) | An example list of approved model formats. |
| ga-5_prm_2 | list of model loaders (str) | An example list of approved model loaders. |

### GA-6: File upload safeguards

Group: Generative AI

#### Control Statement

Implement file upload safeguards if file uploads are enabled in the system.

#### Control Recommendations

Examples of safeguards include but are not limited to: (1) Implementing Data Loss Protection (“DLP”) tools to monitor document uploads, (2) Disabling bulk or batch file uploads, such as by preventing users from selecting multiple files with a single action, or by requiring users to confirm the data security/sensitivity classification of each file individually before uploading, (3) Prompting users who upload excessively large files to review file contents and their collective data security/sensitivity classification.

#### Risk Statement

Enabling file uploads in a generative AI application creates two key risks: (a) the possibility of sensitive data leaks if models are prompted to reveal information from uploaded files, and (b) the risk of exceeding permitted data classification levels — either by uploading multiple files without reassessing their collective classification, or by inadvertently uploading documents with a higher classification level due to human error.

### GA-7: Evaluation of GenAI accuracy, safety, and output quality

Group: Generative AI

#### Control Statement

Test the accuracy, safety, and quality of the GenAI application's outputs, with clearly defined metrics, test scenarios, and criteria.

#### Control Recommendations

Determine the adequate level of accuracy, safety, and output quality for your application. Develop tests to assess outputs on these dimensions. The complexity and structure of these tests should be tailored to the risk profile of your application. Document this approach and measure & monitor results after relevant model, prompt, and dependency updates.

#### Risk Statement

Failure to evaluate models, prompts, and their dependencies (such as tool integrations and retrieval-augmented generation (RAG) systems) can result in low-quality outputs after updates or configuration changes.

### GA-8: Inform users about GenAI risks and limitations

Group: Generative AI

#### Control Statement

Require users to explicitly acknowledge the risk of inaccurate or fabricated outputs (hallucinations), such as selecting a checkbox or clicking on an 'I Agree' button before the application can be accessed.

#### Control Recommendations

Include clauses about the risk of inaccurate / fabricated outputs in your Terms of Use. Indicate prominently in your application about the possibility of inaccurate or fabricated outputs. Ensure that educational materials, such as documentation, guides, playbooks, or workshops, highlight responsible use and best practices for users of the GenAI system.

#### Risk Statement

Uninformed users may trust inaccurate model-generated outputs.

## Human Resource

Controls for human resources.

### HR-1: Security Awareness Training

**Group:** Human Resource

#### Control Statement

Conduct security awareness training for all employees that operate and manage the system every [ insert: param, hr-1_prm_1 ] months.

#### Control Recommendations

Develop a comprehensive security awareness training that covers: (a) Relevant laws, regulations, codes of practice, policies, standards, guidelines and procedures (b) Phishing and social engineering awareness (c) Incident reporting procedures (d) Secure handling of sensitive information

Utilise other methods such as posters, emails to provide regular and timely communication covering cybersecurity awareness messages and prevailing threats, impacts and mitigations.

#### Risk Statement

Inadequate or infrequent security awareness programs increase the likelihood of successful social engineering attacks or data breaches and non-compliance due to human error or negligence.

#### Parameters

HR-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| hr-1_prm_1 | time period (months) (int) | The frequency at which security awareness training should be conducted. |

### HR-2: Security Screening of Employees

**Group:** Human Resource

#### Control Statement

Conduct security screening of employees that operate and manage the system, during their hiring phase.

#### Control Recommendations

Consider engaging reputable 3rd party screening companies to conduct background checks before extending employment offers.

#### Risk Statement

Inadequate screening may lead to the hiring of individuals with criminal histories, falsified credentials or conflicts of interest. This potentially leads to insider threats, causing damage to the organisation.

### HR-3: Employee Termination Process

**Group:** Human Resource

#### Control Statement

Develop and document a process for the secure termination of employees.

#### Control Recommendations

The employee termination process should include:

- Deprovisioning accounts promptly before terminating employees to reduce opportunity for damage
- Conducting exit interviews to ensure understanding of ongoing confidentiality obligations
- Ensuring all issued equipment is returned and inventoried
- Secure disposal of employee's data and removal of access rights
- Notification to relevant stakeholders about the employee's departure and changes to contact points

#### Risk Statement

Inadequate employment termination processes can lead to unauthorised access to sensitive information due to either disgruntled employees retaining access or attackers exploiting non-disabled accounts.

## Infrastructure Security

Controls to secure infrastructure that host applications, services, and data.

### IS-1: Management Agents

**Group:** Infrastructure Security

#### Control Statement

Install CSP management agents on hosts to remotely and securely manage their configurations.

#### Control Recommendations

Most CSP compute instances preinstall management agents (e.g., AWS Systems Manager Agent, Azure Windows VM Agent) by default. If the image does not come with the preinstalled agent, install manually.

#### Risk Statement

Without installing management agents on hosts, there is an increased risk of manual misconfigurations, difficulty in maintaining consistent configurations, and potential security vulnerabilities due to reduced visibility and ability to manage hosts effectively.

### IS-2: Automated Patch Management Tools

**Group:** Infrastructure Security

#### Control Statement

Use automated patch management tools to facilitate security patching of operating systems and software included in the operating systems.

#### Control Recommendations

Patch management can be automated as part of the build and deploy phase, such as with golden images for containers and virtual machines. It can also be automated in running instances on the cloud via CSP node management services (such as Azure Update Manager or AWS Systems Manager Patch Manager) and on-premises via patch management services (such as Windows Server Update Services or Configuration Management tools). To minimise risk of disruption due to patching, consider staged rollouts with canary deployments or in non-production environments.

#### Risk Statement

Failure to automate security patching of operating systems and included software increases the risk of delayed or missed security updates, leaving systems vulnerable to known exploits and potential security breaches, compromising the overall security of the environment.

### IS-3: Restricted Administrator Privileges

**Group:** Infrastructure Security

#### Control Statement

Restrict administrator privileges by disabling remote login for the root/administrator user and restricting sudo/administrators group access.

#### Control Recommendations

Further reduce the attack surface by running common services such as the web server or database without root/administrator/system privileges.

#### Risk Statement

Without restricting administrator privileges, there is an increased risk of unauthorised access, privilege escalation, and potential security breaches, compromising the integrity and security of the system.

### IS-4: Least Functionality

**Group:** Infrastructure Security

#### Control Statement

Disable or remove unnecessary functions, system ports, protocols, software, and services on the host.

#### Control Recommendations

Follow the principle of least functionality to configure the host to carry out only its intended purpose. CSP node management services can provide an inventory of software and services (e.g., AWS Systems Manager Inventory). Vulnerability assessment scanners (e.g., AWS Inspector) can also identify software vulnerabilities and network exposure.

#### Risk Statement

Failure to disable or remove unnecessary functions, system ports, protocols, software, and services on the host increases the attack surface, potential vulnerabilities, and the risk of exploitation, compromising the overall security and performance of the system.

### IS-5: Host System Hardening

**Group:** Infrastructure Security

#### Control Statement

Harden the host configuration with reference to industry standards.

#### Control Recommendations

Select the appropriate benchmark for the host such as from the [NIST National Checklist Program](https://ncp.nist.gov/repository) or [CIS Benchmarks](https://www.cisecurity.org/controls/v8) . Automate the configuration process or use hardened images instead of manually configuring.

#### Risk Statement

Without hardening the operating system configuration according to industry standards, there's an increased risk of security vulnerabilities, unauthorised access, and potential exploitation, compromising the overall security posture and resilience of the operating system.

### IS-6: Remote Administration

**Group:** Infrastructure Security

#### Control Statement

Use remote administration tools to control and monitor remote access.

#### Control Recommendations

Prioritise remote administration tools (e.g., AWS Systems Manager Session Manager, AWS Systems Manager Fleet Manager) over direct SSH or RDP. If SSH is still required and remote administration tools are not available, use it only within a private non-production environment such as an encrypted tunnel and authenticate with short-lived certificates. Document and remediate gaps in monitoring and automation to minimise the need for remote access.

#### Risk Statement

Using remote administration tools enhances security by providing controlled and audited access, reducing the risk of unauthorised activities, and improving overall management of privileged identities.

### IS-7: Malware Protection

**Group:** Infrastructure Security

#### Control Statement

Detect and quarantine malware on hosts with anti-malware tools.

#### Control Recommendations

Configure anti-malware tools for all compute hosts, preferably leveraging cloud-native services where available. These tools should be kept up-to-date with the latest malware signatures. Regular scans should be scheduled to detect and quarantine potential threats.

#### Risk Statement

Without malware protection, there's an increased risk of undetected malicious activities, potential data breaches, and compromise of host systems, highlighting the importance of proactive measures to ensure the security and integrity of the environment.

### IS-8: Endpoint Detection and Response (EDR)

**Group:** Infrastructure Security

#### Control Statement

Monitor security threats on hosts with an EDR tool.

#### Control Recommendations

Implement EDR tools for all compute hosts. Security incident response should be planned and documented for the tool. EDR tools with built-in malware protection should be favoured to reduce additional agents.

#### Risk Statement

Failure to monitor security threats on hosts with an Endpoint Detection and Response (EDR) tool increases the risk of undetected advanced threats, compromises in host security, and delayed response to potential security incidents, highlighting the need for continuous monitoring and proactive threat detection.

### IS-9: End-of-Support (EOS) Assets

**Group:** Infrastructure Security

#### Control Statement

Ensure deployed [ insert: param, is-9_prm_1 ] assets have not reached end-of-support (EOS). Use of EOS assets will require risk acceptance by approved authority.

#### Control Recommendations

Identify, track and replace EOS assets in a timely manner. Regularly review assets to identify upcoming EOS timeframe and replace them ahead of EOS date.

#### Risk Statement

EOS assets can introduce security vulnerabilities as the assets are no longer provided with security fixes.

#### Parameters

IS-9 Parameters

| ID | Type | Description |
| --- | --- | --- |
| is-9_prm_1 | type (str) | The type of asset. |

### IS-10: Synchronise time clocks

**Group:** Infrastructure Security

#### Control Statement

Synchronise internal clocks to a common reference time source.

#### Control Recommendations

Use common time source such as Network Time Protocol (NTP). In the cloud, it is recommended to use the default time sources provided by the CSPs.

#### Risk Statement

The lack of synchronised clocks introduces significant risks, including increased security vulnerabilities, data integrity issues, and challenges in troubleshooting.

### IS-11: Central Domain Name Registration

**Group:** Infrastructure Security

#### Control Statement

Register .gov.sg and .edu.sg domain names with GovTech as the sole registrar.

#### Control Recommendations

Use the Whole of Government Domain Name Server (DNS) portal on the IT Service Management (ITSM) portal to register domain names. While the root level of the domain must be registered with ITSM, the use of subdomain delegation to external DNS Services (e.g. AWS Route53, Cloudflare DNS, etc) is allowed.

#### Risk Statement

Improper management of domain names increase the risk of phishing attacks or domain takeovers.

### IS-12: DNS Security Extensions (DNSSEC)

**Group:** Infrastructure Security

#### Control Statement

Implement DNS Security Extensions (DNSSEC) for public DNS records and servers.

#### Control Recommendations

DNS services such as WOG DNS, Amazon Route 53 and Cloudflare support DNSSEC configuration.

#### Risk Statement

Insecure domain name resolution can lead to man-in-the-middle attacks caused by DNS spoofing or DNS cache poisoning.

### IS-13: Defensive Domain Name Registration

**Group:** Infrastructure Security

#### Control Statement

Register second (.sg) and third (.com.sg, .org.sg, .net.sg, .edu.sg) level domain name variants of the system's primary domain name.

#### Control Recommendations

Consider defensive registration of domain names with typographical variants of the system's primary domain name.

#### Risk Statement

Malicious use of domain names similar to actual organisation domain names increases the risk of phishing and spoofing.

### IS-14: Singapore SMS Sender ID Registry Registration

**Group:** Infrastructure Security

#### Control Statement

Register and use whitelisted SMS Sender IDs with the Singapore SMS Sender ID Registry for sending SMSes to members of the public.

#### Control Recommendations

Agencies must use the "gov.sg" Sender ID via the Postman tool to send SMSes to members of the public unless exempted. Whitelist Sender IDs used to send SMSes and blacklist Sender IDs which are variants of the whitelisted Sender IDs, agency names, or names of services.

#### Risk Statement

Lack of Sender ID registration allows malicious entities to spoof legitimate Government SMSes.

## Logging and Monitoring

Controls to support detection and response to security and operations incidents.

### LM-1: Separate Log Storage

**Group:** Logging and Monitoring

#### Control Statement

Store logs in a different system or system component than the system component that generated the logs.

#### Control Recommendations

Do not store logs only in the same system component that generated it. For example, an application server on EC2 or ECS should send logs to a separate storage such as an S3 bucket as soon as possible after the logged event instead of only storing it on the server. For cloud audit logs, store them in a separate system or account.

#### Risk Statement

Storing logs in a repository separate from the system component reduces the risk of tampering, unauthorised access, and manipulation of logs if the system component is compromised.

### LM-2: Tamper-Resistant Log Storage

**Group:** Logging and Monitoring

#### Control Statement

Protect logs from unauthorised access, modification, and deletion.

#### Control Recommendations

Apply access control policies to logs based on the principle of least privilege. As far as possible, only read access should be granted. Logs sent to GCC Central Logs are tamper-resistant.

#### Risk Statement

Without protection measures, logs are susceptible to unauthorised access, modification, or deletion, leading to the risk of tampering, loss of crucial audit information, and compromised forensic analysis capabilities during security incidents.

### LM-3: Network Flow Logging

**Group:** Logging and Monitoring

#### Control Statement

Log network traffic going to and from network interfaces.

#### Control Recommendations

Enable VPC Flow Logs for AWS or its equivalents.

#### Risk Statement

Failing to log network traffic going to and from network interfaces increases the risk of overlooking suspicious activities, potential security breaches, and the inability to trace and investigate network-related incidents effectively.

### LM-4: Audit Logging

**Group:** Logging and Monitoring

#### Control Statement

Log management and audit events.

#### Control Recommendations

For cloud, configure CloudTrail for AWS or its equivalents to log management and audit events such as changes to accounts, access, IAM policies and resources. For SaaS and COTS, enable audit logging features.

#### Risk Statement

Neglecting to log and manage audit events increases the risk of undetected security incidents, compromises visibility into system activities, and hinders effective forensic analysis and compliance monitoring.

### LM-5: Database Logging

**Group:** Logging and Monitoring

#### Control Statement

Log database audit events.

#### Control Recommendations

Enable RDS logging for AWS or its equivalents.

#### Risk Statement

Neglecting to log database audit events raises the risk of overlooking unauthorised activities, compromises in data security, and hinders the ability to track and investigate security incidents or compliance violations within the database environment.

### LM-6: Access Logging

**Group:** Logging and Monitoring

#### Control Statement

Log access requests sent to web application firewalls, load balancers, proxies or web servers.

#### Control Recommendations

Enable AWS WAF logging, Application Load Balancer logging, API Gateways, or their equivalents.

#### Risk Statement

Failure to log access requests sent to web application firewalls, load balancers, proxies, or web servers increases the risk of overlooking potential security threats, unauthorised access attempts, and compromises visibility into the traffic that could lead to security incidents.

### LM-7: Host Security Event Logging

**Group:** Logging and Monitoring

#### Control Statement

Log security events on hosts.

#### Control Recommendations

Host security events include operating system security events, authentication, and endpoint detection and response alerts, configuration changes, and account and access rights changes.

#### Risk Statement

Neglecting to log security events on hosts increases the risk of undetected security incidents, compromises incident response capabilities, and hinders forensic analysis, limiting the ability to identify and mitigate potential threats.

### LM-8: Security Log Retention

**Group:** Logging and Monitoring

#### Control Statement

Retain security logs for at least [ insert: param, lm-8_prm_1 ] day(s).

#### Control Recommendations

Security logs include network flow logs, cloud management logs, access logs, database logs and host logs. Retain non-security logs (e.g. application, operations and performance logs) as long as needed for incident resolution and debugging. Consider log lifecycle management automation, such as Amazon S3 Lifecycle configurations.

#### Risk Statement

Failure to retain security logs increases the risk of losing crucial historical data, hindering investigations, compliance audits, and the ability to identify and respond to security incidents that occurred beyond a limited timeframe.

#### Parameters

LM-8 Parameters

| ID | Type | Description |
| --- | --- | --- |
| lm-8_prm_1 | time period (days) (int) | The time period in days of log retention. |

### LM-9: Security Monitoring and Alerting

**Group:** Logging and Monitoring

#### Control Statement

Configure security monitoring to identify potential security violations or breaches and send automated alerts, and respond to them accordingly.

#### Control Recommendations

Enable Amazon GuardDuty, Microsoft Azure Security Center, or their equivalents.

#### Risk Statement

Without configuring security monitoring to identify potential security violations or breaches and send automated alerts, there's an increased risk of delayed or unnoticed security incidents, hindering timely response and mitigation efforts to protect the system from further compromise.

### LM-10: Resource Usage Monitoring and Alerting

**Group:** Logging and Monitoring

#### Control Statement

Configure resource usage monitoring to identify abnormal usage and send automated alerts.

#### Control Recommendations

Configure Amazon CloudWatch alarms, Azure Monitor alerts, or their equivalents to identify abnormal usage such as spike in usage, access to resources during unexpected hours, and excessive charges.

#### Risk Statement

Lack of resource usage monitoring with automated alerts increases the risk of overlooking abnormal usage patterns, potential resource abuse, and compromises in system performance, hindering the ability to proactively address issues and prevent service disruptions.

### LM-11: Service Level Monitoring and Alerting

**Group:** Logging and Monitoring

#### Control Statement

Monitor, maintain and alert on service level objectives (SLOs) and indicators (SLIs) to ensure consistent service performance, availability and reliability.

#### Control Recommendations

Implement a comprehensive monitoring system that tracks key SLIs and evaluates them against defined SLOs. This will help in identifying potential service level breaches early and take proactive measures to maintain service quality. Examples include CloudWatch metrics and alerts, Amazon Route 53 health checks, Azure Monitor Application Insights, or their equivalents.

#### Risk Statement

Without effective service level monitoring to identify potential application or service degradation and send automated alerts, there is a risk of failing to meet service availability standards, which could result in user dissatisfaction and reduced reliability.

### LM-12: Central Security Log Management and Monitoring

**Group:** Logging and Monitoring

#### Control Statement

Centralise security log management and monitoring with [ insert: param, lm-12_prm_1 ].

#### Control Recommendations

Tenants on Government Commercial Cloud (GCC) already have Cloud Service Provider (CSP) tenant security logs stored centrally and available for forwarding to Government Cyber Security Operations Centre (GCSOC). Contact GCSOC for subscription and additional services.

#### Risk Statement

Lack of central security log management and monitoring increases the risk of delayed or unnoticed security incidents, hindering effective response, and compromising the overall cybersecurity posture.

#### Parameters

LM-12 Parameters

| ID | Type | Description |
| --- | --- | --- |
| lm-12_prm_1 | service (str) | The central security log management and monitoring service. |

### LM-13: Anomalous Database Activity Monitoring

**Group:** Logging and Monitoring

#### Control Statement

Monitor database activities for anomalous activity.

#### Control Recommendations

Configure database activity monitoring tools, such as RDS Activity Streams or similar mechanisms, to detect and alert on unusual authentication attempts, abnormal read or write operations, or other anomalous database activity.

#### Risk Statement

Neglecting to monitor database activities for anomalous behaviour increases the risk of undetected security threats, unauthorised access, and compromises in data integrity, hindering the ability to identify and respond to potential database-related incidents.

### LM-14: Web Defacement Monitoring

**Group:** Logging and Monitoring

#### Control Statement

Plan for and implement measures to detect and recover from web defacements.

#### Control Recommendations

Visual monitoring tools enable detection of web defacements of internet-facing systems.

#### Risk Statement

Failure to detect and respond to web defacement promptly will lead to prolonged disruption to services.

### LM-15: Structured Log Formatting

**Group:** Logging and Monitoring

#### Control Statement

Publish logs in a consistent, structured format that aligns with industry standards for easy parsing and analysis.

#### Control Recommendations

For security logs, implement or transform to OCSF (Open Cybersecurity Schema Framework), ECS (Elastic Common Schema) or similar schemas to standardise log formats for better threat detection and analysis. For operational logs, adopt OpenTelemetry or structured JSON formats to facilitate clear, structured, and efficient log analysis for system performance and diagnostics. Consistent log formatting aids in automated parsing and helps in integrating logs from various sources.

#### Risk Statement

Inconsistent or unstructured log formatting can lead to difficulties in log analysis and monitoring, potentially resulting in missed critical events or delayed response to system anomalies.

### LM-16: Key Signals Monitoring

**Group:** Logging and Monitoring

#### Control Statement

Monitor key user-facing signals to maintain robust service health and performance.

#### Control Recommendations

Implement monitoring of key signals such as latency, traffic, errors, and saturation (the 4 Golden Signals). Regularly track and analyse these indicators for proactive issue detection and resolution. Use this data to identify trends and areas for system improvement, ensuring continuous enhancement in service quality and reliability.

#### Risk Statement

Inadequate monitoring of key user-facing signals such as latency, traffic, errors, and saturation can lead to suboptimal service performance, adversely impacting user experience, system efficiency, and increasing the likelihood of system failures. This oversight can significantly detract from service reliability and user satisfaction.

### LM-17: Software delivery performance monitoring

**Group:** Logging and Monitoring

#### Control Statement

Measure and analyse software delivery performance to optimise development velocity and operational efficiency.

#### Control Recommendations

Implement tools and processes to track Deployment Frequency, Lead Time for Changes, Change Failure Rate, and Time to Restore Service (the DORA 4 Key metrics). Use these metrics as benchmarks to drive continuous improvement in the software development and deployment process, enhancing agility, reliability, and responsiveness to changes.

#### Risk Statement

Failing to measure and improve the software delivery performance can lead to inefficient development processes, reduced software quality and longer recovery times.

### LM-18: Whole of Government Application Analytics (WOGAA)

**Group:** Logging and Monitoring

#### Control Statement

Implement Whole of Government Application Analytics (WOGAA) in public facing digital services.

#### Control Recommendations

Register at the WOGAA portal at [https://wogaa.sg/](https://wogaa.sg/) and follow the implementation documentation at [https://docs.wogaa.sg/](https://docs.wogaa.sg/) .

#### Risk Statement

Lack of performance tracking can lead to gaps in service delivery.

### LM-19: Log Sanitisation

**Group:** Logging and Monitoring

#### Control Statement

Sanitise logs to protect classified and sensitive data before it is recorded in any logging system or shared to any third party.

#### Control Recommendations

Identify types of classified and sensitive data that may appear in logs. When logging, consider using sanitisation techniques like masking or tokenisation. This ensures that sensitive information — such as PII, credentials, API keys, and payment details — are not stored in plaintext during log collection.

#### Risk Statement

Failing to sanitise logs increases the risk of unauthorised exposure or misuse of sensitive information and other confidential data. This exposure could lead to privacy breaches, financial losses, compliance violations and damage to national reputation.

### LM-20: User and Entity Behaviour Analytics

**Group:** Logging and Monitoring

#### Control Statement

Implement User and Entity Behaviour Analytics (UEBA) to monitor and analyse user activities for suspicious behaviour and potential threats.

#### Control Recommendations

Select a UEBA tool that integrates with existing security information and event management (SIEM) solutions and provides real-time alerts for anomalous activities. Ensure regular updates and tuning of the tool to enhance detection capabilities.

#### Risk Statement

Lack of monitoring for potentially malicious user and entity behaviour increases the risk of insider threats and undetected malicious activities, potentially leading to data breaches and system compromise.

### LM-21: Detection Updates

**Group:** Logging and Monitoring

#### Control Statement

Update detections for malware and indicators of compromise at least every [ insert: param, lm-21_prm_1 ] day(s).

#### Control Recommendations

Implement automation to monitor the rollout of detection updates and ensure conformance.

#### Risk Statement

Outdated detection rules increase the risk of undetected compromises.

#### Parameters

LM-21 Parameters

| ID | Type | Description |
| --- | --- | --- |
| lm-21_prm_1 | time period (days) (int) | The time period in days for detection update frequency. |

## Network Security

Controls to secure the network boundaries of a system.

### NS-1: Network and System Component Segmentation

**Group:** Network Security

#### Control Statement

Segment and isolate system components into separate physical and logical networks or environments based on their security requirements and risk levels.

#### Control Recommendations

Deploy security controls such as firewalls, security groups, and API gateways to establish and maintain segmentation. In cloud environments, implement a tiered network architecture where internet-facing services are restricted to designated public segments, while sensitive backend systems remain in private segments with controlled outbound access. Consider micro-segmentation for granular control based on workload sensitivity.

#### Risk Statement

Failure to segregate private and public resources within distinct subnets in a virtual network increases the risk of unauthorised access to sensitive data, as private resources may be exposed to the public internet, compromising the overall security of the infrastructure.

### NS-2: Access Restrictions on CSP Resources Outside Virtual Network

**Group:** Network Security

#### Control Statement

Restrict access to CSP resources outside of a virtual network (e.g., Lambda, DynamoDb, API Gateways, S3, CloudFront) using access controls or application layer authorisation.

#### Control Recommendations

Apply access restrictions appropriate to the resource type. Access through interface VPC endpoints is only required if the client is hosted in a private subnet. For example:

- Restrict access to DynamoDB with IAM policies.
- Restrict access to API Gateway with Lambda authorizers or authorisation middlewares at the application layer. If the API Gateway is exposed to private subnets, create a private API.
- Restrict access to S3 Buckets with IAM policies and block public access from the internet.

#### Risk Statement

Lack of access restrictions raises the risk of unauthorised access, data exposure, and potential misuse of critical services, compromising the overall security posture.

### NS-3: Deny by Default - Allow by Exception

**Group:** Network Security

#### Control Statement

Deny network communications traffic by default and allow network communications traffic by exception at managed interfaces.

#### Control Recommendations

Configure network access control lists and security groups to deny all traffic by default. Only allow traffic to and from specific hosts and ports by exception. For egress traffic to the internet, consider whitelisting domains at the application layer or DNS resolver rather than just hosts or ports at the transport layer.

#### Risk Statement

Without network access controls, there's an increased risk of unauthorised or malicious network access, leading to potential security breaches and compromise of system integrity.

### NS-4: Inter-Private Network Connectivity

**Group:** Network Security

#### Control Statement

Route network traffic between private networks without going through the internet.

#### Control Recommendations

Use CSP Private endpoint services (e.g., AWS PrivateLink with VPC endpoints) when you want to allow one or more consumer VPCs unidirectional access to a specific service or set of instances in the service provider VPC. Otherwise, use VPC peering and Transit Gateway when you want to enable layer-3 IP connectivity between VPCs. Refer to the [Multi-VPC AWS Network Infrastructure Whitepaper](https://docs.aws.amazon.com/whitepapers/latest/building-scalable-secure-multi-vpc-network-infrastructure/vpc-to-vpc-connectivity.html) for further guidance.

#### Risk Statement

When routing through the internet, there's an increased risk of man-in-the-middle and spoofing attacks. Allowing bidirectional access between networks without fine-grained access controls increases the risk of unauthorised access, potential data exfiltration, and compromise of network security compared to unidirectional access to specific resources.

### NS-5: Network and Application Layer Filtering

**Group:** Network Security

#### Control Statement

Filter direct traffic from the internet to protect against network and application layer attacks.

#### Control Recommendations

Deploy the following mechanisms as appropriate for the system and network architecture:

- Web Application Firewall
- Distributed Denial of Service Protection (e.g., AWS Shield)
- Content Delivery Network (e.g., CloudFront)

#### Risk Statement

Lack of filtering for direct traffic from the internet exposes the system to the risk of network and application layer attacks, increasing the likelihood of unauthorised access, denial-of-service incidents, and compromise of sensitive data.

### NS-6: Valid and Trusted SSL/TLS Certificates

**Group:** Network Security

#### Control Statement

Ensure that deployed SSL/TLS certificates are:

- signed by a trusted root Certificate Authority;
- match the domain name of the service they are issued for;
- not expired; and
- not revoked.

#### Control Recommendations

Configure a certificate manager that auto-renews certificates and sends alerts before expiry (e.g., AWS Certificate Manager). Otherwise, automate these functions separately.

#### Risk Statement

Using invalid SSL/TLS certificates introduces the risk of compromised encryption, man-in-the-middle attacks, and potential unauthorised access to sensitive information.

### NS-7: Secure Inter-Service Communication

**Group:** Network Security

#### Control Statement

Ensure communications between services are secure by making them authenticated, authorised and encrypted.

#### Control Recommendations

Design and build inter-service communications (e.g., databases, microservices) to be authenticated, authorised and encrypted (e.g., via API gateways, proxies, private endpoint services, message queues, or service meshes). It is recommended to log communication (such as access logs, transaction logs or payloads) between services for detection, monitoring and investigation of incidents.

#### Risk Statement

Failure to ensure secure communications between services increases the risk of unauthorised access, data breaches, and potential manipulation of sensitive information during transit.

### NS-8: Secure Cloud and On-Premises Connectivity

**Group:** Network Security

#### Control Statement

Route network traffic between on-premises systems and cloud systems through a secure intermediary.

#### Control Recommendations

Design and build secure communications to or from on-premises systems through a managed interface with boundary protection mechanisms rather than direct connectivity (e.g. via API gateways, application proxies or private endpoint services).

#### Risk Statement

Insecure connectivity between cloud and on-premises environments can lead to data breaches, unauthorised access, and compromise of both cloud and on-premises resources.

### NS-9: Intrusion Prevention System (IPS)/Intrusion Detection System (IDS)

**Group:** Network Security

#### Control Statement

Set up and configure an Intrusion Prevention System (IPS)/Intrusion Detection System (IDS) in the network.

#### Control Recommendations

Configure network or host IPS/IDS to detect malicious traffic to/from public or untrusted networks.

#### Risk Statement

Absence of network or host IPS or IDS in the network increases the likelihood of undetected intrusions, putting sensitive data and system integrity at risk.

### NS-10: Private Network Connectivity

**Group:** Network Security

#### Control Statement

Implement strong access controls, encryption, and logging for remote developer, maintainer, or administrator access to private network resources.

#### Control Recommendations

Use strong authentication and MFA. Layered security mechanisms and controls include:

- Inspect traffic from gateway to private network;
- Terminate all remote access connections in a dedicated network segment within the network and restrict access to only systems and services allowed by the organisation;
- Implement strong encryption for remote access into internal networks;
- Only allow authorised devices to remotely access internal networks;
- Ensure that remote access connections are not perpetual and re-authenticate remote connections to the VPN gateway on a periodic basis (such as every four hours);
- Set the maximum number of consecutive failed authentication attempts before account lockout for remote access to internal networks; and
- Make sure that split tunnelling is not implemented.

#### Risk Statement

Weak private network security may expose the network to malicious activities, jeopardising the confidentiality, integrity, and availability of critical resources.

### NS-11: Alerts on Firewall Configuration Changes

**Group:** Network Security

#### Control Statement

Generate alerts to inform appointed administrators on changes to firewall rules, including the enabling or disabling of rules.

#### Control Recommendations

Implement real time alerts to inform administrators of creation, deletion, modification, enabling and disabling of firewall rules. Also alert administrators when unusual or sudden spike/drop in utilisation of firewall's system resources.

#### Risk Statement

Any unintended changes to firewall rules can significantly lower the perimeter defence of a network.

## Resiliency

Controls for resiliency.

### RS-1: Multi-AZ Deployment

**Group:** Resiliency

#### Control Statement

Deploy the system in [ insert: param, rs-1_prm_1 ] availability zones (AZs) within the region.

#### Control Recommendations

Utilise cloud-native services for load-balancing and auto-scaling across multiple AZs. Implement health checks and automated failover mechanisms to redirect traffic. Ensure data is replicated across multiple AZs.

#### Risk Statement

Failure to deploy services across multiple availability zones increases the risk of extended service outages due to a single point of failure.

#### Parameters

RS-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| rs-1_prm_1 | number of zones (str) | The number of availability zones to deploy to. |

### RS-2: Dynamic Resource Scaling

**Group:** Resiliency

#### Control Statement

Implement and utilise dynamic resource scaling techniques in the system.

#### Control Recommendations

Resource optimisation techniques such as load balancing and auto-scaling should be employed to maximise resource utilisation and minimise idle resources by adjusting to real-time demand fluctuations.

Refer to the AWS Well-Architected Framework's 'Reliability' pillar for further guidance on implementing resilient systems.

#### Risk Statement

Failure to implement dynamic resource scaling may lead to inefficient resource utilisation and potential service degradation during spikes in demand. This can lead to system outages during peak loads, reducing overall system performance and reliability.

### RS-3: Load Testing

**Group:** Resiliency

#### Control Statement

Perform load testing every [ insert: param, rs-3_prm_1 ] days.

#### Control Recommendations

Load testing should be conducted on a replica of the production system. The load should mimic real user traffic.

#### Risk Statement

Inadequate load testing may lead to unexpected system failures during periods of high demand, leading to service interruptions.

#### Parameters

RS-3 Parameters

| ID | Type | Description |
| --- | --- | --- |
| rs-3_prm_1 | time period (days) (int) | The frequency at which load testing should be performed. |

## Secure Development

Controls to secure the development pipeline and perform source code quality assurance.

### SD-1: Push Protection for Secrets

**Group:** Secure Development

#### Control Statement

Configure the code repository to prevent secrets from being pushed to the repository.

#### Control Recommendations

Use GitLab's push rules or GitHub's push protection to reject secrets on push.

#### Risk Statement

Failure to configure the code repository to prevent secrets from being pushed introduces the risk of inadvertent exposure, unauthorised access, and potential misuse of sensitive information, compromising the security of the codebase and associated systems.

### SD-2: Default Branch Push Permissions

**Group:** Secure Development

#### Control Statement

Configure the code repository to prevent pushes (including force pushes) to the default branch.

#### Control Recommendations

Use GitLab's protected branch and merge request settings or GitHub's branch protection settings to enforce this.

#### Risk Statement

Without configuring the code repository to prevent pushes, including force pushes, to the default branch, there's an increased risk of unintentional or malicious changes, potential loss of code history, and compromised version control, impacting the integrity and reliability of the software development process.

### SD-3: Continuous Integration (CI) Tests

**Group:** Secure Development

#### Control Statement

Require Continuous Integration (CI) tests to pass before merging into the default branch.

#### Control Recommendations

Use GitLab's protected branch and merge request settings or GitHub's branch protection settings to enforce this.

#### Risk Statement

Failing to require passing Continuous Integration (CI) tests before merging into the default branch increases the risk of introducing faulty code, potential regressions, and compromise of code quality.

### SD-4: Static Analysis

**Group:** Secure Development

#### Control Statement

Set up a static analysis job in the [ insert: param, sd-4_prm_1 ], and remediate or risk accept true positive vulnerability findings before deploying to production.

#### Control Recommendations

Static analysis tools (such as SAST or IaC security scanners) check source code for common vulnerabilities and misconfigurations. By running static analysis tools earlier in the DevSecOps cycle, vulnerabilities can be detected and prevented from being deployed to production.

#### Risk Statement

Without setting up static analysis in the CI/CD pipeline for each merge request and addressing true positive vulnerability findings, there is an increased risk of deploying insecure code to the production branch, potentially leading to security breaches and compromise of the overall system.

#### Parameters

SD-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| sd-4_prm_1 | location (str) | The location where static analysis occurs. |

### SD-5: Dependency Scanning

**Group:** Secure Development

#### Control Statement

Schedule a scan at least every [ insert: param, sd-5_prm_1 ] day(s) in the [ insert: param, sd-5_prm_2 ] to identify the use of vulnerable software libraries.

#### Control Recommendations

Dependency scanning checks the source code for dependencies with known vulnerabilities. By running scans regularly using bots or software composition analysis (SCA) tools, vulnerabilities arising from outdated dependencies can be quickly detected and patched. Software composition analysis can be performed using tools such as Gitlab, Nexus IQ, or their equivalent, with output in a common SBOM format such as SPDX or CycloneDX.

#### Risk Statement

Failing to schedule regular dependency scanning to identify vulnerable software libraries and address findings in a timely manner increases the risk of deploying applications with known vulnerabilities, potentially exposing the system to security exploits and compromise.

#### Parameters

SD-5 Parameters

| ID | Type | Description |
| --- | --- | --- |
| sd-5_prm_1 | time period (days) (int) | The time period in days of dependency scanning frequency. |
| sd-5_prm_2 | location (str) | The location where dependency scanning occurs. |

### SD-6: Secret Detection

**Group:** Secure Development

#### Control Statement

Set up secret detection in the [ insert: param, sd-6_prm_1 ] and remediate true positives within [ insert: param, sd-6_prm_2 ] day(s).

#### Control Recommendations

Ensure that the exposed secret is revoked and purged from the Git history.

#### Risk Statement

Without setting up secret detection and addressing true positive findings promptly, there's an increased risk of exposing sensitive information, potential unauthorised access, and compromised security.

#### Parameters

SD-6 Parameters

| ID | Type | Description |
| --- | --- | --- |
| sd-6_prm_1 | location (str) | The location where secret detection occurs. |
| sd-6_prm_2 | time period (days) (int) | Number of days within which to remediate a secret detection true positive. |

### SD-7: CI Environment Variable Secrets Management

**Group:** Secure Development

#### Control Statement

Protect environment variable secrets used in CI jobs by limiting them to protected pipelines and masking them in job logs.

#### Control Recommendations

Use GitLab's CI/CD variable security settings or GitHub's encrypted secrets with the add-mask workflow command.

#### Risk Statement

Failing to protect environment variable secrets in CI jobs by limiting them to protected pipelines and masking them in job logs increases the risk of unauthorised access and exposure of sensitive information.

### SD-8: Deployment Environment Segregation

**Group:** Secure Development

#### Control Statement

Segregate production and non-production environments including applications, services, data, secrets, roles, and networks.

#### Control Recommendations

Achieve segregation using separate cloud tenant accounts for environments such as production, development, test, and staging. Account segregation enhances security by limiting exposure, simplifies resource and cost management, maintains configuration integrity, facilitates compliance and auditing and streamlines operational tasks. Deploy and operate environments as similarly as possible to enhance debugging and time-to-market.

#### Risk Statement

Failure to segregate production and non-production environments increases the risk of unauthorised access, data leaks, and denial of service attacks, as compromises in non-production environments may lead to cascading impacts on production systems.

### SD-9: Dynamic Analysis

**Group:** Secure Development

#### Control Statement

Implement dynamic analysis testing in the [ insert: param, sd-9_prm_1 ], and address or risk-accept all identified vulnerabilities before deploying to production.

#### Control Recommendations

Dynamic analysis tools (such as DAST, IAST, or fuzzing tools) test applications in runtime conditions to identify vulnerabilities that may not be apparent in static code. Integrate these tools into your CI/CD pipeline to automatically scan applications in a controlled environment. Ensure that the analysis covers various attack vectors, including input validation, authentication mechanisms, and API endpoints. Regularly update and tune your dynamic analysis tools to detect emerging threats and vulnerabilities.

#### Risk Statement

Failure to perform dynamic analysis in a controlled environment before production deployment increases the risk of undetected runtime vulnerabilities, potentially leading to security breaches, data leaks, and compromised system integrity in the production environment.

#### Parameters

SD-9 Parameters

| ID | Type | Description |
| --- | --- | --- |
| sd-9_prm_1 | location (str) | The location where static analysis occurs. |

### SD-10: Secure Software Development Lifecycle (SSDLC)

**Group:** Secure Development

#### Control Statement

Implement and maintain a secure software development lifecycle based on an industry-standard or organisation-defined framework, integrating security practices throughout all phases of application design, development, testing, deployment, and maintenance.

#### Control Recommendations

Outline a framework that defines roles, responsibilities, and accountability for security throughout the SSDLC. Existing frameworks include NIST Secure Software Development Framework (SSDF), OWASP Software Assurance Maturity Model (SAMM), and Microsoft Security Development Lifecycle (SDL).

#### Risk Statement

Failure to implement a comprehensive secure software development lifecycle increases the risk of introducing vulnerabilities throughout the development process, potentially leading to security breaches, data leaks, and compromised system integrity in production environments.

## Security Programme Management

Controls to implement cybersecurity governance, risk, and compliance processes and policies.

### PM-1: Cybersecurity Incident Management Plan

**Group:** Security Programme Management

#### Control Statement

Develop, document, and disseminate an organisation-level cybersecurity incident management plan to respond to cybersecurity incidents.

#### Control Recommendations

Refer to the NIST SP 800-61 Computer Security Incident Handling Guide for guidelines on incident planning and handling.

#### Risk Statement

Lack of a cybersecurity incident management plan increases the risk of ineffective response to cybersecurity incidents, hindering the ability to contain, mitigate, and recover from security breaches, potentially leading to extended downtime and data compromise.

### PM-2: Risk Assessment

**Group:** Security Programme Management

#### Control Statement

Develop and document a risk assessment by [ insert: param, pm-2_prm_1 ] and get it approved by [ insert: param, pm-2_prm_2 ] prior to [ insert: param, pm-2_prm_3 ] and conduct a review every [ insert: param, pm-2_prm_4 ] day(s).

#### Control Recommendations

The Risk Assessment should cover Cyber Risk, Data Risk, Resiliency Risk, Business Risk, Project Risk, Offshore Risk and other types of risk where applicable.

#### Risk Statement

Without developing and documenting risk assessment before the initial full release, there's an increased risk of overlooking potential security threats, vulnerabilities, and regulatory compliance issues, compromising the overall security posture of the system.

#### Parameters

PM-2 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pm-2_prm_1 | system owner (str) | The owner of the system. |
| pm-2_prm_2 | risk assessment approver (str) | The approver of the risk assessment. |
| pm-2_prm_3 | risk assessment use case (str) | The use case of the risk assessment. |
| pm-2_prm_4 | time period (days) (int) | The time period in days for risk assessment. |

### PM-3: System Security Plan (SSP) Development

**Group:** Security Programme Management

#### Control Statement

Develop and maintain a comprehensive System Security Plan (SSP) that accurately reflects the system characteristics and security controls in place for the organisation's systems and environments.

#### Control Recommendations

The SSP should be detailed, covering all aspects of security controls, roles, responsibilities, and operational processes. Regular updates are necessary to reflect changes in the security landscape and system evolution.

#### Risk Statement

Failure to develop a comprehensive SSP can result in inadequate documentation and security controls, leading to increased vulnerability to cyber threats and non-compliance with regulatory requirements.

### PM-4: Approval of Residual Risks

**Group:** Security Programme Management

#### Control Statement

Get acceptance and approval of the residual risks from agency's [ insert: param, pm-4_prm_1 ], and review and seek approval every [ insert: param, pm-4_prm_2 ] days.

#### Control Recommendations

Agencies should seek acceptance and approval from their [ insert: param, pm-4_prm_1 ] on the residual risks based on the SSP and inform GovTech of the approval.

#### Risk Statement

Failure to seek the right level of authority to accept and approve the residual risks regularly can lead to misalignment of the business implications and trade-offs from the controls set in the SSP with the Agency IDSC direction.

#### Parameters

PM-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pm-4_prm_1 | approving authority (str) | The authority for approval of residual risks. |
| pm-4_prm_2 | review frequency (int) | The frequency of risk review requirement. |

### PM-5: Central Submission of Approved System Security Plan (SSP)

**Group:** Security Programme Management

#### Control Statement

Submit approved SSPs centrally to maintain a unified and up-to-date repository of security plans and practices.

#### Control Recommendations

Use a central submission channel for SSPs.

#### Risk Statement

Inconsistent or decentralised submission of the SSP can lead to decreased visibility of security and compliance adoption across Government.

### PM-6: System Documentation

**Group:** Security Programme Management

#### Control Statement

Maintain detailed, up-to-date documentation of all system information and architecture.

#### Control Recommendations

Example system documentation includes architecture and network diagrams, architecture decision records, hardware and software inventories, data flows, and configurations. This documentation should be regularly reviewed and updated to reflect changes in the environment. Documentation should be accessible to relevant personnel while ensuring sensitive information is protected. Adopt documentation-as-code practices and machine-readable formats (such as Markdown, JSON, YAML, etc), to facilitate version control, collaboration, and automation in maintaining documentation.

#### Risk Statement

Comprehensive documentation of system architecture, components, configurations, and dependencies is essential for effective management, troubleshooting, and security auditing.

### PM-7: Certification

**Group:** Security Programme Management

#### Control Statement

Ensure that the Software as a Service (SaaS) provider is certified with [ insert: param, pm-7_prm_1 ].

#### Control Recommendations

Ensure that the certification is up-to-date. Avoid certifications that are only attestations without a pass/fail element.

#### Risk Statement

Third-party certification provides assurance that security controls have been properly implemented in the Software as a Service (SaaS) provider.

#### Parameters

PM-7 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pm-7_prm_1 | certifications (str) | The required certifications. |

### PM-8: SaaS Whitelisting

**Group:** Security Programme Management

#### Control Statement

Maintain and enforce a whitelist of authorised software and services.

#### Control Recommendations

Refer to a centrally-maintained whitelist or [ insert: param, pm-8_prm_1 ] for authorised SaaS.

#### Risk Statement

Failing to whitelist the SaaS may potentially expose sensitive data (e.g., Confidential (Cloud-Eligible) or SENSITIVE-HIGH data) to the risk of data breaches.

#### Parameters

PM-8 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pm-8_prm_1 | whitelisting authority (str) | The whitelisting authority. |

### PM-9: Cybersecurity Incident Response Testing

**Group:** Security Programme Management

#### Control Statement

Conduct incident response testing and exercises at least every [ insert: param, pm-9_prm_1 ] day(s) to evaluate the effectiveness of incident response plans and procedures.

#### Control Recommendations

Include tabletop exercises, simulations, and full-scale drills in testing. Involve all relevant stakeholders. Document lessons learned and use them to improve incident response capabilities. Consider engaging third-party services for objective assessment.

#### Risk Statement

Lack of regular incident response testing may lead to ineffective or delayed responses during actual security incidents, potentially increasing the impact and damage of such events.

#### Parameters

PM-9 Parameters

| ID | Type | Description |
| --- | --- | --- |
| pm-9_prm_1 | time period (days) (int) | The time period in days for cybersecurity incident response testing. |

### PM-10: Cybersecurity Leadership and Oversight

**Group:** Security Programme Management

#### Control Statement

Ensure that the board of directors (or equivalent body) and senior management team includes at least one member that has knowledge and awareness of cybersecurity matters to have oversight of cybersecurity risks.

#### Control Recommendations

Regularly assess and document the cybersecurity expertise of board members and senior management. Provide ongoing cybersecurity training and briefings to maintain and enhance their knowledge. Consider appointing a dedicated Chief Information Security Officer (CISO) or equivalent role at the senior management level. Ensure clear communication channels between the board, senior management, and cybersecurity teams.

#### Risk Statement

Lack of cybersecurity expertise at the board and senior management levels may lead to inadequate oversight and management of cybersecurity risks, potentially resulting in increased vulnerability to cyber threats and ineffective risk management strategies.

## Security Testing

Controls to validate the security of a system via internal and external testing.

### ST-1: Vulnerability Assessment

**Group:** Security Testing

#### Control Statement

Run regular [ insert: param, st-1_prm_1 ] vulnerability assessment scans for eligible hosts at least every [ insert: param, st-1_prm_2 ] day(s).

#### Control Recommendations

Select agent-based or network-based scans as necessary. Implement authenticated scans where possible for greater coverage. Use scanners such as Amazon Inspector or Microsoft Defender for Cloud for continuous scanning of cloud systems. For on-premises systems or systems that require periodic scans, subscribe to Vulnerability Management System (VMS). For SaaS, refer to past vulnerability scanning reports done by the SaaS provider, if any.

#### Risk Statement

Without regular vulnerability assessment scans, hosts remain exposed to undetected security vulnerabilities or misconfigurations, increasing the risk of exploitation and unauthorised access to critical systems.

#### Parameters

ST-1 Parameters

| ID | Type | Description |
| --- | --- | --- |
| st-1_prm_1 | type (str) | The type of vulnerability assessment scanning. |
| st-1_prm_2 | time period (days) (int) | The time period in days for vulnerability assessment scans. |

### ST-2: Cloud Security Posture Management

**Group:** Security Testing

#### Control Statement

Set up cloud security posture management that performs continuous configuration scans on cloud assets.

#### Control Recommendations

Use cloud security posture management tools such as AWS Security Hub, Azure Defender for Cloud, and Google Security Command Center.

#### Risk Statement

Lack of continuous configuration scans through cloud security posture management increases the risk of misconfigurations in cloud assets, leading to security vulnerabilities, data breaches, and unauthorised access.

### ST-3: Public Vulnerability Disclosure Programme

**Group:** Security Testing

#### Control Statement

Establish a public reporting channel for disclosing vulnerabilities in public-facing systems via [ insert: param, st-3_prm_1 ].

#### Control Recommendations

Use the [security.txt standard](https://securitytxt.org/) or add a link to the vulnerability reporting channel on all pages, such as in the footer.

#### Risk Statement

Lack of a reporting channel for vulnerabilities increases the risk of undetected and unmitigated vulnerabilities.

#### Parameters

ST-3 Parameters

| ID | Type | Description |
| --- | --- | --- |
| st-3_prm_1 | type (str) | The type of public vulnerability reporting channel. |

### ST-4: Security Testing Programme

**Group:** Security Testing

#### Control Statement

Conduct and document a [ insert: param, st-4_prm_1 ] by internal teams or independent external parties every [ insert: param, st-4_prm_2 ] day(s).

#### Control Recommendations

Refer to the WOG Security Testing Guidelines for recommendations on the conduct of security testing engagements. For systems that are permitted to use Government Bug Bounty Programme as an alternative, obtain a passing rating based on the Agency Readiness Scorecard. For SaaS, refer to past penetration testing reports done by the SaaS provider, if any.

#### Risk Statement

Without undergoing security testing, there's an increased risk of undetected security weaknesses, leaving the application susceptible to exploitation, data breaches, and unauthorised access.

#### Parameters

ST-4 Parameters

| ID | Type | Description |
| --- | --- | --- |
| st-4_prm_1 | type (str) | The type of security testing programme. |
| st-4_prm_2 | time period (days) (int) | The time period in days of penetration testing frequency. |

### ST-5: Vulnerability Management

**Group:** Security Testing

#### Control Statement

Triage, prioritise and then remediate or risk accept vulnerabilities that materially impact security within the following timeframe based on severity:

- Critical: [ insert: param, st-5_prm_1 ] day(s)
- High: [ insert: param, st-5_prm_2 ] day(s)
- Medium: [ insert: param, st-5_prm_3 ] day(s)
- Low: [ insert: param, st-5_prm_4 ] day(s)

#### Control Recommendations

Vulnerabilities that materially impact security include vulnerabilities that have a high likelihood of exploitability, or are known to be exploited. Refer to resources such as the [Known Exploited Vulnerabilities Catalog](https://www.cisa.gov/known-exploited-vulnerabilities-catalog) to prioritise vulnerabilities that should be remediated. Seek approval from the appropriate approving authority for risk acceptance. Document the actions taken.

#### Risk Statement

Failure to promptly remediate vulnerabilities increases the risk of potential exploits, security breaches, and prolonged exposure to known vulnerabilities in the system.

#### Parameters

ST-5 Parameters

| ID | Type | Description |
| --- | --- | --- |
| st-5_prm_1 | time period (days) (int) | The time period in days to remediate or risk accept critical vulnerability findings. |
| st-5_prm_2 | time period (days) (int) | The time period in days to remediate or risk accept high vulnerability findings. |
| st-5_prm_3 | time period (days) (int) | The time period in days to remediate or risk accept medium vulnerability findings. |
| st-5_prm_4 | time period (days) (int) | The time period in days to remediate or risk accept low vulnerability findings. |

## Software Supply Chain

Controls to prevent tampering and improve the integrity of the software supply chain.

### SC-1: Code Repository

**Group:** Software Supply Chain

#### Control Statement

Manage the codebase in a central code repository with version control.

#### Control Recommendations

Use common internal platforms that provide Git repository services.

#### Risk Statement

Absence of centralised code repository and version control increases the risk of code inconsistencies, loss of code history, and difficulties in collaboration, potentially leading to errors and security vulnerabilities.

### SC-2: Commit Signing

**Group:** Software Supply Chain

#### Control Statement

Configure the code repository to reject unsigned commits.

#### Control Recommendations

Use GitLab's push rules, GitHub's branch protection rules or similar code repository controls to reject unsigned commits on push.

#### Risk Statement

Allowing unsigned commits in the code repository introduces the risk of unauthorised or malicious code changes, compromising the integrity and security of the software development process.

### SC-3: Peer Review

**Group:** Software Supply Chain

#### Control Statement

Require peer review and approval by a designated reviewer before merging into the default branch.

#### Control Recommendations

Use GitLab's protected branch and merge request settings, GitHub's branch protection settings or similar code repository controls to enforce this.

#### Risk Statement

Without peer review and approval before merging, there is an increased risk of introducing undetected coding errors, security vulnerabilities, and maintaining codebase consistency may become challenging.

### SC-4: Dependency Manifest Version Pinning

**Group:** Software Supply Chain

#### Control Statement

Pin direct and transitive dependency versions in the application's dependency manifest.

#### Control Recommendations

Dependency manifests such as package-lock.json for npm and Pipfile.lock for pipenv allow you to pin dependency versions.

#### Risk Statement

Failure to pin direct and transitive dependency versions in the application's manifest may lead to version drift, introducing compatibility issues, security vulnerabilities, and unpredictability in the software environment.

### SC-5: Build and Release Process

**Group:** Software Supply Chain

#### Control Statement

Use a consistent build and release process that generates a record of how the release artefact was built and deployed.

#### Control Recommendations

Consider automated build and deploy tools such as CI/CD Pipelines, Infrastructure as Code (IaC) and other scripts, which allow for signing and validation of build artefacts. If automation is not possible, develop and implement release management processes.

#### Risk Statement

Inconsistent and unmanaged releases may lead to configuration drift, increased likelihood of errors, and unapproved changes to releases.

### SC-6: Dependency Installation during Deployment

**Group:** Software Supply Chain

#### Control Statement

Only install pinned versions in the manifest when installing dependencies during deployment.

#### Control Recommendations

Use package manager commands such as npm ci for npm and pipenv sync for pipenv that ensure only versions specified in the manifest are installed rather than the latest version.

#### Risk Statement

Failure to install only pinned versions of dependencies during deployment increases the risk of introducing unforeseen changes, compatibility issues, and potential security vulnerabilities into the deployed environment.

### SC-7: Software Artefact Signing

**Group:** Software Supply Chain

#### Control Statement

Sign software artefacts such as code and container images using a trusted source during build.

#### Control Recommendations

Use tools or services like Cosign or AWS Signer to sign and verify code.

#### Risk Statement

Unsigned code and container images pose a risk of tampering, impersonation, and the injection of malicious code during the build process, compromising the integrity and security of the deployed software.

### SC-8: Software Artefact Signature Verification

**Group:** Software Supply Chain

#### Control Statement

Verify the signatures of code and artefacts before deployment or runtime.

#### Control Recommendations

Implement a signature verification step such as a pipeline stage or Kubernetes Admission Controller.

#### Risk Statement

Without verifying the signatures of code and artefacts before deployment or runtime, there's an increased risk of deploying tampered or malicious software, compromising the integrity and security of the system.

### SC-9: Internal Code Collaboration and Sharing

**Group:** Software Supply Chain

#### Control Statement

Share source code internally to enhance code quality, accelerate innovation, and improve problem-solving efficiency.

#### Control Recommendations

Adopt InnerSource practices for internal collaboration, utilising Git platforms to manage and share code repositories internally. Source code should be evaluated for suitability for InnerSourcing, such as the use of confidential algorithms or embedded sensitive data. The InnerSource guidelines published in the Singapore Government Developer Portal provide a useful framework for code sharing.

#### Risk Statement

Restricting code repositories to closed source can result in duplicated efforts, hinder collaborative learning, and lead to missed bugs or vulnerabilities.
