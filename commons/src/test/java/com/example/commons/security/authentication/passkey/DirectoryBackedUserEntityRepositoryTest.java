package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;

import com.example.commons.security.authentication.passkey.PasskeyUserDirectory.PasskeyUser;

class DirectoryBackedUserEntityRepositoryTest {

	private static final String ALICE_ID = "6c1a2f6e-1b53-4d0e-9f0a-3b2d5a6f7c81";

	private final Map<String, PasskeyUser> enabledUsers = new HashMap<>(
			Map.of("alice", new PasskeyUser(ALICE_ID, "alice", "Alice")));

	private final PasskeyUserDirectory directory = username -> Optional.ofNullable(this.enabledUsers.get(username));

	private final MapPublicKeyCredentialUserEntityRepository delegate = new MapPublicKeyCredentialUserEntityRepository();

	private final DirectoryBackedUserEntityRepository repository = new DirectoryBackedUserEntityRepository(
			this.delegate, this.directory);

	@Test
	void theEntityOfAnEnabledUserHasTheUsersUuidAsItsHandleAndIsStored() {
		PublicKeyCredentialUserEntity entity = this.repository.findByUsername("alice");

		assertThat(entity.getId()).isEqualTo(PasskeyUserHandle.of(ALICE_ID));
		assertThat(entity.getId().getBytes()).hasSize(16);
		assertThat(entity.getName()).isEqualTo("alice");
		assertThat(entity.getDisplayName()).isEqualTo("Alice");
		assertThat(this.delegate.findById(entity.getId())).isNotNull();
	}

	@Test
	void theHandleIsTheUuidBytes() {
		byte[] handle = PasskeyUserHandle.of(ALICE_ID).getBytes();

		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(handle);
		assertThat(new UUID(buffer.getLong(), buffer.getLong())).hasToString(ALICE_ID);
	}

	@Test
	void aUserTheDirectoryDoesNotKnowHasNoEntity() {
		assertThat(this.repository.findByUsername("mallory")).isNull();
	}

	@Test
	void aDisabledUserHasNoEntityEvenIfOneWasStoredBefore() {
		this.repository.findByUsername("alice");
		this.enabledUsers.remove("alice");

		assertThat(this.repository.findByUsername("alice")).isNull();
	}

	@Test
	void anEntityWithARandomHandleIsRefused() {
		PublicKeyCredentialUserEntity random = ImmutablePublicKeyCredentialUserEntity.builder()
			.id(Bytes.random())
			.name("alice")
			.displayName("alice")
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.save(random));
	}

	@Test
	void anEntityForAnUnknownUserIsRefused() {
		PublicKeyCredentialUserEntity unknown = ImmutablePublicKeyCredentialUserEntity.builder()
			.id(PasskeyUserHandle.of(UUID.randomUUID().toString()))
			.name("mallory")
			.displayName("mallory")
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.save(unknown));
	}

}
