# ADR 0008: Actuator on a separate, minimally exposed management port

**Status:** Accepted

## Decision

Spring Boot Actuator runs on a separate management port from the application,
exposing only a minimal, unauthenticated health check for the load balancer. No
other actuator endpoint is enabled, and the endpoints are not served under the
default `/actuator` path.

## Context

An ALB or equivalent load balancer needs an HTTP health check without exposing the
rest of the application on the same listener. Investigating how Spring Boot wires a
separate management port surfaced two behaviors that shaped this decision: it runs as
a genuinely separate embedded server, so the connector hardening applied elsewhere in
the template needed independent verification rather than being assumed to carry over;
and it still shares one Spring Security filter chain with the application, so the
health path needed an explicit permit rule rather than being reachable automatically.
`ActuatorManagementPortTest` covers both.

## Consequences

Only the health check is reachable, and only with its detail hidden. Adding any other
endpoint (`metrics`, `info`, `env`, etc.) requires adding authentication for the
management port at that time; the current design relies on nothing sensitive being
exposed, not on a credential guarding what is. Network restriction of the management
port to the load balancer's health-check path and internal ops network remains a
deployment responsibility this template cannot enforce in code. Implementation detail
is maintained in
[`docs/system-design/06-security/hardening.md`](../system-design/06-security/hardening.md#actuator-management-port).
