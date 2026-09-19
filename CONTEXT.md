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
