# ADR 0025: JSON logout URL for single-page applications

## Status

Accepted

## Context

Logout ended the application's session and answered with a 302 to Keycloak's
`end_session_endpoint`, which a browser navigation follows. A single-page
application calls logout with `fetch`, which cannot follow that redirect: it is
cross-origin, and it carries none of Keycloak's cookies. The application's
session ended, but Keycloak's did not, so the next login signed the user
straight back in.

Submitting a form to `/logout` instead would navigate the browser, but with
`csrf.spa()` a token in a form field is read as the encoded value, and the
application only has the plain value from the `XSRF-TOKEN` cookie.

## Decision

`POST /logout` from a client that accepts `application/json` and not
`text/html` is answered with `200` and `{"logoutUrl": "..."}`, the URL the
redirect would have pointed to, and no redirect. The application then navigates
the browser to that URL, so Keycloak sees its own cookies, ends its session, and
redirects to the post-logout URI. Any other request is redirected as before.

The post-logout URI is `commons.security.logout.post-logout-redirect-uri`,
defaulting to the login page's logout message; a single-page application sets
its own route, which must also be a valid post logout redirect URI of the
Keycloak client.

A user who logged in with a passkey has no Keycloak session to end, so the URL
is the application's own and nothing more happens at Keycloak.

## Consequences

The frontend has one more step after logout: it must follow `logoutUrl`, or
the Keycloak session stays alive. Back-channel logout still ends the
application's session when the user logs out at Keycloak first.
