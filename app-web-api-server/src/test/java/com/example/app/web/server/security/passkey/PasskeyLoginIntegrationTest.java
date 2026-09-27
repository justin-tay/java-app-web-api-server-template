package com.example.app.web.server.security.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Base64;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests a passkey login through the application's real security filter chain, with the
 * cryptographic check of the assertion, which needs a real authenticator, stubbed out:
 * the login authenticates as the local user, gets that user's local roles and no others,
 * records when it happened, is refused for a user who is not enabled locally, and gets a
 * new session ID (see docs/adr/0024).
 */
@SpringBootTest(properties = { "commons.security.passkeys.enabled=true", "commons.security.passkeys.rp-id=localhost",
		"commons.security.passkeys.allowed-origins=http://localhost:8081" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasskeyLoginIntegrationTest {

	private static final String SESSION_COOKIE = "SESSION=";

	private static final String ASSERTION = """
			{"id":"AQID","rawId":"AQID","type":"public-key","response":{"authenticatorData":"AQID",
			"clientDataJSON":"AQID","signature":"AQID","userHandle":"AQID"},"clientExtensionResults":{}}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoSpyBean
	private WebAuthnRelyingPartyOperations relyingPartyOperations;

	@Test
	void aPasskeyLoginAuthenticatesAsTheLocalUserWithTheirLocalRolesAndNoOthers() throws Exception {
		Cookie administrator = logIn("admin");
		Cookie testUser = logIn("test-user");

		// The administrator's local role opens the administration API, and the test
		// user, whose passkey carries no roles of its own, is refused it.
		this.mockMvc.perform(get("/admin/users").cookie(administrator)).andExpect(status().isOk());
		this.mockMvc.perform(get("/admin/users").cookie(testUser)).andExpect(status().isForbidden());
		this.mockMvc.perform(get("/account/passkeys").cookie(testUser)).andExpect(status().isOk());
	}

	@Test
	void aPasskeyLoginRecordsWhenItHappenedForTheRecentLoginChecks() throws Exception {
		Cookie session = logIn("admin");

		// A passkey login may add another passkey while it is recent.
		this.mockMvc.perform(post("/webauthn/register/options").cookie(session).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.name").value("admin"));
	}

	@Test
	void aUserWhoIsNotEnabledLocallyCannotLogInWithAPasskey() throws Exception {
		Cookie session = startLogin();
		doReturn(entityOf("unknown-user")).when(this.relyingPartyOperations).authenticate(any());

		this.mockMvc
			.perform(post("/login/webauthn").cookie(session)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ASSERTION))
			.andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/account/passkeys").cookie(session).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aPasskeyLoginGetsANewSessionId() throws Exception {
		Cookie before = startLogin();
		doReturn(entityOf("admin")).when(this.relyingPartyOperations).authenticate(any());

		MockHttpServletResponse response = this.mockMvc
			.perform(post("/login/webauthn").cookie(before)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ASSERTION))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andReturn()
			.getResponse();

		assertThat(sessionCookie(response).getValue()).isNotEqualTo(before.getValue());
	}

	private Cookie logIn(String username) throws Exception {
		Cookie started = startLogin();
		doReturn(entityOf(username)).when(this.relyingPartyOperations).authenticate(any());
		return sessionCookie(this.mockMvc
			.perform(post("/login/webauthn").cookie(started)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(ASSERTION))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse());
	}

	/**
	 * Asks for login options, which the application keeps in the new session, and returns
	 * the session cookie.
	 */
	private Cookie startLogin() throws Exception {
		return sessionCookie(this.mockMvc.perform(post("/webauthn/authenticate/options").with(csrf()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse());
	}

	private static Cookie sessionCookie(MockHttpServletResponse response) {
		String header = response.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith(SESSION_COOKIE))
			.findFirst()
			.orElseThrow(() -> new AssertionError("The response set no session cookie"));
		return new Cookie("SESSION", header.substring(SESSION_COOKIE.length(), header.indexOf(';')));
	}

	private static PublicKeyCredentialUserEntity entityOf(String username) {
		return ImmutablePublicKeyCredentialUserEntity.builder()
			.id(new Bytes(Base64.getUrlDecoder().decode("AAAAAAAAAAAAAAAAAAAAIg")))
			.name(username)
			.displayName(username)
			.build();
	}

}
