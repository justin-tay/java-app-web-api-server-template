package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import com.example.commons.security.authentication.passkey.PasskeyUserDirectory.PasskeyUser;

class PasskeySecurityAutoConfigurationTest {

	private final PasskeySecurityAutoConfiguration configuration = new PasskeySecurityAutoConfiguration();

	private final MapPublicKeyCredentialUserEntityRepository userEntities = new MapPublicKeyCredentialUserEntityRepository();

	private final MapUserCredentialRepository userCredentials = new MapUserCredentialRepository();

	private final PasskeyAuditLogger auditLogger = new PasskeyAuditLogger();

	@Test
	void startupFailsWhenThePasskeysAreEnabledWithoutARelyingPartyId() {
		PasskeyProperties properties = new PasskeyProperties();
		properties.setAllowedOrigins(Set.of("https://app.example.com"));

		assertThatIllegalArgumentException().isThrownBy(() -> operations(properties))
			.withMessageContaining("commons.security.passkeys.relying-party.id");
	}

	@Test
	void startupFailsWhenThePasskeysAreEnabledWithoutAllowedOrigins() {
		PasskeyProperties properties = new PasskeyProperties();
		properties.getRelyingParty().setId("app.example.com");

		assertThatIllegalArgumentException().isThrownBy(() -> operations(properties))
			.withMessageContaining("commons.security.passkeys.allowed-origins");
	}

	@Test
	void thePolicyRequiresDiscoverableCredentialsAndUserVerificationWithNoAttestation() {
		PasskeyProperties properties = new PasskeyProperties();
		properties.getRelyingParty().setId("app.example.com");
		properties.setAllowedOrigins(Set.of("https://app.example.com"));
		String userId = UUID.randomUUID().toString();
		WebAuthnRelyingPartyOperations operations = operations(properties, new DirectoryBackedUserEntityRepository(
				this.userEntities, username -> java.util.Optional.of(new PasskeyUser(userId, username, "Alice"))));

		PublicKeyCredentialCreationOptions creation = operations
			.createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(
					new TestingAuthenticationToken("alice", null, "ROLE_USER")));
		PublicKeyCredentialRequestOptions request = operations
			.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(null));

		assertThat(creation.getRp().getId()).isEqualTo("app.example.com");
		assertThat(creation.getRp().getName()).isEqualTo("app.example.com");
		assertThat(creation.getUser().getId()).isEqualTo(PasskeyUserHandle.of(userId));
		assertThat(creation.getAuthenticatorSelection().getUserVerification())
			.isEqualTo(UserVerificationRequirement.REQUIRED);
		assertThat(creation.getAuthenticatorSelection().getResidentKey()).isEqualTo(ResidentKeyRequirement.REQUIRED);
		assertThat(creation.getAttestation().getValue()).isEqualTo("none");
		assertThat(request.getUserVerification()).isEqualTo(UserVerificationRequirement.REQUIRED);
	}

	private WebAuthnRelyingPartyOperations operations(PasskeyProperties properties) {
		return operations(properties, this.userEntities);
	}

	private WebAuthnRelyingPartyOperations operations(PasskeyProperties properties,
			org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository entities) {
		return this.configuration.passkeyRelyingPartyOperations(properties, entities,
				new AuditedUserCredentialRepository(this.userCredentials, entities, this.auditLogger, 10));
	}

}
