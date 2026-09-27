package com.example.commons.security.authentication.passkey;

import org.jspecify.annotations.Nullable;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

import com.example.commons.security.authentication.passkey.PasskeyUserDirectory.PasskeyUser;

/**
 * Stores passkey user entities whose WebAuthn user handle is the local user's UUID,
 * instead of the random handle Spring Security would generate. Spring asks for the entity
 * of a username when a user starts registering a passkey and creates a random one if
 * there is none; this repository answers from the {@link PasskeyUserDirectory} first, so
 * the entity that gets stored is the directory's, and refuses to store any other. A user
 * the directory does not know, such as a disabled one, has no entity for a new
 * registration.
 */
class DirectoryBackedUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

	private final PublicKeyCredentialUserEntityRepository delegate;

	private final PasskeyUserDirectory directory;

	DirectoryBackedUserEntityRepository(PublicKeyCredentialUserEntityRepository delegate,
			PasskeyUserDirectory directory) {
		this.delegate = delegate;
		this.directory = directory;
	}

	@Override
	public @Nullable PublicKeyCredentialUserEntity findById(Bytes id) {
		return this.delegate.findById(id);
	}

	@Override
	public @Nullable PublicKeyCredentialUserEntity findByUsername(String username) {
		return this.directory.findByUsername(username).map(user -> {
			PublicKeyCredentialUserEntity entity = entityOf(user);
			PublicKeyCredentialUserEntity stored = this.delegate.findByUsername(username);
			if (stored == null || !stored.getId().equals(entity.getId())) {
				this.delegate.save(entity);
			}
			return entity;
		}).orElse(null);
	}

	@Override
	public void save(PublicKeyCredentialUserEntity userEntity) {
		PasskeyUser user = this.directory.findByUsername(userEntity.getName()).orElse(null);
		if (user == null || !PasskeyUserHandle.of(user.id()).equals(userEntity.getId())) {
			throw new IllegalArgumentException("A passkey user entity must be the local user's");
		}
		this.delegate.save(userEntity);
	}

	@Override
	public void delete(Bytes id) {
		this.delegate.delete(id);
	}

	private static PublicKeyCredentialUserEntity entityOf(PasskeyUser user) {
		return ImmutablePublicKeyCredentialUserEntity.builder()
			.id(PasskeyUserHandle.of(user.id()))
			.name(user.username())
			.displayName(user.displayName())
			.build();
	}

}
