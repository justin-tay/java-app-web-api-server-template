package com.example.app.web.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Web security configuration.
 *
 * <p>
 * The commons module's {@code WebSecurityAutoConfiguration} has already applied the
 * shared security baseline (OIDC login, headers, session management, audit logging,
 * Problem Details responses, and the health and JWKS endpoint rules) to the
 * {@link HttpSecurity} this method receives, so this chain holds only this application's
 * own authorization rules. Spring Security denies any request no rule matches; the final
 * rule grants every other request to an authenticated user.
 */
@Configuration(proxyBeanMethods = false)
public class WebSecurityConfiguration {

	/**
	 * Configure the security filter chain.
	 * @param http the http security
	 * @return the security filter chain
	 * @throws Exception the exception
	 */
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/users/**"))
				.hasRole("USER_MANAGE")
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/groups/**"))
				.hasRole("GROUP_MANAGE")
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/admin/roles/**"))
				.hasAuthority("ROLE_ROLE_MANAGE"))
			.authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
				.requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/**"))
				.authenticated())
			.build();
	}

}
