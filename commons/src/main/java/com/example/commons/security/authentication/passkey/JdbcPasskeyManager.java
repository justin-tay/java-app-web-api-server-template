package com.example.commons.security.authentication.passkey;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

/**
 * The {@link PasskeyManager} over Spring Security's credential and user entity
 * repositories.
 */
class JdbcPasskeyManager implements PasskeyManager {

	private final AuditedUserCredentialRepository userCredentials;

	private final PublicKeyCredentialUserEntityRepository userEntities;

	JdbcPasskeyManager(AuditedUserCredentialRepository userCredentials,
			PublicKeyCredentialUserEntityRepository userEntities) {
		this.userCredentials = userCredentials;
		this.userEntities = userEntities;
	}

	@Override
	public List<Passkey> list(String userId) {
		return this.userCredentials.findByUserId(PasskeyUserHandle.of(userId))
			.stream()
			.map(JdbcPasskeyManager::view)
			.toList();
	}

	@Override
	public boolean rename(String userId, String credentialId, String label) {
		CredentialRecord existing = ownedBy(userId, credentialId);
		if (existing == null) {
			return false;
		}
		this.userCredentials.rename(existing, label);
		return true;
	}

	@Override
	public boolean remove(String userId, String credentialId) {
		CredentialRecord existing = ownedBy(userId, credentialId);
		if (existing == null) {
			return false;
		}
		this.userCredentials.delete(existing.getCredentialId());
		return true;
	}

	@Override
	public void removeAll(String userId) {
		Bytes handle = PasskeyUserHandle.of(userId);
		for (CredentialRecord credential : this.userCredentials.findByUserId(handle)) {
			this.userCredentials.delete(credential.getCredentialId());
		}
		this.userEntities.delete(handle);
	}

	private CredentialRecord ownedBy(String userId, String credentialId) {
		Bytes id;
		try {
			id = Bytes.fromBase64(credentialId);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		CredentialRecord credential = this.userCredentials.findByCredentialId(id);
		return (credential != null && credential.getUserEntityUserId().equals(PasskeyUserHandle.of(userId)))
				? credential : null;
	}

	private static Passkey view(CredentialRecord credential) {
		Set<String> transports = credential.getTransports()
			.stream()
			.map(AuthenticatorTransport::getValue)
			.collect(Collectors.toCollection(TreeSet::new));
		return new Passkey(credential.getCredentialId().toBase64UrlString(), credential.getLabel(),
				credential.getCreated(), credential.getLastUsed(), credential.isBackupEligible(), transports);
	}

}
