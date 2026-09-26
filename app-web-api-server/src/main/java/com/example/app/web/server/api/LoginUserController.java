package com.example.app.web.server.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login user endpoint: the caller's identity from their ID token, limited to the claims
 * that describe who they are. Token metadata such as {@code nonce}, {@code sid},
 * {@code at_hash}, and {@code azp}, and any claim a provider adds later, are never
 * returned.
 */
@RestController
public class LoginUserController {

	static final List<String> CLAIMS = List.of(StandardClaimNames.SUB, StandardClaimNames.PREFERRED_USERNAME,
			StandardClaimNames.NAME, StandardClaimNames.GIVEN_NAME, StandardClaimNames.FAMILY_NAME,
			StandardClaimNames.EMAIL, StandardClaimNames.EMAIL_VERIFIED);

	@GetMapping(path = "/login-user", produces = MediaType.APPLICATION_JSON_VALUE)
	public Map<String, Object> loginUser(@AuthenticationPrincipal OidcUser principal) {
		Map<String, Object> claims = new LinkedHashMap<>();
		for (String claim : CLAIMS) {
			Object value = principal.getClaims().get(claim);
			if (value != null) {
				claims.put(claim, value);
			}
		}
		return claims;
	}

}
