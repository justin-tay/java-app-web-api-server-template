package com.example.commons.security.oauth2;

import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches an OpenID Provider's discovery document with configurable connect and read
 * timeouts and, for a provider with a
 * {@code commons.security.oauth2.client.provider.<id>.ssl-bundle}, the trust material of
 * that bundle. Spring Security's {@code ClientRegistrations} offers neither: its timeouts
 * are fixed at 30 seconds and its HTTP client cannot be configured (spring-security#14176
 * and #14777, both declined). A failure is reported as a {@link DiscoveryException} that
 * says whether retrying can help.
 */
public class OidcDiscoveryClient implements MetadataFetcher {

	private static final String WELL_KNOWN_PATH = "/.well-known/openid-configuration";

	private static final ParameterizedTypeReference<Map<String, Object>> METADATA_TYPE = new ParameterizedTypeReference<>() {
	};

	private final Map<String, RestClient> restClients = new ConcurrentHashMap<>();

	private final Duration connectTimeout;

	private final Duration readTimeout;

	private final Function<String, SslBundle> sslBundles;

	public OidcDiscoveryClient(Duration connectTimeout, Duration readTimeout) {
		this(connectTimeout, readTimeout, issuerUri -> null);
	}

	/**
	 * Creates the client.
	 * @param connectTimeout the connect timeout
	 * @param readTimeout the read timeout
	 * @param sslBundles gets the bundle for an issuer URI, or null for the JVM's default
	 * trust
	 */
	public OidcDiscoveryClient(Duration connectTimeout, Duration readTimeout, Function<String, SslBundle> sslBundles) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.sslBundles = sslBundles;
	}

	@Override
	public Map<String, Object> fetch(String issuerUri) {
		String metadataUri = (issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri)
				+ WELL_KNOWN_PATH;
		Map<String, Object> metadata;
		try {
			metadata = restClient(issuerUri).get().uri(metadataUri).retrieve().body(METADATA_TYPE);
		}
		catch (HttpServerErrorException ex) {
			throw new DiscoveryException(false, "HTTP " + ex.getStatusCode().value(), ex);
		}
		catch (HttpStatusCodeException ex) {
			int status = ex.getStatusCode().value();
			throw new DiscoveryException(status != 408 && status != 429, "HTTP " + status, ex);
		}
		catch (ResourceAccessException ex) {
			if (rejectsCertificate(ex)) {
				throw new DiscoveryException(true, "the provider's TLS certificate is not trusted", ex);
			}
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

	private RestClient restClient(String issuerUri) {
		return this.restClients.computeIfAbsent(issuerUri,
				key -> RestClient.builder()
					.requestFactory(ProviderTrust.requestFactory(this.sslBundles.apply(key), this.connectTimeout,
							this.readTimeout))
					.build());
	}

	/**
	 * Whether the TLS handshake failed because the provider's certificate is not trusted
	 * or does not match the host, which retrying cannot fix, rather than because the
	 * connection broke, which it can.
	 */
	private static boolean rejectsCertificate(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof CertificateException || cause instanceof SSLPeerUnverifiedException) {
				return true;
			}
		}
		return false;
	}

}
