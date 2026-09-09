package com.example.app.web.server.security;

import java.util.Collection;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the address portion of CloudFront's {@code CloudFront-Viewer-Address} header
 * when it was supplied by a trusted immediate proxy.
 */
public final class CloudFrontViewerAddressClientIpResolver implements ClientIpResolver {

	private static final String HEADER_NAME = "CloudFront-Viewer-Address";

	private final TrustedProxyMatcher trustedProxies;

	public CloudFrontViewerAddressClientIpResolver(Collection<String> trustedProxyCidrs) {
		this.trustedProxies = new TrustedProxyMatcher(trustedProxyCidrs);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (!this.trustedProxies.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		return addressFromHeader(request.getHeader(HEADER_NAME));
	}

	private Optional<String> addressFromHeader(String value) {
		if (value == null) {
			return Optional.empty();
		}
		if (value.startsWith("[")) {
			int closingBracket = value.indexOf(']');
			if (closingBracket < 2 || closingBracket + 2 >= value.length() || value.charAt(closingBracket + 1) != ':'
					|| !value.substring(closingBracket + 2).matches("[0-9]+")) {
				return Optional.empty();
			}
			return TrustedProxyMatcher.normalizeLiteralIp(value.substring(1, closingBracket));
		}
		int separator = value.indexOf(':');
		if (separator < 1 || separator != value.lastIndexOf(':') || separator == value.length() - 1
				|| !value.substring(separator + 1).matches("[0-9]+")) {
			return Optional.empty();
		}
		return TrustedProxyMatcher.normalizeLiteralIp(value.substring(0, separator));
	}

}
