package com.example.commons.logging.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the client address from an {@code X-Forwarded-For} chain.
 *
 * <p>
 * Each proxy that forwards the request appends the address it received the request from,
 * so the chain reads from the client on the left to the application's immediate peer on
 * the right. Entries a client sends in its own {@code X-Forwarded-For} stay on the left
 * unless a proxy replaces the header, so which entry is the client depends on the
 * deployment's proxies. Choose the strategy that matches them:
 * <ul>
 * <li>{@link #XForwardedForClientIpResolver(Collection) trusted proxy CIDRs}: the
 * rightmost entry that is not one of the deployment's proxies. Every proxy that appends
 * to the header must be in the CIDR blocks.</li>
 * <li>{@link #fromRight(int) a fixed number of entries from the right}: for a fixed
 * topology, such as 1 behind an Application Load Balancer alone, or 2 behind CloudFront
 * and then an Application Load Balancer, both of which append.</li>
 * <li>{@link #leftmost() the leftmost entry}: only when the edge proxy replaces the
 * header with the address it received the request from, as nginx does with
 * {@code proxy_set_header X-Forwarded-For $remote_addr}. CloudFront and Application Load
 * Balancers append to a client's header instead, so behind them the leftmost entry is
 * whatever the client sent.</li>
 * </ul>
 *
 * <p>
 * Entries to the left of the selected one are never parsed, so a malformed value a client
 * prepends cannot prevent resolution. An entry may carry a port, as an Application Load
 * Balancer with client port preservation writes it, and the port is dropped.
 */
public final class XForwardedForClientIpResolver implements ClientIpResolver {

	private static final String HEADER_NAME = "X-Forwarded-For";

	private final TrustedProxyMatcher trustedPeers;

	private final EntrySelector selector;

	/**
	 * Creates a resolver that trusts the header only when the immediate peer is in one of
	 * the given CIDR blocks, and resolves the rightmost entry that is not.
	 * @param trustedProxyCidrs the CIDR blocks of every proxy that appends to the header
	 */
	public XForwardedForClientIpResolver(Collection<String> trustedProxyCidrs) {
		this.trustedPeers = new TrustedProxyMatcher(trustedProxyCidrs);
		this.selector = rightmostNotIn(this.trustedPeers);
	}

	private XForwardedForClientIpResolver(TrustedProxyMatcher trustedPeers, EntrySelector selector) {
		this.trustedPeers = trustedPeers;
		this.selector = selector;
	}

	/**
	 * Creates a resolver that trusts the header from any immediate peer and resolves the
	 * entry the given number of places from the right.
	 * @param proxies the number of the deployment's proxies that append to the header, so
	 * that 1 selects the rightmost entry
	 * @return the resolver
	 */
	public static XForwardedForClientIpResolver fromRight(int proxies) {
		return new XForwardedForClientIpResolver(null, fromRightSelector(proxies));
	}

	/**
	 * Creates a resolver that trusts the header only when the immediate peer is in one of
	 * the given CIDR blocks, and resolves the entry the given number of places from the
	 * right.
	 * @param proxies the number of the deployment's proxies that append to the header, so
	 * that 1 selects the rightmost entry
	 * @param trustedPeerCidrs the CIDR blocks of the trusted immediate peers
	 * @return the resolver
	 */
	public static XForwardedForClientIpResolver fromRight(int proxies, Collection<String> trustedPeerCidrs) {
		return new XForwardedForClientIpResolver(new TrustedProxyMatcher(trustedPeerCidrs), fromRightSelector(proxies));
	}

	/**
	 * Creates a resolver that trusts the header from any immediate peer and resolves its
	 * leftmost entry. Use it only when the edge proxy replaces the header.
	 * @return the resolver
	 */
	public static XForwardedForClientIpResolver leftmost() {
		return new XForwardedForClientIpResolver(null, XForwardedForClientIpResolver::leftmostEntry);
	}

	/**
	 * Creates a resolver that trusts the header only when the immediate peer is in one of
	 * the given CIDR blocks, and resolves its leftmost entry. Use it only when the edge
	 * proxy replaces the header.
	 * @param trustedPeerCidrs the CIDR blocks of the trusted immediate peers
	 * @return the resolver
	 */
	public static XForwardedForClientIpResolver leftmost(Collection<String> trustedPeerCidrs) {
		return new XForwardedForClientIpResolver(new TrustedProxyMatcher(trustedPeerCidrs),
				XForwardedForClientIpResolver::leftmostEntry);
	}

	@Override
	public Optional<String> resolve(HttpServletRequest request) {
		if (this.trustedPeers != null && !this.trustedPeers.matches(request.getRemoteAddr())) {
			return Optional.empty();
		}
		List<String> entries = entries(request.getHeaders(HEADER_NAME));
		return entries.isEmpty() ? Optional.empty() : this.selector.select(entries);
	}

	private static EntrySelector rightmostNotIn(TrustedProxyMatcher proxies) {
		return entries -> {
			for (int index = entries.size() - 1; index >= 0; index--) {
				Optional<String> address = AddressWithPort.optional(entries.get(index));
				if (address.isEmpty()) {
					return Optional.empty();
				}
				if (!proxies.matches(address.get())) {
					return address;
				}
			}
			return Optional.empty();
		};
	}

	private static EntrySelector fromRightSelector(int proxies) {
		if (proxies < 1) {
			throw new IllegalArgumentException("The number of proxies must be at least 1");
		}
		return entries -> {
			if (entries.size() < proxies) {
				return Optional.empty();
			}
			for (int index = entries.size() - 1; index > entries.size() - proxies; index--) {
				if (AddressWithPort.optional(entries.get(index)).isEmpty()) {
					return Optional.empty();
				}
			}
			return AddressWithPort.optional(entries.get(entries.size() - proxies));
		};
	}

	private static Optional<String> leftmostEntry(List<String> entries) {
		return AddressWithPort.optional(entries.get(0));
	}

	/**
	 * Splits every {@code X-Forwarded-For} header line, in order, into its entries.
	 */
	private static List<String> entries(Enumeration<String> headerValues) {
		List<String> entries = new ArrayList<>();
		while (headerValues.hasMoreElements()) {
			for (String entry : headerValues.nextElement().split(",", -1)) {
				entries.add(entry.strip());
			}
		}
		return entries;
	}

	/**
	 * Selects the client address from a non-empty chain, left to right, returning empty
	 * when the entries it relies on are malformed.
	 */
	@FunctionalInterface
	private interface EntrySelector {

		Optional<String> select(List<String> entries);

	}

}
