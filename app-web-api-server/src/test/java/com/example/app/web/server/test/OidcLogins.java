package com.example.app.web.server.test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * OIDC logins for MockMvc tests whose username is the {@code preferred_username} claim,
 * the {@code user-name-attribute} of the application's provider, which is what the
 * application reads the principal's name from. {@code oidcLogin()} alone names the
 * principal by {@code sub}.
 */
public final class OidcLogins {

	private OidcLogins() {
	}

	public static RequestPostProcessor oidcLoginAs(String username) {
		return oidcLoginAs(username, null);
	}

	public static RequestPostProcessor oidcLoginAs(String username, Instant authTime) {
		Instant now = Instant.now();
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", "test-subject");
		claims.put("preferred_username", username);
		if (authTime != null) {
			claims.put("auth_time", authTime);
		}
		OidcIdToken idToken = new OidcIdToken("token", now, now.plusSeconds(300), claims);
		return oidcLogin().oidcUser(
				new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken, "preferred_username"));
	}

}
