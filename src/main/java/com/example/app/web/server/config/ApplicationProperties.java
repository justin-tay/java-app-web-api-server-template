package com.example.app.web.server.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Application properties.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "app")
@Validated
public class ApplicationProperties {

	/**
	 * Message reported when {@code app.jwks} is unset, which fails startup.
	 */
	static final String JWKS_REQUIRED_MESSAGE = "must be set to the location of this deployment's private JWKS "
			+ "(for example file:/run/secrets/jwks.json); no JWKS is packaged with the application, "
			+ "see docs/adr/0018";

	/**
	 * Resource location of the private JWKS used for {@code private_key_jwt} client
	 * authentication. Required, with no default, so that no private key material ships in
	 * the artifact.
	 */
	@NotBlank(message = JWKS_REQUIRED_MESSAGE)
	private String jwks;

	private Session session = new Session();

	public String getJwks() {
		return jwks;
	}

	public void setJwks(String jwks) {
		this.jwks = jwks;
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	/**
	 * Session security properties.
	 */
	public static class Session {

		private Duration absoluteTimeout;

		public Duration getAbsoluteTimeout() {
			return absoluteTimeout;
		}

		public void setAbsoluteTimeout(Duration absoluteTimeout) {
			this.absoluteTimeout = absoluteTimeout;
		}

	}

}
