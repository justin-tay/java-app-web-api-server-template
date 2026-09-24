package com.example.commons.logging.client;

import java.util.Collection;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the viewer address from CloudFront's {@code CloudFront-Viewer-Address} header,
 * which CloudFront sets from the viewer's TCP connection as {@code address:port}.
 *
 * <p>
 * CloudFront sends the header only when it is added to the distribution's origin request
 * policy. The header is trustworthy only when every request reaches the application
 * through CloudFront, so the deployment must restrict its origin, for example an
 * Application Load Balancer, to CloudFront. That is a network control this resolver
 * cannot verify: a request that bypasses CloudFront but reaches the load balancer still
 * arrives from the load balancer.
 *
 * <p>
 * Construct it with the trusted-proxy CIDRs of the application's immediate peer, such as
 * the load balancer's subnets, to also ignore the header on a request that reaches the
 * application from anywhere else, or without them to trust the header from any peer.
 */
public final class CloudFrontViewerAddressClientIpResolver implements ClientIpResolver {

	private static final String HEADER_NAME = "CloudFront-Viewer-Address";

	private final TrustedProxyMatcher trustedPeers;

	/**
	 * Creates a resolver that trusts the header from any immediate peer, for a deployment
	 * where only CloudFront, through the application's own load balancer, can reach the
	 * application.
	 */
	public CloudFrontViewerAddressClientIpResolver() {
		this.trustedPeers = null;
	}

	/**
	 * Creates a resolver that trusts the header only when the immediate peer is in one of
	 * the given CIDR blocks.
	 * @param trustedProxyCidrs the CIDR blocks of the trusted immediate peers
	 */
	public CloudFrontViewerAddressClientIpResolver(Collection<String> trustedProxyCidrs) {
		this.trustedPeers = new TrustedProxyMatcher(trustedProxyCidrs);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (this.trustedPeers != null && !this.trustedPeers.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		return AddressWithPort.required(request.getHeader(HEADER_NAME));
	}

}
