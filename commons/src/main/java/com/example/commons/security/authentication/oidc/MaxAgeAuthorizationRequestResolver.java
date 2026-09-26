package com.example.commons.security.authentication.oidc;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Resolves authorization requests as Spring Security does, and passes a {@code max_age}
 * query parameter of the login URI on to the OpenID Provider. {@code max_age=0} makes the
 * provider authenticate the user again, which a client does when a request is answered
 * with a {@code reauthentication-required} problem; OpenID Connect then requires the
 * provider to return the new {@code auth_time}. Any other value, or a malformed one, is
 * ignored.
 */
public class MaxAgeAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	static final String MAX_AGE = "max_age";

	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public MaxAgeAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
				OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return withMaxAge(request, this.delegate.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return withMaxAge(request, this.delegate.resolve(request, clientRegistrationId));
	}

	private static OAuth2AuthorizationRequest withMaxAge(HttpServletRequest request,
			OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null || !"0".equals(request.getParameter(MAX_AGE))) {
			return authorizationRequest;
		}
		return OAuth2AuthorizationRequest.from(authorizationRequest)
			.additionalParameters(parameters -> parameters.put(MAX_AGE, "0"))
			.build();
	}

}
