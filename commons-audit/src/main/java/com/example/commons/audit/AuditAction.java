package com.example.commons.audit;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What an audit trail event records being done, declared once as a constant so that no
 * call site chooses its ECS classification. Its name is the row's action and the log
 * event's {@code event.action}; a refused attempt is recorded under the same action with
 * the outcome {@code failure}.
 *
 * @param name the action, such as {@code update_user}
 * @param category the ECS {@code event.category}, such as {@code [iam]}
 * @param type the ECS {@code event.type}, such as {@code [user, change]}
 * @param message the log message, such as {@code User update}; a refusal is logged as
 * {@code User update rejected}
 */
public record AuditAction(String name, List<String> category, List<String> type, String message) {

	private static final Pattern NAME = Pattern.compile("[a-z][a-z_]*");

	public AuditAction {
		if (name == null || !NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("An audit action name is lower case with underscores: " + name);
		}
		category = List.copyOf(category);
		type = List.copyOf(type);
	}

	/**
	 * Returns an identity and access management action.
	 * @param name the action
	 * @param object the object type, such as {@code user} or {@code group}
	 * @param activity the activity type, such as {@code creation}, {@code change},
	 * {@code deletion}, or {@code info}
	 * @param message the log message
	 * @return the action
	 */
	public static AuditAction iam(String name, String object, String activity, String message) {
		return new AuditAction(name, List.of("iam"), List.of(object, activity), message);
	}

	/**
	 * Returns a change to the settings the application runs under.
	 * @param name the action
	 * @param message the log message
	 * @return the action
	 */
	public static AuditAction configuration(String name, String message) {
		return new AuditAction(name, List.of("configuration"), List.of("change"), message);
	}

}
