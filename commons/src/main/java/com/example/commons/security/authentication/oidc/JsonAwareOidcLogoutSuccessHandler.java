package com.example.commons.security.authentication.oidc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import com.example.commons.security.ContentNegotiation;

/**
 * OpenID Connect RP-initiated logout that a single-page application can use. A browser
 * navigation is redirected to the provider's {@code end_session_endpoint}, as
 * {@link OidcClientInitiatedLogoutSuccessHandler} does. A client that asks for JSON, such
 * as a {@code fetch} call, cannot follow that redirect (it is cross-origin, and carries
 * none of the provider's cookies), so it is answered with {@code 200} and
 * {@code {"logoutUrl": "..."}} instead, and the application then navigates the browser to
 * that URL, which ends the provider's session (see docs/adr/0025).
 */
public class JsonAwareOidcLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

	public JsonAwareOidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
		super(clientRegistrationRepository);
	}

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException, ServletException {
		if (!ContentNegotiation.prefersJson(request)) {
			super.onLogoutSuccess(request, response, authentication);
			return;
		}
		String logoutUrl = determineTargetUrl(request, response, authentication);
		response.setStatus(HttpStatus.OK.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8);
		response.getWriter().write("{\"logoutUrl\":\"" + escape(logoutUrl) + "\"}");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
