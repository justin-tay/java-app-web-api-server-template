package com.example.commons.security;

/**
 * The browser login page and the page shown after logout, used by the core security
 * baseline and by OIDC login.
 */
final class LoginPaths {

	static final String LOGIN_PAGE_URI = "/login";

	static final String LOGOUT_SUCCESS_URI = LOGIN_PAGE_URI + "?logout";

	private LoginPaths() {
	}

}
