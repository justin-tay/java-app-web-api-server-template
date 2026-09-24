package com.example.commons.logging.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class XForwardedForClientIpResolverTest {

	/**
	 * A chain behind CloudFront and then an Application Load Balancer, both of which
	 * append: the client's own forged entry, the viewer address CloudFront saw, and the
	 * CloudFront edge address the load balancer saw.
	 */
	private static final String CLOUDFRONT_THEN_ALB = "6.6.6.6, 198.51.100.7, 130.176.1.23";

	@Nested
	class TrustedProxyCidrs {

		private final XForwardedForClientIpResolver resolver = new XForwardedForClientIpResolver(
				List.of("192.0.2.0/24", "130.176.0.0/16", "2001:db8:ffff::/48"));

		@Test
		void resolvesTheRightmostAddressThatIsNotATrustedProxy() {
			assertThat(this.resolver.resolve(request("192.0.2.10", CLOUDFRONT_THEN_ALB))).contains("198.51.100.7");
		}

		@Test
		void readsEveryHeaderLineInOrder() {
			MockHttpServletRequest request = request("192.0.2.10", "198.51.100.7");
			request.addHeader("X-Forwarded-For", "203.0.113.9, 192.0.2.20");

			assertThat(this.resolver.resolve(request)).contains("203.0.113.9");
		}

		@Test
		void normalizesAnIpv6ClientAddress() {
			assertThat(this.resolver.resolve(request("192.0.2.10", "2001:DB8::7, 2001:db8:ffff::1")))
				.contains("2001:db8:0:0:0:0:0:7");
		}

		@Test
		void resolvesNothingFromAnUntrustedPeer() {
			assertThat(this.resolver.resolve(request("203.0.113.50", "198.51.100.7"))).isEmpty();
		}

		@Test
		void resolvesNothingWhenEveryAddressIsATrustedProxy() {
			assertThat(this.resolver.resolve(request("192.0.2.10", "192.0.2.20, 130.176.1.23"))).isEmpty();
		}

		/**
		 * A client cannot suppress its own address by prepending an entry that is not an
		 * IP address, because entries left of the selected one are never parsed.
		 */
		@Test
		void ignoresMalformedEntriesTheClientPrepended() {
			assertThat(this.resolver.resolve(request("192.0.2.10", "cafe, x, , 198.51.100.7, 130.176.1.23")))
				.contains("198.51.100.7");
		}

		@Test
		void resolvesNothingWhenAProxyEntryIsMalformed() {
			assertThat(this.resolver.resolve(request("192.0.2.10", "198.51.100.7, 1.2.3, 130.176.1.23"))).isEmpty();
			assertThat(this.resolver.resolve(request("192.0.2.10", "198.51.100.7,,130.176.1.23"))).isEmpty();
		}

	}

	@Nested
	class FromRight {

		@Test
		void resolvesTheViewerBehindCloudFrontAndAnApplicationLoadBalancer() {
			assertThat(XForwardedForClientIpResolver.fromRight(2).resolve(request("10.0.1.5", CLOUDFRONT_THEN_ALB)))
				.contains("198.51.100.7");
		}

		@Test
		void resolvesTheClientBehindAnApplicationLoadBalancerAlone() {
			assertThat(XForwardedForClientIpResolver.fromRight(1).resolve(request("10.0.1.5", "6.6.6.6, 198.51.100.7")))
				.contains("198.51.100.7");
		}

		@Test
		void resolvesNothingFromAChainShorterThanTheProxyCount() {
			assertThat(XForwardedForClientIpResolver.fromRight(2).resolve(request("10.0.1.5", "198.51.100.7")))
				.isEmpty();
		}

		@Test
		void ignoresMalformedEntriesTheClientPrepended() {
			assertThat(XForwardedForClientIpResolver.fromRight(2)
				.resolve(request("10.0.1.5", "unknown, 198.51.100.7, 130.176.1.23"))).contains("198.51.100.7");
		}

		@Test
		void resolvesNothingWhenTheSelectedOrAProxyEntryIsMalformed() {
			assertThat(XForwardedForClientIpResolver.fromRight(2)
				.resolve(request("10.0.1.5", "198.51.100.7, unknown, 130.176.1.23"))).isEmpty();
			assertThat(XForwardedForClientIpResolver.fromRight(2)
				.resolve(request("10.0.1.5", "198.51.100.7, 130.176.1.23, unknown"))).isEmpty();
		}

		@Test
		void checksThePeerWhenCidrsAreGiven() {
			XForwardedForClientIpResolver resolver = XForwardedForClientIpResolver.fromRight(2, List.of("10.0.0.0/16"));

			assertThat(resolver.resolve(request("10.0.1.5", CLOUDFRONT_THEN_ALB))).contains("198.51.100.7");
			assertThat(resolver.resolve(request("203.0.113.50", CLOUDFRONT_THEN_ALB))).isEmpty();
		}

		@Test
		void requiresAtLeastOneProxy() {
			assertThatIllegalArgumentException().isThrownBy(() -> XForwardedForClientIpResolver.fromRight(0));
		}

	}

	@Nested
	class Leftmost {

		@Test
		void resolvesTheEntryAnEdgeThatReplacesTheHeaderWrote() {
			assertThat(XForwardedForClientIpResolver.leftmost().resolve(request("10.0.1.5", "198.51.100.7, 10.0.2.9")))
				.contains("198.51.100.7");
		}

		/**
		 * Behind an edge that appends, such as CloudFront, the leftmost entry is whatever
		 * the client sent, which is why this strategy is only for an edge that replaces
		 * the header.
		 */
		@Test
		void returnsWhateverTheClientSentBehindAnEdgeThatAppends() {
			assertThat(XForwardedForClientIpResolver.leftmost().resolve(request("10.0.1.5", CLOUDFRONT_THEN_ALB)))
				.contains("6.6.6.6");
		}

		@Test
		void resolvesNothingFromAMalformedLeftmostEntry() {
			assertThat(XForwardedForClientIpResolver.leftmost().resolve(request("10.0.1.5", "cafe, 198.51.100.7")))
				.isEmpty();
		}

		@Test
		void checksThePeerWhenCidrsAreGiven() {
			XForwardedForClientIpResolver resolver = XForwardedForClientIpResolver.leftmost(List.of("10.0.0.0/16"));

			assertThat(resolver.resolve(request("10.0.1.5", "198.51.100.7"))).contains("198.51.100.7");
			assertThat(resolver.resolve(request("203.0.113.50", "198.51.100.7"))).isEmpty();
		}

	}

	/**
	 * An Application Load Balancer with client port preservation enabled writes the port
	 * after the address, bracketing an IPv6 address.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			12.34.56.78:8080                                   | 12.34.56.78
			[2001:db8:85a3:8d3:1319:8a2e:370:7348]:8080        | 2001:db8:85a3:8d3:1319:8a2e:370:7348
			[2001:db8::7]                                      | 2001:db8:0:0:0:0:0:7
			2001:db8:85a3::8a2e:370:7334                       | 2001:db8:85a3:0:0:8a2e:370:7334
			""")
	void dropsThePortAnApplicationLoadBalancerAdds(String entry, String expected) {
		assertThat(XForwardedForClientIpResolver.fromRight(1).resolve(request("10.0.1.5", entry))).contains(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			12.34.56.78:
			12.34.56.78:99999
			[2001:db8::7]x8080
			[12.34.56.78]:8080
			""")
	void resolvesNothingFromAMalformedPortEntry(String entry) {
		assertThat(XForwardedForClientIpResolver.fromRight(1).resolve(request("10.0.1.5", entry))).isEmpty();
	}

	@Test
	void resolvesNothingWithoutTheHeader() {
		assertThat(XForwardedForClientIpResolver.fromRight(1).resolve(request("10.0.1.5", null))).isEmpty();
	}

	private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}

}
