# Security documentation

These documents describe the template's implemented security posture and the
deployment or product decisions an adopter must still make. They are not a
security certification.

| Topic | Document |
| --- | --- |
| OIDC authentication, tokens, and logout | [Authentication](authentication.md) |
| Local users, groups, roles, and authorization | [Authorization](authorization.md) |
| Server-side session security | [Sessions](sessions.md) |
| HTTP response security headers | [Headers](headers.md) |
| Error-response disclosure policy | [Error responses](error-responses.md) |
| Servlet-container and runtime hardening | [Hardening](hardening.md) |
| OWASP ASVS verification crosswalk | [ASVS crosswalk](asvs.md) |
| ECS security and request logging | [Logging](logging/README.md) |

Durable technical choices are recorded separately in
[Architecture Decision Records](../adr/README.md).
