package com.example.app.web.server.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Renders a {@link Throwable}'s stack trace in the standard JDK format (the same
 * {@code Caused by:}/{@code ... N more} folding {@link Throwable#printStackTrace()}
 * produces), but with every message in the cause chain, including suppressed exceptions,
 * treated as absent.
 * <p>
 * This exists for code paths that need an exception's class names and stack frames for
 * debugging, but cannot review whether its message is safe to log; a message can echo
 * unsanitized request content. Only {@link Throwable#getClass()} and
 * {@link Throwable#getStackTrace()} are read from the original exception; its message is
 * never called or copied anywhere.
 */
public final class MessageRedactedStackTraces {

	private MessageRedactedStackTraces() {
	}

	/**
	 * Renders {@code throwable} exactly as {@link Throwable#printStackTrace()} would,
	 * except that its message, and the message of every cause and suppressed exception,
	 * is omitted.
	 * @param throwable the exception to render; must not be {@code null}
	 * @return the rendered stack trace, with no message text anywhere in it
	 */
	public static String stackTraceWithoutMessages(Throwable throwable) {
		Objects.requireNonNull(throwable, "throwable");
		StringWriter stringWriter = new StringWriter();
		redact(throwable).printStackTrace(new PrintWriter(stringWriter));
		return stringWriter.toString();
	}

	private static Throwable redact(Throwable original) {
		if (original == null) {
			return null;
		}
		RedactedThrowable redacted = new RedactedThrowable(original.getClass().getName(), redact(original.getCause()));
		redacted.setStackTrace(original.getStackTrace());
		for (Throwable suppressed : original.getSuppressed()) {
			redacted.addSuppressed(redact(suppressed));
		}
		return redacted;
	}

	/**
	 * Stands in for the original exception during rendering. {@link #toString()} reports
	 * the original class name so {@code printStackTrace()} output is indistinguishable
	 * from the original's, apart from the missing message; the message passed to
	 * {@link Throwable#Throwable(String, Throwable)} is always {@code null}, so
	 * {@link Throwable#toString()}'s own message-appending logic is never reached.
	 */
	private static final class RedactedThrowable extends Throwable {

		private static final long serialVersionUID = 1L;

		private final String originalClassName;

		RedactedThrowable(String originalClassName, Throwable cause) {
			super(null, cause);
			this.originalClassName = originalClassName;
		}

		@Override
		public String toString() {
			return this.originalClassName;
		}

	}

}
