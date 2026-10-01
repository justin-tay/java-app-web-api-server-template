package com.example.commons.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.example.commons.security.session.SessionLifecycleAuditLogger;

@ExtendWith(OutputCaptureExtension.class)
class LocalAuthorityRefreshFilterTest {

	/**
	 * The local users' authorities by username; a username that is absent is a disabled
	 * or deleted user.
	 */
	private final Map<String, Collection<GrantedAuthority>> localUsers = new HashMap<>();

	private final List<String> lookedUp = new ArrayList<>();

	private final LocalAuthorityLookup localAuthorityLookup = (username) -> {
		this.lookedUp.add(username);
		return Optional.ofNullable(this.localUsers.get(username));
	};

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = new SessionLifecycleAuditLogger();

	private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();

	private final LocalAuthorityRefreshFilter filter = new LocalAuthorityRefreshFilter(this.localAuthorityLookup,
			this.sessionLifecycleAuditLogger, this.sessionRegistry);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void replacesRoleAuthoritiesWithFreshOnesFromTheDatabaseButKeepsOtherAuthorities() throws Exception {
		this.localUsers.put("alice", roles("USER_MANAGE"));
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
	void deauthenticatesAndInvalidatesTheSessionWhenTheUserIsDisabledOrDeleted(CapturedOutput output) throws Exception {
		SecurityContextHolder.getContext().setAuthentication(oauthToken("alice"));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);
		this.sessionLifecycleAuditLogger.logSessionCreatedIfNeeded(session);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(session.isInvalid()).isTrue();
		assertThat(output).contains("event.action=\"destroy_session\"")
			.contains("session.termination_reason=\"user_disabled_or_deleted\"");
	}

	@Test
	void logsAPrivilegeChangeOnceAndSavesTheRefreshedAuthenticationToTheSession(CapturedOutput output)
			throws Exception {
		this.localUsers.put("alice", roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("SCOPE_openid"),
					new SimpleGrantedAuthority("ROLE_STALE_ROLE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(output).containsOnlyOnce("event.action=\"update_session\"")
			.contains("roles.added=\"[USER_MANAGE]\"")
			.contains("roles.removed=\"[STALE_ROLE]\"");
		SecurityContext savedContext = (SecurityContext) session
			.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
		assertThat(savedContext.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("SCOPE_openid", "ROLE_USER_MANAGE");
	}

	@Test
	void doesNotLogWhenTheReloadedAuthoritiesAreUnchanged(CapturedOutput output) throws Exception {
		this.localUsers.put("alice", roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("ROLE_USER_MANAGE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(output).doesNotContain("update_session");
		assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
	}

	@Test
	void doesNotLogAPrivilegeChangeForASessionAlreadyExpiredForRevocation(CapturedOutput output) throws Exception {
		this.localUsers.put("alice", roles("USER_MANAGE"));
		SecurityContextHolder.getContext()
			.setAuthentication(oauthToken("alice", new SimpleGrantedAuthority("ROLE_STALE_ROLE")));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts");
		MockHttpSession session = (MockHttpSession) request.getSession(true);
		this.sessionRegistry.registerNewSession(session.getId(), "alice");
		this.sessionRegistry.getSessionInformation(session.getId()).expireNow();

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(output).doesNotContain("update_session");
	}

	@Test
	void doesNothingWhenNotAuthenticatedAsALocalOidcUser() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		this.filter.doFilter(new MockHttpServletRequest("GET", "/accounts"), new MockHttpServletResponse(),
				(request, response) -> {
				});

		assertThat(this.lookedUp).isEmpty();
	}

	private static Collection<GrantedAuthority> roles(String... roleNames) {
		return Arrays.stream(roleNames)
			.<GrantedAuthority>map((roleName) -> new SimpleGrantedAuthority("ROLE_" + roleName))
			.toList();
	}

	private OAuth2AuthenticationToken oauthToken(String preferredUsername, GrantedAuthority... authorities) {
		OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("sub", "subject", "preferred_username", preferredUsername));
		DefaultOidcUser principal = new DefaultOidcUser(List.of(authorities), idToken, "preferred_username");
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");
	}

}
