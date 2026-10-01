package com.example.commons.security.oauth2;

import java.time.Duration;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties for the discovery of an OpenID Provider's metadata from a client
 * registration's {@code issuer-uri}, which happens on first use rather than at startup
 * (see docs/adr/0029).
 */
@ConfigurationProperties(prefix = "commons.security.oauth2.discovery")
@Validated
public class OidcDiscoveryProperties {

	/**
	 * How long to wait after a failed discovery before trying again. Requests arriving in
	 * the meantime fail at once with 503 rather than each waiting on the provider.
	 */
	@DurationMin(seconds = 1)
	@DurationMax(hours = 1)
	private Duration retryInterval = Duration.ofSeconds(30);

	/**
	 * Timeout for connecting to the discovery endpoint.
	 */
	@DurationMin(millis = 100)
	@DurationMax(minutes = 1)
	private Duration connectTimeout = Duration.ofSeconds(2);

	/**
	 * Timeout for reading the discovery response.
	 */
	@DurationMin(millis = 100)
	@DurationMax(minutes = 1)
	private Duration readTimeout = Duration.ofSeconds(5);

	public Duration getRetryInterval() {
		return this.retryInterval;
	}

	public void setRetryInterval(Duration retryInterval) {
		this.retryInterval = retryInterval;
	}

	public Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

}
