package com.example.commons.logging;

/**
 * Names of the MDC keys that one component writes and others read, so each name is
 * spelled once and a typo cannot silently drop a correlation field from the logs.
 */
public final class LoggingContextKeys {

	public static final String USER_NAME = "user.name";

	public static final String HTTP_REQUEST_ID = "http.request.id";

	public static final String SOURCE_IP = "source.ip";

	public static final String CLIENT_IP = "client.ip";

	private LoggingContextKeys() {
	}

}
