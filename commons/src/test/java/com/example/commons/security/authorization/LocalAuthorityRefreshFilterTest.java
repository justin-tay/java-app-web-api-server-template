package com.example.commons.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.example.commons.security.session.SessionLifecycleAuditLogger;

class LocalAuthorityRefreshFilterTest {

	private final LocalAuthorityLookup localAuthorityLookup = mock(LocalAuthorityLookup.class);

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = mock(SessionLifecycleAuditLogger.class);

	private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

	private final LocalAuthorityRefreshFilter filter = new LocalAuthorityRefreshFilter(this.localAuthorityLookup,
			this.sessionLifecycleAuditLogger, this.sessionRegistry);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void replacesRoleAuthoritiesWithFreshOnesFromTheDatabaseButKeepsOtherAuthorities() throws Exception {
		when(this.localAuthorityLookup.findAuthorities("alice")).thenReturn(roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("SCOPE_openid"),
					new SimpleGrantedAuthority("ROLE_STALE_ROLE")));

		this.filter.doFilter(new MockHttpServletRequest("GET", "/accounts"), new MockHttpServletResponse(),
				(request, response) -> {
				});

		Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext()
			.getAuthentication()
			.getAuthorities();
		assertThat(authorities).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("SCOPE_openid", "ROLE_USER_MANAGE");
	}

	@Test
	void deauthenticatesAndInvalidatesTheSessionWhenTheUserIsDisabledOrDeleted() throws Exception {
		when(this.localAuthorityLookup.findAuthorities("alice")).thenReturn(Optional.empty());
		SecurityContextHolder.getContext().setAuthentication(oauthToken("alice"));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(session.isInvalid()).isTrue();
		verify(this.sessionLifecycleAuditLogger).logSessionDestroyed(session, "user_disabled_or_deleted");
	}

	@Test
	void logsAPrivilegeChangeOnceAndSavesTheRefreshedAuthenticationToTheSession() throws Exception {
		when(this.localAuthorityLookup.findAuthorities("alice")).thenReturn(roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("SCOPE_openid"),
					new SimpleGrantedAuthority("ROLE_STALE_ROLE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		verify(this.sessionLifecycleAuditLogger).logSessionPrivilegeChanged(session, "alice",
				Set.of("ROLE_USER_MANAGE"), Set.of("ROLE_STALE_ROLE"));
		SecurityContext savedContext = (SecurityContext) session
			.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(savedContext.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("SCOPE_openid", "ROLE_USER_MANAGE");
	}

	@Test
	void doesNotLogWhenTheReloadedAuthoritiesAreUnchanged() throws Exception {
		when(this.localAuthorityLookup.findAuthorities("alice")).thenReturn(roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("ROLE_USER_MANAGE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		verify(this.sessionLifecycleAuditLogger, never()).logSessionPrivilegeChanged(any(), anyString(), any(), any());
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
	}

	@Test
	void doesNotLogAPrivilegeChangeForASessionAlreadyExpiredForRevocation() throws Exception {
		when(this.localAuthorityLookup.findAuthorities("alice")).thenReturn(roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("ROLE_STALE_ROLE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);
		SessionInformation sessionInformation = new SessionInformation("alice", session.getId(), new Date());
		sessionInformation.expireNow();
		when(this.sessionRegistry.getSessionInformation(session.getId())).thenReturn(sessionInformation);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		verify(this.sessionLifecycleAuditLogger, never()).logSessionPrivilegeChanged(any(), anyString(), any(), any());
	}

	@Test
	void doesNothingWhenNotAuthenticatedAsALocalOidcUser() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		this.filter.doFilter(new MockHttpServletRequest("GET", "/accounts"), new MockHttpServletResponse(),
				(request, response) -> {
				});

		verifyNoInteractions(this.localAuthorityLookup);
	}

	private static Optional<Collection<GrantedAuthority>> roles(String... roleNames) {
		return Optional.of(Arrays.stream(roleNames)
			.<GrantedAuthority>map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
			.toList());
	}

	private OAuth2AuthenticationToken oauthToken(String preferredUsername, GrantedAuthority... authorities) {
		OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("sub", "subject", "preferred_username", preferredUsername));
		DefaultOidcUser principal = new DefaultOidcUser(List.of(authorities), idToken);
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");
	}

}
