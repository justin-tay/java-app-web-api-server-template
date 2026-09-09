package com.example.app.web.server.logging.client;

import java.util.Collection;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves a single normalized literal IP address from a header supplied by a trusted
 * immediate proxy.
 */
public final class TrustedHeaderClientIpResolver implements ClientIpResolver {

	private final String headerName;

	private final TrustedProxyMatcher trustedProxies;

	public TrustedHeaderClientIpResolver(String headerName, Collection<String> trustedProxyCidrs) {
		this.headerName = headerName;
		this.trustedProxies = new TrustedProxyMatcher(trustedProxyCidrs);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (!this.trustedProxies.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		return TrustedProxyMatcher.normalizeLiteralIp(request.getHeader(this.headerName));
	}

}
