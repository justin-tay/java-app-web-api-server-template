package com.example.app.web.server.security.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.example.app.web.server.test.OidcLogins.oidcLoginAs;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests passkey login and registration as this application serves them with passkeys
 * enabled (see docs/adr/0024): the login options are public and require user
 * verification, registration needs a recent login and is bound to the local user's UUID
 * as its WebAuthn user handle, and a user's passkeys can be listed, renamed, and revoked
 * by the user and by an administrator, and go when the user is deleted.
 */
@SpringBootTest(
		properties = { "commons.security.passkeys.enabled=true", "commons.security.passkeys.relying-party.id=localhost",
				"commons.security.passkeys.relying-party.name=Test Application",
				"commons.security.passkeys.allowed-origins=http://localhost:8081" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasskeyIntegrationTest {

	private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000021";

	private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000022";

	private static final String MULTI_GROUP_USER_ID = "00000000-0000-0000-0000-000000000023";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PublicKeyCredentialUserEntityRepository userEntities;

	@Autowired
	private UserCredentialRepository userCredentials;

	@Test
	void loginOptionsArePublicAndRequireUserVerification() throws Exception {
		this.mockMvc.perform(post("/webauthn/authenticate/options").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rpId").value("localhost"))
			.andExpect(jsonPath("$.userVerification").value("required"))
			.andExpect(jsonPath("$.challenge").isNotEmpty());
	}

	@Test
	void aLoginWithoutTheOptionsItAnswersIsRefused() throws Exception {
		this.mockMvc.perform(post("/login/webauthn").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void registrationRequiresAuthentication() throws Exception {
		this.mockMvc.perform(post("/webauthn/register/options").with(csrf()).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void registrationOptionsUseTheLocalUsersUuidAsTheUserHandleAndRequireUserVerification() throws Exception {
		this.mockMvc.perform(post("/webauthn/register/options").with(loginAt("test-user", Instant.now())).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rp.id").value("localhost"))
			.andExpect(jsonPath("$.rp.name").value("Test Application"))
			.andExpect(jsonPath("$.user.name").value("test-user"))
			.andExpect(jsonPath("$.user.id").value(handleOf(TEST_USER_ID)))
			.andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"))
			.andExpect(jsonPath("$.authenticatorSelection.residentKey").value("required"))
			.andExpect(jsonPath("$.attestation").value("none"));
	}

	@Test
	void registrationAfterAStaleLoginNeedsReauthentication() throws Exception {
		this.mockMvc
			.perform(post("/webauthn/register/options")
				.with(loginAt("test-user", Instant.now().minus(Duration.ofMinutes(16))))
				.with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"))
			.andExpect(jsonPath("$.max_age").value(900));
	}

	@Test
	void aUserListsRenamesAndRemovesTheirOwnPasskeys() throws Exception {
		saveCredential("test-user", "first", "laptop");
		saveCredential("test-user", "second", "phone");

		this.mockMvc.perform(get("/account/passkeys").with(loginAt("test-user", Instant.now())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));

		this.mockMvc
			.perform(patch("/account/passkeys/" + credentialId("first")).with(loginAt("test-user", Instant.now()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"work laptop\"}"))
			.andExpect(status().isNoContent());
		assertThat(this.userCredentials.findByCredentialId(new Bytes("first".getBytes())).getLabel())
			.isEqualTo("work laptop");

		this.mockMvc
			.perform(delete("/webauthn/register/" + credentialId("first")).with(loginAt("test-user", Instant.now()))
				.with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(this.userCredentials.findByCredentialId(new Bytes("first".getBytes()))).isNull();
	}

	@Test
	void aUserCannotRenameOrRemoveAnotherUsersPasskey() throws Exception {
		saveCredential("multi-group-user", "theirs", "laptop");

		this.mockMvc
			.perform(patch("/account/passkeys/" + credentialId("theirs")).with(loginAt("test-user", Instant.now()))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"mine now\"}"))
			.andExpect(status().isNotFound());
		this.mockMvc
			.perform(delete("/webauthn/register/" + credentialId("theirs")).with(loginAt("test-user", Instant.now()))
				.with(csrf()))
			.andExpect(status().isForbidden());

		assertThat(this.userCredentials.findByCredentialId(new Bytes("theirs".getBytes()))).isNotNull();
	}

	@Test
	void anAdministratorListsAndRevokesAUsersPasskeys() throws Exception {
		saveCredential("test-user", "lost", "lost phone");

		this.mockMvc.perform(get("/admin/users/" + TEST_USER_ID + "/passkeys").with(loginAt("admin", Instant.now())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].label").value("lost phone"))
			.andExpect(jsonPath("$[0].id").value(credentialId("lost")));

		this.mockMvc
			.perform(delete("/admin/users/" + TEST_USER_ID + "/passkeys/" + credentialId("lost"))
				.with(loginAt("admin", Instant.now()))
				.with(csrf()))
			.andExpect(status().isNoContent());
		assertThat(this.userCredentials.findByCredentialId(new Bytes("lost".getBytes()))).isNull();

		this.mockMvc
			.perform(delete("/admin/users/" + TEST_USER_ID + "/passkeys/" + credentialId("lost"))
				.with(loginAt("admin", Instant.now()))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	void anAdministratorRevokingAPasskeyNeedsARecentLogin() throws Exception {
		saveCredential("test-user", "kept", "phone");

		this.mockMvc
			.perform(delete("/admin/users/" + TEST_USER_ID + "/passkeys/" + credentialId("kept"))
				.with(loginAt("admin", Instant.now().minus(Duration.ofMinutes(16))))
				.with(csrf()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.type").value("urn:problem:reauthentication-required"));

		assertThat(this.userCredentials.findByCredentialId(new Bytes("kept".getBytes()))).isNotNull();
	}

	@Test
	void deletingAUserDeletesTheirPasskeys() throws Exception {
		PublicKeyCredentialUserEntity entity = saveCredential("multi-group-user", "gone", "laptop");

		this.mockMvc
			.perform(delete("/admin/users/" + MULTI_GROUP_USER_ID).with(loginAt("admin", Instant.now())).with(csrf()))
			.andExpect(status().isNoContent());

		assertThat(this.userCredentials.findByCredentialId(new Bytes("gone".getBytes()))).isNull();
		assertThat(this.userEntities.findById(entity.getId())).isNull();
	}

	private PublicKeyCredentialUserEntity saveCredential(String username, String credentialId, String label) {
		PublicKeyCredentialUserEntity entity = this.userEntities.findByUsername(username);
		this.userCredentials.save(ImmutableCredentialRecord.builder()
			.credentialType(PublicKeyCredentialType.PUBLIC_KEY)
			.credentialId(new Bytes(credentialId.getBytes()))
			.userEntityUserId(entity.getId())
			.publicKey(new ImmutablePublicKeyCose(new byte[] { 1 }))
			.signatureCount(0)
			.backupEligible(false)
			.backupState(false)
			.label(label)
			.created(Instant.now())
			.lastUsed(Instant.now())
			.build());
		return entity;
	}

	private static String credentialId(String credentialId) {
		return new Bytes(credentialId.getBytes()).toBase64UrlString();
	}

	private static String handleOf(String userId) {
		java.util.UUID uuid = java.util.UUID.fromString(userId);
		byte[] bytes = java.nio.ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Logs in through OpenID Connect as a seeded user at the given time.
	 * {@code LocalAuthorityRefreshFilter} replaces the login's authorities with the
	 * user's local roles.
	 */
	private static RequestPostProcessor loginAt(String username, Instant authTime) {
		return oidcLoginAs(username, authTime);
	}

}
