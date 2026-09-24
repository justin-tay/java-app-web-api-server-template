package com.example.commons.logging;

/**
 * Produces the {@code url.path} value that log events record for a request path.
 * <p>
 * A path segment can carry path parameters after a {@code ;}, such as
 * {@code /login-user;jsessionid=<session ID>}. The servlet container keeps them in the
 * raw request URI, so logging that URI unchanged would write a session ID, or whatever
 * else a client put there, to the logs. Every path parameter section (from a {@code ;} to
 * the next {@code /} or the end of the path) is replaced with {@code ;[REDACTED]}, the
 * same marker the request logging uses for redacted query values, so the event still
 * shows that path parameters were present without recording them. See the "Sensitive-data
 * policy" in
 * docs/system-design/08-crosscutting-concepts/06-logging-and-monitoring/schema.md.
 */
public final class LoggedUrlPath {

	/**
	 * Marker that replaces a redacted value in a log event.
	 */
	public static final String REDACTED_VALUE = "[REDACTED]";

	private LoggedUrlPath() {
	}

	/**
	 * Returns the path with each path parameter section replaced by {@code ;[REDACTED]}.
	 * @param path the raw request path, such as
	 * {@code HttpServletRequest.getRequestURI()}
	 * @return the path safe to log, or {@code null} if {@code path} is {@code null}
	 */
	public static String of(String path) {
		if (path == null || path.indexOf(';') < 0) {
			return path;
		}
		StringBuilder logged = new StringBuilder(path.length());
		int index = 0;
		while (index < path.length()) {
			int parameters = path.indexOf(';', index);
			if (parameters < 0) {
				logged.append(path, index, path.length());
				break;
			}
			logged.append(path, index, parameters).append(';').append(REDACTED_VALUE);
			int nextSegment = path.indexOf('/', parameters);
			if (nextSegment < 0) {
				break;
			}
			index = nextSegment;
		}
		return logged.toString();
	}

}
