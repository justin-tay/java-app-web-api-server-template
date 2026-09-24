package com.example.commons.security.authorization;

import java.io.IOException;
import java.util.Collection;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.commons.security.session.SessionLifecycleAuditLogger;

/**
 * Reloads the authenticated user's {@code ROLE_} authorities from the local user, group,
 * and role model, through {@link LocalAuthorityLookup}, on every request, instead of
 * trusting the authorities computed once at login and cached in the session. A local user
 * who has been disabled or deleted since login is deauthenticated immediately, rather
 * than continuing to act under stale authorities for the rest of the session's lifetime.
 *
 * This is a defense-in-depth backstop, not the primary revocation path: an administrator
 * disabling or deleting a user, or changing their group membership, already triggers
 * immediate revocation through {@code SessionRevocationService}. This filter additionally
 * covers any authorization-relevant change that revocation does not enumerate (for
 * example, redefining a group's role set, or deleting a role), and guards against a
 * session outliving its user for any other reason.
 * <p>
 * When the reloaded {@code ROLE_} authorities differ from the ones the session holds, and
 * the session is not already expired for revocation, the change is logged as a
 * {@code privilege_change} session event and the refreshed authentication is saved to the
 * session, so the change is logged once rather than on every later request.
 */
public class LocalAuthorityRefreshFilter extends OncePerRequestFilter {

	private final LocalAuthorityLookup localAuthorityLookup;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	private final SessionRegistry sessionRegistry;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public LocalAuthorityRefreshFilter(LocalAuthorityLookup localAuthorityLookup,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, SessionRegistry sessionRegistry) {
		this.localAuthorityLookup = localAuthorityLookup;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
		this.sessionRegistry = sessionRegistry;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof OAuth2AuthenticationToken oauthToken
				&& oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
			refresh(request, response, oauthToken, oidcUser);
		}
		filterChain.doFilter(request, response);
	}

	private void refresh(HttpServletRequest request, HttpServletResponse response, OAuth2AuthenticationToken oauthToken,
			OidcUser oidcUser) {
		String username = oidcUser.getClaimAsString("preferred_username");
		Collection<GrantedAuthority> localAuthorities = (username != null)
				? this.localAuthorityLookup.findAuthorities(username).orElse(null) : null;
		if (localAuthorities == null) {
			deauthenticate(request);
			return;
		}
		Set<GrantedAuthority> authorities = oidcUser.getAuthorities()
			.stream()
			.filter(authority -> !authority.getAuthority().startsWith("ROLE_"))
			.collect(Collectors.toCollection(HashSet::new));
		authorities.addAll(localAuthorities);
		OidcUser refreshedUser = new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(),
				"preferred_username");
		OAuth2AuthenticationToken refreshedToken = new OAuth2AuthenticationToken(refreshedUser, authorities,
				oauthToken.getAuthorizedClientRegistrationId());
		refreshedToken.setDetails(oauthToken.getDetails());
		SecurityContextHolder.getContext().setAuthentication(refreshedToken);
		Set<String> previousRoles = roleNames(oauthToken.getAuthorities());
		Set<String> currentRoles = roleNames(authorities);
		if (!previousRoles.equals(currentRoles)) {
			privilegeChanged(request, response, username, previousRoles, currentRoles);
		}
	}

	private void privilegeChanged(HttpServletRequest request, HttpServletResponse response, String username,
			Set<String> previousRoles, Set<String> currentRoles) {
		HttpSession session = request.getSession(false);
		if (session == null || isExpired(session)) {
			return;
		}
		Set<String> added = new HashSet<>(currentRoles);
		added.removeAll(previousRoles);
		Set<String> removed = new HashSet<>(previousRoles);
		removed.removeAll(currentRoles);
		this.sessionLifecycleAuditLogger.logSessionPrivilegeChanged(session, username, added, removed);
		this.securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
	}

	private boolean isExpired(HttpSession session) {
		SessionInformation sessionInformation = this.sessionRegistry.getSessionInformation(session.getId());
		return sessionInformation != null && sessionInformation.isExpired();
	}

	private static Set<String> roleNames(Collection<? extends GrantedAuthority> authorities) {
		return authorities.stream()
			.map(GrantedAuthority::getAuthority)
			.filter(authority -> authority.startsWith("ROLE_"))
			.collect(Collectors.toSet());
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
