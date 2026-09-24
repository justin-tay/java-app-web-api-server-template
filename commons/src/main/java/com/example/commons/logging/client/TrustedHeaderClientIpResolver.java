package com.example.commons.logging.client;

import java.util.Collection;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves a single literal IP address from a header that a proxy in front of the
 * application sets, such as {@code True-Client-IP} or {@code X-Real-IP}.
 *
 * <p>
 * The header is trustworthy only when the proxy that sets it replaces any value a client
 * sent, and every request reaches the application through that proxy. Construct it with
 * the trusted-proxy CIDRs of the application's immediate peer to also ignore the header
 * on a request that reaches the application from anywhere else, or without them to trust
 * the header from any peer.
 */
public final class TrustedHeaderClientIpResolver implements ClientIpResolver {

	private final String headerName;

	private final TrustedProxyMatcher trustedPeers;

	/**
	 * Creates a resolver that trusts the header from any immediate peer.
	 * @param headerName the header that holds the client address
	 */
	public TrustedHeaderClientIpResolver(String headerName) {
		this.headerName = headerName;
		this.trustedPeers = null;
	}

	/**
	 * Creates a resolver that trusts the header only when the immediate peer is in one of
	 * the given CIDR blocks.
	 * @param headerName the header that holds the client address
	 * @param trustedProxyCidrs the CIDR blocks of the trusted immediate peers
	 */
	public TrustedHeaderClientIpResolver(String headerName, Collection<String> trustedProxyCidrs) {
		this.headerName = headerName;
		this.trustedPeers = new TrustedProxyMatcher(trustedProxyCidrs);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (this.trustedPeers != null && !this.trustedPeers.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		return TrustedProxyMatcher.normalizeLiteralIp(request.getHeader(this.headerName));
	}

}
