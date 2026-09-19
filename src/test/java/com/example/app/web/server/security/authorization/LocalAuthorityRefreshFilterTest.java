package com.example.app.web.server.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import com.example.app.web.server.domain.AppGroup;
import com.example.app.web.server.domain.AppRole;
import com.example.app.web.server.domain.AppUser;
import com.example.app.web.server.domain.AppUserRepository;
import com.example.app.web.server.security.session.SessionLifecycleAuditLogger;

class LocalAuthorityRefreshFilterTest {

	private final AppUserRepository users = mock(AppUserRepository.class);

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = mock(SessionLifecycleAuditLogger.class);

	private final LocalAuthorityRefreshFilter filter = new LocalAuthorityRefreshFilter(this.users,
			this.sessionLifecycleAuditLogger);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void replacesRoleAuthoritiesWithFreshOnesFromTheDatabaseButKeepsOtherAuthorities() throws Exception {
		AppUser user = new AppUser("alice", "Alice", "alice@example.com", true);
		AppGroup group = new AppGroup("managers");
		group.getRoles().add(new AppRole("USER_MANAGE"));
		user.getGroups().add(group);
		when(this.users.findByUsernameAndEnabledTrue("alice")).thenReturn(Optional.of(user));
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
		when(this.users.findByUsernameAndEnabledTrue("alice")).thenReturn(Optional.empty());
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
	void doesNothingWhenNotAuthenticatedAsALocalOidcUser() throws Exception {
		SecurityContextHolder.getContext()
			.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
					List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		this.filter.doFilter(new MockHttpServletRequest("GET", "/accounts"), new MockHttpServletResponse(),
				(request, response) -> {
				});

		verifyNoInteractions(this.users);
	}

	private OAuth2AuthenticationToken oauthToken(String preferredUsername, GrantedAuthority... authorities) {
		OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
				Map.of("sub", "subject", "preferred_username", preferredUsername));
		DefaultOidcUser principal = new DefaultOidcUser(List.of(authorities), idToken);
		return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "keycloak");
	}

}
