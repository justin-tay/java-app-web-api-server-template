package com.example.commons.security.authorization;

/**
 * The prefix Spring Security puts on an authority to mark it as a role, so that
 * {@code hasRole("ADMIN")} matches the authority {@code ROLE_ADMIN}.
 */
public final class RolePrefix {

	/**
	 * Spring Security's default role prefix, which applies because the application does
	 * not define a {@code GrantedAuthorityDefaults} bean.
	 */
	public static final String VALUE = "ROLE_";

	private RolePrefix() {
	}

}
