package com.example.commons.logging.client;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class TrustedProxyMatcher {

	private static final String IPV4_OCTET = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";

	private static final Pattern DOTTED_QUAD_IPV4 = Pattern
		.compile(IPV4_OCTET + "\\." + IPV4_OCTET + "\\." + IPV4_OCTET + "\\." + IPV4_OCTET);

	private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:.]+");

	private final List<CidrBlock> trustedProxyCidrs;

	TrustedProxyMatcher(Collection<String> trustedProxyCidrs) {
		this.trustedProxyCidrs = trustedProxyCidrs.stream().map(CidrBlock::new).toList();
	}

	boolean matches(String address) {
		return normalizeLiteralIp(address).map(this::matchesNormalized).orElse(false);
	}

	private boolean matchesNormalized(String normalizedAddress) {
		return this.trustedProxyCidrs.stream().anyMatch(cidr -> cidr.matches(normalizedAddress));
	}

	/**
	 * Normalizes a literal IP address, or returns empty for anything else.
	 *
	 * <p>
	 * The value is validated before {@code InetAddress} sees it, because
	 * {@code InetAddress.getByName} resolves a value it cannot parse as a literal as a
	 * host name, so a header value made only of hexadecimal letters and dots, such as
	 * {@code cafe}, would otherwise cause a DNS lookup on the request thread. It also
	 * accepts legacy IPv4 forms, such as {@code 1.2.3} and {@code 010.1.1.1}, and
	 * rewrites them to a different address than another parser might read. Only a
	 * dotted-quad IPv4 address without leading zeros, or a value containing a colon,
	 * which {@code InetAddress} parses only as an IPv6 literal and never resolves, is
	 * accepted.
	 * @param value the candidate address
	 * @return the normalized address, or empty when the value is not a literal IP address
	 */
	static Optional<String> normalizeLiteralIp(String value) {
		if (value == null || value.length() > 45) {
			return Optional.empty();
		}
		boolean ipv6 = value.indexOf(':') >= 0;
		if (ipv6 ? !IPV6_CHARACTERS.matcher(value).matches() : !DOTTED_QUAD_IPV4.matcher(value).matches()) {
			return Optional.empty();
		}
		try {
			InetAddress address = InetAddress.getByName(value);
			return Optional.of(address.getHostAddress());
		}
		catch (UnknownHostException ex) {
			return Optional.empty();
		}
	}

	private static final class CidrBlock {

		private final byte[] address;

		private final int prefixLength;

		private CidrBlock(String value) {
			String[] parts = value.split("/", -1);
			if (parts.length != 2) {
				throw new IllegalArgumentException("A trusted proxy CIDR must include a prefix length");
			}
			String normalizedAddress = normalizeLiteralIp(parts[0])
				.orElseThrow(() -> new IllegalArgumentException("Trusted proxy CIDR has an invalid IP address"));
			try {
				this.address = InetAddress.getByName(normalizedAddress).getAddress();
			}
			catch (UnknownHostException ex) {
				throw new IllegalArgumentException("Trusted proxy CIDR has an invalid IP address", ex);
			}
			try {
				this.prefixLength = Integer.parseInt(parts[1]);
			}
			catch (NumberFormatException ex) {
				throw new IllegalArgumentException("Trusted proxy CIDR has an invalid prefix length", ex);
			}
			if (this.prefixLength < 0 || this.prefixLength > this.address.length * Byte.SIZE) {
				throw new IllegalArgumentException("Trusted proxy CIDR prefix length is out of range");
			}
		}

		private boolean matches(String candidate) {
			try {
				byte[] candidateAddress = InetAddress.getByName(candidate).getAddress();
				if (this.address.length != candidateAddress.length) {
					return false;
				}
				int fullBytes = this.prefixLength / Byte.SIZE;
				for (int index = 0; index < fullBytes; index++) {
					if (this.address[index] != candidateAddress[index]) {
						return false;
					}
				}
				int remainingBits = this.prefixLength % Byte.SIZE;
				if (remainingBits == 0) {
					return true;
				}
				int mask = 0xFF << (Byte.SIZE - remainingBits);
				return (this.address[fullBytes] & mask) == (candidateAddress[fullBytes] & mask);
			}
			catch (UnknownHostException ex) {
				return false;
			}
		}

	}

}
