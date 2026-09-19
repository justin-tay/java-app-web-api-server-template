package com.example.app.web.server.security.authorization;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.app.web.server.domain.AppUser;
import com.example.app.web.server.domain.AppUserRepository;
import com.example.app.web.server.security.session.SessionLifecycleAuditLogger;

/**
 * Reloads the authenticated user's {@code ROLE_} authorities from the local user, group,
 * and role model on every request, instead of trusting the authorities computed once at
 * login and cached in the session. A local user who has been disabled or deleted since
 * login is deauthenticated immediately, rather than continuing to act under stale
 * authorities for the rest of the session's lifetime.
 *
 * This is a defense-in-depth backstop, not the primary revocation path: an administrator
 * disabling or deleting a user, or changing their group membership, already triggers
 * immediate revocation through {@code SessionRevocationService}. This filter additionally
 * covers any authorization-relevant change that revocation does not enumerate (for
 * example, redefining a group's role set, or deleting a role), and guards against a
 * session outliving its user for any other reason.
 */
public class LocalAuthorityRefreshFilter extends OncePerRequestFilter {

	private final AppUserRepository users;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	public LocalAuthorityRefreshFilter(AppUserRepository users,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger) {
		this.users = users;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof OAuth2AuthenticationToken oauthToken
				&& oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
			refresh(request, oauthToken, oidcUser);
		}
		filterChain.doFilter(request, response);
	}

	private void refresh(HttpServletRequest request, OAuth2AuthenticationToken oauthToken, OidcUser oidcUser) {
		String username = oidcUser.getClaimAsString("preferred_username");
		AppUser user = (username != null) ? this.users.findByUsernameAndEnabledTrue(username).orElse(null) : null;
		if (user == null) {
			deauthenticate(request);
			return;
		}
		Set<GrantedAuthority> authorities = oidcUser.getAuthorities()
			.stream()
			.filter(authority -> !authority.getAuthority().startsWith("ROLE_"))
			.collect(Collectors.toCollection(HashSet::new));
		user.getGroups()
			.stream()
			.flatMap(group -> group.getRoles().stream())
			.map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
			.forEach(authorities::add);
		OidcUser refreshedUser = new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(),
				"preferred_username");
		OAuth2AuthenticationToken refreshedToken = new OAuth2AuthenticationToken(refreshedUser, authorities,
				oauthToken.getAuthorizedClientRegistrationId());
		refreshedToken.setDetails(oauthToken.getDetails());
		SecurityContextHolder.getContext().setAuthentication(refreshedToken);
	}

	private void deauthenticate(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			this.sessionLifecycleAuditLogger.logSessionDestroyed(session, "user_disabled_or_deleted");
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
	}

}
