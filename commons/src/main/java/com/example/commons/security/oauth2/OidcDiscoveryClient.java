package com.example.commons.security.oauth2;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches an OpenID Provider's discovery document with connect and read timeouts, which
 * Spring Security's {@code ClientRegistrations} does not offer. A failure is reported as
 * a {@link DiscoveryException} that says whether retrying can help.
 */
public class OidcDiscoveryClient implements MetadataFetcher {

	private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

	private static final ParameterizedTypeReference<Map<String, Object>> METADATA_TYPE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	public OidcDiscoveryClient(Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public Map<String, Object> fetch(String issuerUri) {
		String metadataUri = (issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri)
				+ WELL_KNOWN_PATH;
		Map<String, Object> metadata;
		try {
			metadata = this.restClient.get().uri(metadataUri).retrieve().body(METADATA_TYPE);
		}
		catch (HttpServerErrorException ex) {
			throw new DiscoveryException(false, "HTTP " + ex.getStatusCode().value(), ex);
		}
		catch (HttpStatusCodeException ex) {
			int status = ex.getStatusCode().value();
			throw new DiscoveryException(status != 408 && status != 429, "HTTP " + status, ex);
		}
		catch (ResourceAccessException ex) {
			throw new DiscoveryException(false, "the provider is not reachable", ex);
		}
		catch (RestClientException ex) {
			throw new DiscoveryException(true, "the response is not a JSON object", ex);
		}
		if (metadata == null || !issuerUri.equals(metadata.get("issuer"))) {
			throw new DiscoveryException(true, "the issuer in the metadata does not match the configured issuer-uri",
					null);
		}
		return metadata;
	}

}
