package com.example.commons.logging.client;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses a literal IP address that is followed by a port, as proxies write it into
 * forwarding headers, and returns the normalized address without the port.
 */
final class AddressWithPort {

	private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

	private AddressWithPort() {
	}

	/**
	 * Parses {@code address:port}, where the port is required. This is the
	 * {@code CloudFront-Viewer-Address} format. An IPv6 address may be enclosed in
	 * brackets, {@code [2001:db8::7]:46532}, or not, {@code 2001:db8::7:46532}, which is
	 * how CloudFront is reported to send it; an unbracketed value is split at its last
	 * colon, because the header always ends with the port.
	 * @param value the header value
	 * @return the normalized address, or empty when the value is malformed
	 */
	static Optional<String> required(String value) {
		if (value == null) {
			return Optional.empty();
		}
		if (value.startsWith("[")) {
			return bracketed(value, true);
		}
		int separator = value.lastIndexOf(':');
		if (separator < 1 || !isPort(value.substring(separator + 1))) {
			return Optional.empty();
		}
		return TrustedProxyMatcher.normalizeLiteralIp(value.substring(0, separator));
	}

	/**
	 * Parses an address with an optional port. This is the format of an
	 * {@code X-Forwarded-For} entry: a literal address, or, when an Application Load
	 * Balancer's client port preservation is enabled, {@code 192.0.2.10:8080} or
	 * {@code [2001:db8::7]:8080}. An unbracketed value with more than one colon is an
	 * IPv6 address without a port.
	 * @param value the entry
	 * @return the normalized address, or empty when the entry is malformed
	 */
	static Optional<String> optional(String value) {
		if (value == null) {
			return Optional.empty();
		}
		if (value.startsWith("[")) {
			return bracketed(value, false);
		}
		int separator = value.indexOf(':');
		if (separator >= 0 && separator == value.lastIndexOf(':')) {
			if (!isPort(value.substring(separator + 1))) {
				return Optional.empty();
			}
			return TrustedProxyMatcher.normalizeLiteralIp(value.substring(0, separator));
		}
		return TrustedProxyMatcher.normalizeLiteralIp(value);
	}

	private static Optional<String> bracketed(String value, boolean portRequired) {
		int closingBracket = value.indexOf(']');
		if (closingBracket < 2) {
			return Optional.empty();
		}
		String rest = value.substring(closingBracket + 1);
		boolean validPort = rest.startsWith(":") && isPort(rest.substring(1));
		if (!(validPort || (!portRequired && rest.isEmpty()))) {
			return Optional.empty();
		}
		String address = value.substring(1, closingBracket);
		return (address.indexOf(':') >= 0) ? TrustedProxyMatcher.normalizeLiteralIp(address) : Optional.empty();
	}

	private static boolean isPort(String value) {
		return PORT.matcher(value).matches() && Integer.parseInt(value) <= 65535;
	}

}
