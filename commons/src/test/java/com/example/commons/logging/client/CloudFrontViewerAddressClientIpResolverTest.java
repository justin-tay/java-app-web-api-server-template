package com.example.commons.logging.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class CloudFrontViewerAddressClientIpResolverTest {

	private final CloudFrontViewerAddressClientIpResolver resolver = new CloudFrontViewerAddressClientIpResolver(
			List.of("192.0.2.0/24"));

	/**
	 * CloudFront documents only the IPv4 form, and is reported to send IPv6 as the
	 * address followed by a colon and the port, without brackets; the bracketed form is
	 * accepted too.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			198.51.100.7:46532                              | 198.51.100.7
			2001:0db8:85a3:0000:0000:8a2e:0370:7334:46532   | 2001:db8:85a3:0:0:8a2e:370:7334
			2001:db8::7:46532                               | 2001:db8:0:0:0:0:0:7
			[2001:db8::7]:46532                             | 2001:db8:0:0:0:0:0:7
			[::ffff:198.51.100.7]:443                       | 198.51.100.7
			198.51.100.7:65535                              | 198.51.100.7
			""")
	void resolvesTheViewerAddressWithoutItsPort(String viewerAddress, String expected) {
		assertThat(this.resolver.resolve(request("192.0.2.10", viewerAddress))).contains(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "198.51.100.7", "198.51.100.7:", ":46532", "198.51.100.7:port", "198.51.100.7:1:2",
			"198.51.100.7:65536", "198.51.100.7:123456", "[2001:db8::7]", "[2001:db8::7]:", "[2001:db8::7]x46532",
			"[]:46532", "[2001:db8::7:46532", "[198.51.100.7]:46532", "cafe:46532", "1.2.3:46532", "[cafe]:46532" })
	void resolvesNothingFromAMalformedViewerAddress(String viewerAddress) {
		assertThat(this.resolver.resolve(request("192.0.2.10", viewerAddress))).isEmpty();
	}

	@Test
	void resolvesNothingFromAnUntrustedPeer() {
		assertThat(this.resolver.resolve(request("203.0.113.50", "198.51.100.7:46532"))).isEmpty();
	}

	@Test
	void trustsTheHeaderFromAnyPeerWhenNoCidrsAreGiven() {
		assertThat(new CloudFrontViewerAddressClientIpResolver().resolve(request("203.0.113.50", "198.51.100.7:46532")))
			.contains("198.51.100.7");
	}

	@Test
	void resolvesNothingWithoutTheHeader() {
		assertThat(this.resolver.resolve(request("192.0.2.10", null))).isEmpty();
	}

	private static MockHttpServletRequest request(String remoteAddr, String viewerAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		if (viewerAddress != null) {
			request.addHeader("CloudFront-Viewer-Address", viewerAddress);
		}
		return request;
	}

}
