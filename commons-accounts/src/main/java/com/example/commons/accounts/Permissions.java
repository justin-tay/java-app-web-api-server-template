package com.example.commons.accounts;

/**
 * The permissions the application checks, as {@code domain:action}, which the schema
 * seeds as rows of {@code app_permission} (see docs/adr/0038). A test fails if the two
 * differ.
 */
public final class Permissions {

	public static final String APPLICATION_ACCESS = "application:access";

	public static final String USER_READ = "user:read";

	/** Privileged. */
	public static final String USER_CREATE = "user:create";

	public static final String USER_UPDATE = "user:update";

	/** Privileged. */
	public static final String USER_ADD_ROLE = "user:add-role";

	public static final String USER_REMOVE_ROLE = "user:remove-role";

	public static final String USER_SUSPEND = "user:suspend";

	/** Privileged. */
	public static final String USER_UNSUSPEND = "user:unsuspend";

	public static final String USER_REMOVE = "user:remove";

	public static final String USER_REVOKE_SESSION = "user:revoke-session";

	public static final String USER_REMOVE_PASSKEY = "user:remove-passkey";

	public static final String ROLE_READ = "role:read";

	public static final String ROLE_CREATE = "role:create";

	public static final String ROLE_UPDATE = "role:update";

	public static final String ROLE_DELETE = "role:delete";

	/** Privileged. */
	public static final String ROLE_ADD_PERMISSION = "role:add-permission";

	public static final String ROLE_REMOVE_PERMISSION = "role:remove-permission";

	public static final String PERMISSION_READ = "permission:read";

	public static final String SETTINGS_READ = "settings:read";

	/** Privileged. */
	public static final String SETTINGS_UPDATE = "settings:update";

	public static final String AUDIT_READ = "audit:read";

	public static final String REVIEW_READ = "review:read";

	public static final String REVIEW_DECIDE = "review:decide";

	public static final String REVIEW_CONFIRM_POPULATION = "review:confirm-population";

	public static final String REVIEW_DOWNLOAD_REPORT = "review:download-report";

	private Permissions() {
	}

}
