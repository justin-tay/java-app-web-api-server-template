# Java App Web API Server Template

This is the project's shared vocabulary: canonical terms for concepts that
were worded inconsistently across the documentation, so future contributions
(human or AI) use one term per concept instead of reinventing synonyms.

## Language

**Delegated to \<system\>**:
A capability the application never implements at all; a specific external
system (currently only the identity provider, Keycloak) owns it entirely,
and there is no decision left for the application or its deployer to make.
_Avoid_: "deployment decision" for this case, "handled externally"

**Deployment decision required**:
A one-time infrastructure, topology, or configuration choice that the team
deploying this application must actively make; the template deliberately
leaves it unset.
_Avoid_: "deployment decision" (without "required"), "deployment concerns"

**Deployment responsibility**:
An ongoing operational duty the deploying environment must carry out
continuously (for example, restricting access to logs, protecting log
transit), as distinct from a one-time choice.
_Avoid_: "deployment decision required" for this case (that is a one-time
choice, not an ongoing duty)

**Product decision required**:
A business or feature-policy choice (for example, when to require
reauthentication) that is independent of infrastructure, and that this
template does not make on the adopter's behalf.
_Avoid_: "deployment decision" for this case (that is an infra/ops choice,
not a business/policy one)

**Control implementation**:
A document or section that maps an external standard's or catalog's
requirements (OWASP ASVS, an OWASP cheat sheet, the CIS Tomcat Benchmark) to
this template's actual implementation status, per requirement. Named after
OSCAL's Component Definition model, which uses this exact term for a
component describing how it satisfies a control catalog. See
[ADR 0003](docs/adr/0003-control-implementation-terminology.md).
_Avoid_: "crosswalk" (that term properly means mapping two different
standards to each other, not a standard to an implementation), "OWASP
review", "recommendation matrix"

**Commons module**:
A shared Maven module that backends in the repository depend on: `commons`,
which every backend depends on, and optional ones named `commons-<concern>` (the
planned `commons-accounts`). It applies its behavior through Spring Boot
auto-configuration when it is on the classpath and is secure by default. Its
code lives under `com.example.commons`, with one subpackage and matching
property prefix per concern, such as `com.example.commons.security` and
`commons.security.*`. See
[ADR 0019](docs/adr/0019-shared-commons-auto-configuration.md).
_Avoid_: "library" or "starter" (nothing is published), "core", "common",
"shared module" as a proper name

**App module**:
A deployable Spring Boot backend named `app-<name>` (currently
`app-web-api-server`), in a `com.example.app.<name>` package that is a sibling
of, never a parent of, a commons module's package.
_Avoid_: "service" or "example app" as a module name, "backend module"
