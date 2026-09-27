package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import com.example.commons.security.authentication.oidc.RecentAuthentication;
import com.example.commons.security.session.SessionLifecycleAuditLogger;

class PasskeySessionFiltersTest {

	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final PasskeySessionFilters.AbsoluteTimeoutFilter filter = new PasskeySessionFilters.AbsoluteTimeoutFilter(
			Duration.ofHours(8), this.clock, new SessionLifecycleAuditLogger());

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void theLoginRecorderRecordsWhenThePasskeyLoginHappened() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login/webauthn");

		new PasskeySessionFilters.LoginRecorder(this.clock).onAuthenticationSuccess(request,
				new MockHttpServletResponse(), passkeyLogin());

		assertThat(request.getSession(false).getAttribute(RecentAuthentication.PASSKEY_AUTHENTICATED_AT_ATTRIBUTE))
			.isEqualTo(NOW);
	}

	@Test
	void aPasskeySessionWithinItsLifetimeContinues() throws Exception {
		MockHttpServletRequest request = sessionLoggedInAt(NOW.minus(Duration.ofHours(7)));
		SecurityContextHolder.getContext().setAuthentication(passkeyLogin());
		AtomicBoolean proceeded = new AtomicBoolean();

		this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> proceeded.set(true));

		assertThat(proceeded).isTrue();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(request.getSession(false)).isNotNull();
	}

	@Test
	void aPasskeySessionPastItsLifetimeIsEnded() throws Exception {
		MockHttpServletRequest request = sessionLoggedInAt(NOW.minus(Duration.ofHours(8)));
		SecurityContextHolder.getContext().setAuthentication(passkeyLogin());

		this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
		});

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void aPasskeySessionWithNoRecordedLoginIsEnded() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.getSession(true);
		SecurityContextHolder.getContext().setAuthentication(passkeyLogin());

		this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
		});

		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void anotherKindOfLoginIsLeftToTheCommonAbsoluteTimeout() throws Exception {
		MockHttpServletRequest request = sessionLoggedInAt(NOW.minus(Duration.ofDays(1)));
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("alice", null, "ROLE_USER"));

		this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
		});

		assertThat(request.getSession(false)).isNotNull();
	}

	private static MockHttpServletRequest sessionLoggedInAt(Instant loggedInAt) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account");
		request.getSession(true).setAttribute(RecentAuthentication.PASSKEY_AUTHENTICATED_AT_ATTRIBUTE, loggedInAt);
		return request;
	}

	private static WebAuthnAuthentication passkeyLogin() {
		return new WebAuthnAuthentication(ImmutablePublicKeyCredentialUserEntity.builder()
			.id(PasskeyUserHandle.of(UUID.randomUUID().toString()))
			.name("alice")
			.displayName("Alice")
			.build(), List.of());
	}

}
