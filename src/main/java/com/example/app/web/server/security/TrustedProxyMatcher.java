package com.example.app.web.server.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

final class TrustedProxyMatcher {

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

	static Optional<String> normalizeLiteralIp(String value) {
		if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) {
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
