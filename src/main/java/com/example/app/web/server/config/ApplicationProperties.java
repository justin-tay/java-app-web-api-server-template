package com.example.app.web.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Application properties.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

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
