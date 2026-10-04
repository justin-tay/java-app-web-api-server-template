package com.example.commons.security.authorization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
 * {@code privilege_change} session event, naming the added and removed roles by their
 * stored names, and the refreshed authentication is saved to the session, so the change
 * is logged once rather than on every later request.
 * <p>
 * Each way of logging in is handled by a {@link LocalAuthorityRefresher}: OpenID Connect
 * always, and any others passed to the constructor, such as passkeys.
 */
public class LocalAuthorityRefreshFilter extends OncePerRequestFilter {

	private final LocalAuthorityLookup localAuthorityLookup;

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger;

	private final SessionRegistry sessionRegistry;

	private final List<LocalAuthorityRefresher> refreshers;

	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public LocalAuthorityRefreshFilter(LocalAuthorityLookup localAuthorityLookup,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, SessionRegistry sessionRegistry) {
		this(localAuthorityLookup, sessionLifecycleAuditLogger, sessionRegistry, List.of());
	}

	public LocalAuthorityRefreshFilter(LocalAuthorityLookup localAuthorityLookup,
			SessionLifecycleAuditLogger sessionLifecycleAuditLogger, SessionRegistry sessionRegistry,
			List<LocalAuthorityRefresher> additionalRefreshers) {
		this.localAuthorityLookup = localAuthorityLookup;
		this.sessionLifecycleAuditLogger = sessionLifecycleAuditLogger;
		this.sessionRegistry = sessionRegistry;
		List<LocalAuthorityRefresher> refreshers = new ArrayList<>();
		refreshers.add(new OidcLocalAuthorityRefresher());
		refreshers.addAll(additionalRefreshers);
		this.refreshers = List.copyOf(refreshers);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null) {
			for (LocalAuthorityRefresher refresher : this.refreshers) {
				if (refresher.supports(authentication)) {
					refresh(request, response, authentication, refresher);
					break;
				}
			}
		}
		filterChain.doFilter(request, response);
	}

	private void refresh(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
			LocalAuthorityRefresher refresher) {
		String username = refresher.username(authentication);
		Collection<GrantedAuthority> localAuthorities = (username != null)
				? this.localAuthorityLookup.findAuthorities(username).orElse(null) : null;
		if (localAuthorities == null) {
			deauthenticate(request);
			return;
		}
		Authentication refreshed = refresher.refresh(authentication, localAuthorities);
		SecurityContextHolder.getContext().setAuthentication(refreshed);
		Set<String> previousRoles = roleNames(authentication.getAuthorities());
		Set<String> currentRoles = roleNames(refreshed.getAuthorities());
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
		Set<String> added = storedRoleNames(currentRoles, previousRoles);
		Set<String> removed = storedRoleNames(previousRoles, currentRoles);
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
			.filter(authority -> authority.startsWith(RolePrefix.VALUE))
			.collect(Collectors.toSet());
	}

	/**
	 * Returns the {@code ROLE_} authorities in {@code authorities} that are not in
	 * {@code excluded}, as the stored role names the audit log uses, without the prefix.
	 */
	private static Set<String> storedRoleNames(Set<String> authorities, Set<String> excluded) {
		return authorities.stream()
			.filter(authority -> !excluded.contains(authority))
			.map(authority -> authority.substring(RolePrefix.VALUE.length()))
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
