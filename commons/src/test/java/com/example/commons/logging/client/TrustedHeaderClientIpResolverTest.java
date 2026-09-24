package com.example.commons.logging.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedHeaderClientIpResolverTest {

	private final TrustedHeaderClientIpResolver resolver = new TrustedHeaderClientIpResolver("True-Client-IP",
			List.of("192.0.2.0/24"));

	@Test
	void resolvesTheHeaderFromATrustedPeer() {
		assertThat(this.resolver.resolve(request("192.0.2.10", "198.51.100.7"))).contains("198.51.100.7");
	}

	@Test
	void resolvesNothingFromAnUntrustedPeer() {
		assertThat(this.resolver.resolve(request("203.0.113.50", "198.51.100.7"))).isEmpty();
	}

	@Test
	void trustsTheHeaderFromAnyPeerWhenNoCidrsAreGiven() {
		assertThat(new TrustedHeaderClientIpResolver("True-Client-IP").resolve(request("203.0.113.50", "198.51.100.7")))
			.contains("198.51.100.7");
	}

	@Test
	void resolvesNothingFromAPeerThatIsNotALiteralAddress() {
		assertThat(this.resolver.resolve(request("cafe", "198.51.100.7"))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "198.51.100.7, 203.0.113.9", "198.51.100.7:443", "cafe", "1.2.3", "" })
	void resolvesNothingFromAValueThatIsNotASingleLiteralAddress(String value) {
		assertThat(this.resolver.resolve(request("192.0.2.10", value))).isEmpty();
	}

	private static MockHttpServletRequest request(String remoteAddr, String value) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		request.addHeader("True-Client-IP", value);
		return request;
	}

}
