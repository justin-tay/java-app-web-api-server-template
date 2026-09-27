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

	@Valid
	private final Csrf csrf = new Csrf();

	public Session getSession() {
		return this.session;
	}

	public Csrf getCsrf() {
		return this.csrf;
	}

	/**
	 * CSRF protection properties.
	 */
	public static class Csrf {

		/**
		 * Whether the CSRF token is also sent to the browser as a cookie that frontend
		 * JavaScript reads and echoes in the {@code X-XSRF-TOKEN} header. Off by default,
		 * where the token is kept only in the session.
		 */
		private boolean cookieEnabled = false;

		public boolean isCookieEnabled() {
			return this.cookieEnabled;
		}

		public void setCookieEnabled(boolean cookieEnabled) {
			this.cookieEnabled = cookieEnabled;
		}

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

		public Duration getAbsoluteTimeout() {
			return this.absoluteTimeout;
		}

		public void setAbsoluteTimeout(Duration absoluteTimeout) {
			this.absoluteTimeout = absoluteTimeout;
		}

	}

}
