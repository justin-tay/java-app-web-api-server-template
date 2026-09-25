# 11. Risks and Technical Debt

## Risks

<!-- arc42-generated -->
| # | Risk | Priority | Impact | Probability | Mitigation |
| --- | --- | --- | --- | --- | --- |
| 1 | The management port (8082) exposes Actuator's `/app/health` without authentication; if network-level restriction to the health-check/internal ops network is misconfigured at deploy time, this becomes an unauthenticated, internet-reachable endpoint. | High | Low (single endpoint, minimal detail) if reached | Medium (depends entirely on deployer network configuration, not enforced by the application) | Restrict the management port at the network layer (security group, firewall, ingress rule); see [ADR 0014](../adr/0014-actuator-management-port.md). Confirmed per deployment, not something the codebase can verify. |
| 2 | JDBC-backed sessions add a database round trip to every authenticated request (session read, and now also `LocalAuthorityRefreshFilter`'s authority reload). Under database latency or an outage, this directly degrades or blocks all authenticated traffic. | Medium | High (single point of failure for all authenticated requests) | Low under normal operation | Accepted trade-off in [ADR 0006](../adr/0006-jdbc-backed-server-side-sessions.md) and [ADR 0015](../adr/0015-per-request-local-authority-refresh.md) in exchange for stateless horizontal scaling and immediate authorization changes. No caching layer or read replica strategy is defined. |
| 3 | No production datasource, Keycloak realm, or TLS termination point is fixed in the codebase; every environment beyond local development is a deployment decision required that this document cannot verify was made correctly. | Medium | Medium | Medium (depends on adopter discipline) | Track via [Deployment View](07-deployment-view.md) and require the manual placeholders there to be filled in before go-live. |
| 4 | GraalVM native image support depends on `ApplicationRuntimeHints` staying in sync with actual reflection/resource usage; a future change that adds unregistered reflection will fail native compilation silently until someone runs `mvn -Pnative native:compile`, which is not part of the default CI pipeline. | Low | Medium | Medium as the codebase grows | Add a native-image build/smoke-test step to CI, or document that native builds are verified manually before release. |
<!-- /arc42-generated -->

## Technical Debt

<!-- arc42-generated -->
| # | Debt Item | Priority | Impact | Source | Remediation Plan |
| --- | --- | --- | --- | --- | --- |
| 1 | JaCoCo coverage is reported on every pull request but no minimum threshold is enforced; coverage can regress without failing CI. | Low | Medium | `pom.xml` (`jacoco-maven-plugin` has no `check` goal configured), `.github/workflows/build-and-test.yml` | Add a JaCoCo `check` execution with a minimum instruction/branch coverage rule if coverage regression becomes a real concern. |
| 2 | No container image build (Dockerfile) or deployment manifest exists in the repository; deployment automation is entirely undocumented in code. | Low (by design, this is a template) | Low | Repository-wide scan: no `Dockerfile`, `docker-compose.yml`, or Kubernetes manifests found | Adopting projects add these when they choose a concrete deployment target; out of scope for the template itself. |
| 3 | An AsciiDoc/PDF export pipeline for this system design document has been discussed but remains undesigned; no ADR tracks it. | Low | Low | `docs/system-design/` sharded-file structure | Record a fresh ADR if and when a converter/pipeline is actually chosen. |
| 4 | The GraalVM native image with `commons-aws` has been verified only by hand, once: the binary read its JWKS from AWS Secrets Manager (emulated by Floci) by name and by ARN, picked up a rotated secret on its scheduled refresh, and reported readiness DOWN for an empty secret. Decrypting an encrypted ID token (ECDH-ES through the JDK's security providers) has not been exercised in a native image, and no CI job builds or runs one, so a change that needs a new reachability hint is only found by hand. | Medium | High for a native deployment on AWS (a missing hint fails startup or the first encrypted login) | `commons-aws/pom.xml`, `commons/src/main/java/com/example/commons/CommonsRuntimeHints.java`, [ADR 0020](../adr/0020-jwks-rotation-from-aws-secrets-manager.md) | Add a CI job that builds the native image and runs it against Floci, including one login with an encrypted ID token, before deploying a native binary on AWS. |
<!-- /arc42-generated -->

<!-- arc42-manual: Add risks known to the team but not visible in the codebase (e.g. planned Keycloak version upgrades, known capacity limits of a specific deployment target, vendor lock-in concerns). -->
<!-- /arc42-manual -->
