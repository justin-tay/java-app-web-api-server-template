package com.example.commons.security.authentication.oidc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.example.commons.security.authorization.LocalAuthorityLookup;

/**
 * Loads the OIDC user from the identity provider and adds the application's local
 * {@code ROLE_} authorities from {@link LocalAuthorityLookup}, rejecting the login when
 * there is no enabled local user for the username.
 *
 * <p>
 * The username is the claim named by the client registration's
 * {@code user-name-attribute} ({@code sub} if unset, as in Spring Security), which may
 * also be a dotted path to a nested claim such as {@code xyz.preferred_username}. Spring
 * Security reads the attribute only as a top-level claim name, so a nested one is
 * resolved here, and added to the ID token as a top-level claim named by the path. The
 * user's {@link OidcUser#getName() name} is then the username, for this service and for
 * the local authority refresh alike.
 */
public class LocalAuthoritiesOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private static final Log logger = LogFactory.getLog(LocalAuthoritiesOidcUserService.class);

	private final OidcUserService delegate = new OidcUserService();

	private final LocalAuthorityLookup localAuthorityLookup;

	public LocalAuthoritiesOidcUserService(LocalAuthorityLookup localAuthorityLookup) {
		this.localAuthorityLookup = localAuthorityLookup;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
		String usernameClaim = usernameClaim(request.getClientRegistration());
		OidcUser oidcUser = this.delegate.loadUser(withoutUserNameAttribute(request));
		String username = ClaimPath.resolve(oidcUser.getClaims(), usernameClaim);
		if (username == null || username.isBlank()) {
			logger.warn("Login rejected: the '" + usernameClaim + "' claim of registration '"
					+ request.getClientRegistration().getRegistrationId() + "' is missing or not a string");
			throw unauthorized();
		}
		Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
		authorities.addAll(this.localAuthorityLookup.findAuthorities(username).orElseThrow(this::unauthorized));
		return new DefaultOidcUser(authorities, withClaim(oidcUser.getIdToken(), usernameClaim, username),
				oidcUser.getUserInfo(), usernameClaim);
	}

	private static String usernameClaim(ClientRegistration registration) {
		String userNameAttribute = registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
		return (userNameAttribute != null && !userNameAttribute.isBlank()) ? userNameAttribute : IdTokenClaimNames.SUB;
	}

	/**
	 * The delegate would reject a nested {@code user-name-attribute} as a missing claim,
	 * so it loads the user with {@code sub}, its default, and the attribute is resolved
	 * here instead.
	 */
	private static OidcUserRequest withoutUserNameAttribute(OidcUserRequest request) {
		ClientRegistration registration = ClientRegistration.withClientRegistration(request.getClientRegistration())
			.userNameAttributeName(null)
			.build();
		return new OidcUserRequest(registration, request.getAccessToken(), request.getIdToken(),
				request.getAdditionalParameters());
	}

	private static OidcIdToken withClaim(OidcIdToken idToken, String name, String value) {
		if (value.equals(idToken.getClaims().get(name))) {
			return idToken;
		}
		Map<String, Object> claims = new HashMap<>(idToken.getClaims());
		claims.put(name, value);
		return new OidcIdToken(idToken.getTokenValue(), idToken.getIssuedAt(), idToken.getExpiresAt(), claims);
	}

	private OAuth2AuthenticationException unauthorized() {
		return new OAuth2AuthenticationException(new OAuth2Error("local_user_not_authorized"));
	}

}
