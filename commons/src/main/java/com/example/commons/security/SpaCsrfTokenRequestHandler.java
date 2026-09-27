package com.example.commons.security;

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Resolves the CSRF token a single-page application sends in the {@code X-XSRF-TOKEN}
 * header as the plain value it read from the {@code XSRF-TOKEN} cookie, and any token
 * sent as a request parameter (a rendered form) as the BREACH-protected encoded value.
 */
class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

	private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
		this.xor.handle(request, response, csrfToken);
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		CsrfTokenRequestHandler handler = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName())) ? this.plain
				: this.xor;
		return handler.resolveCsrfTokenValue(request, csrfToken);
	}

}
