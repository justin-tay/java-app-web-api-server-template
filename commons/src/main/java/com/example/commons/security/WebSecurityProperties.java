package com.example.commons.security;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security properties under {@code commons.security}. Their defaults are in
 * {@code commons-defaults.yaml}.
 */
@ConfigurationProperties(prefix = "commons.security")
@Validated
public class WebSecurityProperties {

	@Valid
	private final Session session = new Session();

	public Session getSession() {
		return this.session;
	}

	/**
	 * Session security properties.
	 */
	public static class Session {

		/**
		 * Maximum lifetime of a session from its creation, regardless of activity.
		 */
		@NotNull
		private Duration absoluteTimeout;

		/**
		 * Whether a change in the session's bound User-Agent invalidates it.
		 */
		private boolean hijackingProtection = true;

		/**
		 * Whether a change in the session's bound client IP is logged as an anomaly.
		 * Never invalidates the session.
		 */
		private boolean anomalyDetection = true;

		public Duration getAbsoluteTimeout() {
			return this.absoluteTimeout;
		}

		public void setAbsoluteTimeout(Duration absoluteTimeout) {
			this.absoluteTimeout = absoluteTimeout;
		}

		public boolean isHijackingProtection() {
			return this.hijackingProtection;
		}

		public void setHijackingProtection(boolean hijackingProtection) {
			this.hijackingProtection = hijackingProtection;
		}

		public boolean isAnomalyDetection() {
			return this.anomalyDetection;
		}

		public void setAnomalyDetection(boolean anomalyDetection) {
			this.anomalyDetection = anomalyDetection;
		}

	}

}
