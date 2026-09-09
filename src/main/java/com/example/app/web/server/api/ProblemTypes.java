package com.example.app.web.server.api;

import java.net.URI;

/**
 * Stable RFC 9457 problem type identifiers exposed by this application.
 */
public final class ProblemTypes {

	public static final URI ACCESS_DENIED = type("access-denied");

	public static final URI CSRF_VALIDATION_FAILED = type("csrf-validation-failed");

	public static final URI HTTP_ERROR = type("http-error");

	public static final URI INTERNAL_ERROR = type("internal-error");

	public static final URI MALFORMED_REQUEST = type("malformed-request");

	public static final URI METHOD_NOT_ALLOWED = type("method-not-allowed");

	public static final URI RESOURCE_CONFLICT = type("resource-conflict");

	public static final URI RESOURCE_NOT_FOUND = type("resource-not-found");

	public static final URI ROUTE_NOT_FOUND = type("route-not-found");

	public static final URI UPSTREAM_RESPONSE_FAILED = type("upstream-response-failed");

	public static final URI VALIDATION_FAILED = type("validation-failed");

	private ProblemTypes() {
	}

	private static URI type(String kind) {
		return URI.create("urn:problem:" + kind);
	}

}
