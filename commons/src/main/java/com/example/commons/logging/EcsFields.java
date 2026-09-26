package com.example.commons.logging;

/**
 * Names of the ECS fields this application writes to log events and to the MDC, so each
 * name is spelled once. {@code event.category} and {@code event.type} are always written
 * as lists, as ECS defines them.
 */
public final class EcsFields {

	public static final String EVENT_CATEGORY = "event.category";

	public static final String EVENT_TYPE = "event.type";

	public static final String EVENT_ACTION = "event.action";

	public static final String EVENT_OUTCOME = "event.outcome";

	public static final String EVENT_REASON = "event.reason";

	public static final String USER_NAME = "user.name";

	public static final String HTTP_REQUEST_ID = "http.request.id";

	public static final String SOURCE_IP = "source.ip";

	public static final String CLIENT_IP = "client.ip";

	private EcsFields() {
	}

}
