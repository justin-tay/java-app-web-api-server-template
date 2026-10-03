package com.example.commons.security.authentication.oidc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.web.util.UriUtils;

/**
 * The members a {@code reauthentication-required} problem adds to tell a client how to
 * authenticate again: the method the current session logged in with, and for an OpenID
 * Connect login the URI that sends the browser to the same OpenID Provider with
 * {@code max_age=0}. A passkey login has no URI, because the client runs the WebAuthn
 * ceremony itself. A session of any other kind gets no members, and the client chooses.
 * The method is only a hint; the server accepts any login that is recent enough.
 */
public final class ReauthenticationChallenge {

	static final String METHOD = "method";

	static final String REAUTHENTICATION_URI = "reauthentication_uri";

	private ReauthenticationChallenge() {
	}

	/**
	 * Gets the members to add to the problem.
	 * @param authentication the current authentication, which may be null
	 * @return the member names and values, in order, with only characters that are safe
	 * in a JSON string
	 */
	public static Map<String, String> members(Authentication authentication) {
		Map<String, String> members = new LinkedHashMap<>();
		if (authentication instanceof OAuth2AuthenticationToken token) {
			members.put(METHOD, "oidc");
			members.put(REAUTHENTICATION_URI, "/oauth2/authorization/"
					+ UriUtils.encodePathSegment(token.getAuthorizedClientRegistrationId(), StandardCharsets.UTF_8)
					+ "?max_age=0");
		}
		else if (authentication instanceof WebAuthnAuthentication) {
			members.put(METHOD, "passkey");
		}
		return members;
	}

}
