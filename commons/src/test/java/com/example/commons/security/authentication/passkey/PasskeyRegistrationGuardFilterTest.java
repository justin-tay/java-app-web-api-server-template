package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;

import com.example.commons.security.authentication.oidc.RecentAuthentication;

class PasskeyRegistrationGuardFilterTest {

	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private static final Bytes HANDLE = PasskeyUserHandle.of(UUID.randomUUID().toString());

	private final AuthenticationEntryPoint entryPoint = mock(AuthenticationEntryPoint.class);

	private final MapPublicKeyCredentialUserEntityRepository userEntities = new MapPublicKeyCredentialUserEntityRepository();

	private final MapUserCredentialRepository userCredentials = new MapUserCredentialRepository();

	private final PasskeyRegistrationGuardFilter filter = new PasskeyRegistrationGuardFilter(this.entryPoint,
			this.userEntities, this.userCredentials, Duration.ofMinutes(15), 1, Clock.fixed(NOW, ZoneOffset.UTC));

	PasskeyRegistrationGuardFilterTest() {
		this.userEntities.save(
				ImmutablePublicKeyCredentialUserEntity.builder().id(HANDLE).name("alice").displayName("Alice").build());
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void aRequestThatDoesNotRegisterAPasskeyIsNotChecked() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicBoolean proceeded = new AtomicBoolean();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/account/passkeys"), response,
				(request, servletResponse) -> proceeded.set(true));

		assertThat(proceeded).isTrue();
	}

	@Test
	void anUnauthenticatedRegistrationIsAnsweredByTheAuthenticationEntryPoint() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webauthn/register/options");
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("The request must not proceed");
		});

		verify(this.entryPoint).commence(any(), any(), any(AuthenticationException.class));
	}

	@Test
	void aRegistrationAfterAStaleLoginNeedsReauthentication() throws Exception {
		MockHttpServletRequest request = registration("/webauthn/register", NOW.minus(Duration.ofMinutes(16)));
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("The request must not proceed");
		});

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/problem+json");
		assertThat(response.getContentAsString()).contains("urn:problem:reauthentication-required")
			.contains("\"max_age\":900");
	}

	@Test
	void aRegistrationFromASessionWithNoRecordedLoginNeedsReauthentication() throws Exception {
		MockHttpServletRequest request = registration("/webauthn/register/options", null);
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("The request must not proceed");
		});

		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void aUserWhoHoldsTheMostPasskeysAllowedCannotRegisterAnother() throws Exception {
		this.userCredentials.save(credential("a"));
		MockHttpServletRequest request = registration("/webauthn/register/options", NOW.minus(Duration.ofMinutes(1)));
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(request, response, (req, res) -> {
			throw new AssertionError("The request must not proceed");
		});

		assertThat(response.getStatus()).isEqualTo(409);
		assertThat(response.getContentAsString()).contains("urn:problem:resource-conflict");
	}

	@Test
	void aRecentLoginWithRoomForAnotherPasskeyMayRegister() throws Exception {
		MockHttpServletRequest request = registration("/webauthn/register/options", NOW.minus(Duration.ofMinutes(1)));
		AtomicBoolean proceeded = new AtomicBoolean();

		this.filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> proceeded.set(true));

		assertThat(proceeded).isTrue();
	}

	private MockHttpServletRequest registration(String path, Instant loggedInAt) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		if (loggedInAt != null) {
			request.getSession(true).setAttribute(RecentAuthentication.PASSKEY_AUTHENTICATED_AT_ATTRIBUTE, loggedInAt);
		}
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken("alice", "unused", "ROLE_USER"));
		return request;
	}

	private static CredentialRecord credential(String name) {
		return ImmutableCredentialRecord.builder()
			.credentialType(PublicKeyCredentialType.PUBLIC_KEY)
			.credentialId(new Bytes(name.getBytes()))
			.userEntityUserId(HANDLE)
			.publicKey(new ImmutablePublicKeyCose(new byte[] { 1 }))
			.signatureCount(0)
			.backupEligible(false)
			.backupState(false)
			.label("laptop")
			.created(NOW)
			.lastUsed(NOW)
			.build();
	}

}
