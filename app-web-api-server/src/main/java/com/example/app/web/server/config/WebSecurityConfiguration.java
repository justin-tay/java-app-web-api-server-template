package com.example.app.web.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.example.commons.security.authorization.LocalAuthorities;

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
 *
 * <p>
 * Which permission an endpoint needs is checked by {@code @PreAuthorize} on its
 * controller method (see docs/adr/0038). The rules here are only a coarse gate in front
 * of it: a caller who holds no permission of an API's domain, such as no {@code user:*}
 * permission for {@code /admin/users/**}, is refused with 403 before the check that a
 * change needs a recent login ({@code AdminReauthenticationInterceptor}) can ask them to
 * sign in again, which would be pointless for something they may not do.
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
			.authorizeHttpRequests(
					authorizeHttpRequests -> authorizeHttpRequests.requestMatchers(path("/admin/users/**"))
						.access(holdsAPermissionOf("user"))
						.requestMatchers(path("/admin/roles/**"))
						.access(holdsAPermissionOf("role"))
						.requestMatchers(path("/admin/permissions/**"))
						.access(holdsAPermissionOf("permission"))
						.requestMatchers(path("/admin/settings/**"))
						.access(holdsAPermissionOf("settings"))
						.requestMatchers(path("/account-reviews/**"), path("/tasks/**"))
						.access(holdsAPermissionOf("review"))
						.requestMatchers(path("/audit-events/**"))
						.access(holdsAPermissionOf("audit")))
			.authorizeHttpRequests(
					authorizeHttpRequests -> authorizeHttpRequests.requestMatchers(path("/**")).authenticated())
			.build();
	}

	private static PathPatternRequestMatcher path(String pattern) {
		return PathPatternRequestMatcher.withDefaults().matcher(pattern);
	}

	/**
	 * Admits a caller who holds at least one permission of the domain, such as
	 * {@code user:read} or {@code user:create} for {@code user}.
	 */
	private static AuthorizationManager<RequestAuthorizationContext> holdsAPermissionOf(String domain) {
		String prefix = domain + ":";
		return (authentication, context) -> {
			var current = authentication.get();
			boolean holds = current != null && current.isAuthenticated()
					&& current.getAuthorities()
						.stream()
						.filter(LocalAuthorities::isLocal)
						.map(GrantedAuthority::getAuthority)
						.anyMatch(authority -> authority.startsWith(prefix));
			return new AuthorizationDecision(holds);
		};
	}

}
