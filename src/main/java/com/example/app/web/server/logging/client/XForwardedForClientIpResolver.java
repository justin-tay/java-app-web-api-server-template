package com.example.app.web.server.logging.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the first non-proxy IP address from an {@code X-Forwarded-For} chain supplied
 * by a trusted immediate proxy.
 */
public final class XForwardedForClientIpResolver implements ClientIpResolver {

	private static final String HEADER_NAME = "X-Forwarded-For";

	private final TrustedProxyMatcher trustedProxies;

	public XForwardedForClientIpResolver(Collection<String> trustedProxyCidrs) {
		this.trustedProxies = new TrustedProxyMatcher(trustedProxyCidrs);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (!this.trustedProxies.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		List<String> addresses = forwardedAddresses(request);
		for (int index = addresses.size() - 1; index >= 0; index--) {
			String address = addresses.get(index);
			if (!this.trustedProxies.matches(address)) {
				return Optional.of(address);
			}
		}
		return Optional.empty();
	}

	private List<String> forwardedAddresses(HttpServletRequest request) {
		List<String> addresses = new ArrayList<>();
		var headerValues = request.getHeaders(HEADER_NAME);
		while (headerValues.hasMoreElements()) {
			String[] values = headerValues.nextElement().split(",", -1);
			for (String value : values) {
				Optional<String> address = TrustedProxyMatcher.normalizeLiteralIp(value.strip());
				if (address.isEmpty()) {
					return List.of();
				}
				addresses.add(address.get());
			}
		}
		return addresses;
	}

}
