package com.example.commons.audit;

/**
 * What an audit trail event is about. Its full name is kept in the row for recognition
 * and is never logged.
 *
 * @param type the target type, such as {@code USER} or {@code SETTING}
 * @param id the target's public ID, or null
 * @param name the target's name, or null
 * @param fullName the target's full name, or null
 * @param user whether the name is a username, which the log event lists in
 * {@code related.user}
 */
public record AuditTarget(String type, String id, String name, String fullName, boolean user) {

	public AuditTarget {
		if (type == null || type.isBlank()) {
			throw new IllegalArgumentException("An audit target needs a type.");
		}
	}

	/**
	 * Returns a target that is not a user.
	 * @param type the target type
	 * @param id the target's ID, or null
	 * @param name the target's name, or null
	 * @return the target
	 */
	public static AuditTarget of(String type, Object id, String name) {
		return new AuditTarget(type, id == null ? null : id.toString(), name, null, false);
	}

	/**
	 * Returns a target named by a username.
	 * @param type the target type
	 * @param id the target's ID, or null
	 * @param username the username, or null
	 * @param fullName the user's full name, or null
	 * @return the target
	 */
	public static AuditTarget user(String type, Object id, String username, String fullName) {
		return new AuditTarget(type, id == null ? null : id.toString(), username, fullName, username != null);
	}

}
