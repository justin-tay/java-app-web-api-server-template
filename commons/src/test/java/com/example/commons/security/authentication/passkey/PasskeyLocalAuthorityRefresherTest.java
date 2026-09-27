package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import com.example.commons.security.authorization.LocalAuthorityLookup;
import com.example.commons.security.authorization.LocalAuthorityRefreshFilter;
import com.example.commons.security.session.SessionLifecycleAuditLogger;

/**
 * A passkey session is revoked, and follows role changes, exactly as an OpenID Connect
 * session does, and the user details a passkey login is built from come from the same
 * local model.
 */
class PasskeyLocalAuthorityRefresherTest {

	private final Map<String, Collection<GrantedAuthority>> localUsers = new HashMap<>();

	private final LocalAuthorityLookup lookup = username -> Optional.ofNullable(this.localUsers.get(username));

	private final LocalAuthorityRefreshFilter filter = new LocalAuthorityRefreshFilter(this.lookup,
			new SessionLifecycleAuditLogger(), new SessionRegistryImpl(),
			List.of(new PasskeyLocalAuthorityRefresher()));

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void aPasskeySessionGetsTheCurrentLocalRolesAndKeepsItsFactorAuthority() throws Exception {
		this.localUsers.put("alice", List.of(new SimpleGrantedAuthority("ROLE_USER_MANAGE")));
		SecurityContextHolder.getContext()
			.setAuthentication(passkeyLogin("alice", new SimpleGrantedAuthority("ROLE_STALE"),
					FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY)));

		this.filter.doFilter(new MockHttpServletRequest("GET", "/account"), new MockHttpServletResponse(),
				(request, response) -> {
				});

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isInstanceOf(WebAuthnAuthentication.class);
		assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
			.extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_USER_MANAGE", FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
	}

	@Test
	void aPasskeySessionOfADisabledOrDeletedUserIsDeauthenticated() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(passkeyLogin("alice"));
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.getSession(true);

		this.filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
		});

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void aUserWhoIsNotEnabledLocallyCannotBeLoadedForAPasskeyLogin() {
		LocalAuthorityUserDetailsService service = new LocalAuthorityUserDetailsService(this.lookup);
		this.localUsers.put("alice", List.of(new SimpleGrantedAuthority("ROLE_APPLICATION_USER")));

		assertThat(service.loadUserByUsername("alice").getAuthorities()).extracting(GrantedAuthority::getAuthority)
			.containsExactly("ROLE_APPLICATION_USER");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loadUserByUsername("mallory"))
			.isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
	}

	private static WebAuthnAuthentication passkeyLogin(String username, GrantedAuthority... authorities) {
		return new WebAuthnAuthentication(ImmutablePublicKeyCredentialUserEntity.builder()
			.id(PasskeyUserHandle.of(UUID.randomUUID().toString()))
			.name(username)
			.displayName(username)
			.build(), List.of(authorities));
	}

}
