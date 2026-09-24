package com.example.commons.logging.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TrustedProxyMatcherTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			192.0.2.10          | 192.0.2.10
			0.0.0.0             | 0.0.0.0
			255.255.255.255     | 255.255.255.255
			2001:db8::1         | 2001:db8:0:0:0:0:0:1
			2001:DB8::1         | 2001:db8:0:0:0:0:0:1
			::1                 | 0:0:0:0:0:0:0:1
			::ffff:192.0.2.10   | 192.0.2.10
			""")
	void normalizesALiteralIpAddress(String value, String expected) {
		assertThat(TrustedProxyMatcher.normalizeLiteralIp(value)).contains(expected);
	}

	/**
	 * Only a literal IP address is accepted. A value that {@code InetAddress} would treat
	 * as a host name, such as one made only of hexadecimal letters, must be rejected
	 * without a DNS lookup, and a legacy or ambiguous IPv4 form must be rejected rather
	 * than rewritten to a different address.
	 */
	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "cafe", "dead.beef", "localhost", "1.2.3", "12345", "010.001.001.001", "192.0.2.010",
			"256.0.0.1", "192.0.2.10.1", "192.0.2.", ".192.0.2.10", "::1:", "[::1]", "::1%1", "fe80::1%eth0",
			" 192.0.2.10", "192.0.2.10 ", "2001:db8::1:192.0.2.1.1",
			"0000:0000:0000:0000:0000:0000:0000:0000:0000:0000:0000:0000" })
	void rejectsAnythingButALiteralIpAddress(String value) {
		assertThat(TrustedProxyMatcher.normalizeLiteralIp(value)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			192.0.2.0/24     | 192.0.2.0         | true
			192.0.2.0/24     | 192.0.2.255       | true
			192.0.2.0/24     | 192.0.3.0         | false
			192.0.2.0/24     | 192.0.1.255       | false
			192.0.16.0/20    | 192.0.31.255      | true
			192.0.16.0/20    | 192.0.32.0        | false
			192.0.16.0/20    | 192.0.15.255      | false
			192.0.2.10/32    | 192.0.2.10        | true
			192.0.2.10/32    | 192.0.2.11        | false
			0.0.0.0/0        | 203.0.113.9       | true
			0.0.0.0/0        | 2001:db8::1       | false
			192.0.2.0/24     | ::ffff:192.0.2.10 | true
			2001:db8::/32    | 2001:db8:ffff::1  | true
			2001:db8::/32    | 2001:db9::1       | false
			2001:db8::/32    | 192.0.2.10        | false
			::/0             | 2001:db8::1       | true
			192.0.2.0/24     | cafe              | false
			192.0.2.0/24     | 192.0.2           | false
			""")
	void matchesAddressesInsideTheTrustedCidrBlocksOnly(String cidr, String address, boolean expected) {
		assertThat(new TrustedProxyMatcher(List.of(cidr)).matches(address)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "192.0.2.0", "192.0.2.0/", "192.0.2.0/24/8", "192.0.2.0/-1", "192.0.2.0/33",
			"2001:db8::/129", "192.0.2.0/abc", "cafe/16", "192.0.2/24", "/24" })
	void rejectsAnInvalidTrustedProxyCidr(String cidr) {
		assertThatIllegalArgumentException().isThrownBy(() -> new TrustedProxyMatcher(List.of(cidr)));
	}

}
