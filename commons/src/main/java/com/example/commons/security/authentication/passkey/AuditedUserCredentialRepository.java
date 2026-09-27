package com.example.commons.security.authentication.passkey;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Stores passkey credentials, limiting how many a user may hold, refusing a login whose
 * signature counter did not increase, and recording each registration and removal.
 * <p>
 * Spring Security saves a credential when it is registered and again after each
 * successful login, with the new signature counter and last-used time, so a save of a
 * credential that already exists is a login. Spring's verification does not compare the
 * counter with the one stored after the previous login, which is what detects a cloned
 * authenticator, so that comparison is made here. A counter that is zero both times is
 * allowed, because passkeys synced between devices report zero.
 */
class AuditedUserCredentialRepository implements UserCredentialRepository {

	private final UserCredentialRepository delegate;

	private final PublicKeyCredentialUserEntityRepository userEntities;

	private final PasskeyAuditLogger auditLogger;

	private final int maxPerUser;

	AuditedUserCredentialRepository(UserCredentialRepository delegate,
			PublicKeyCredentialUserEntityRepository userEntities, PasskeyAuditLogger auditLogger, int maxPerUser) {
		this.delegate = delegate;
		this.userEntities = userEntities;
		this.auditLogger = auditLogger;
		this.maxPerUser = maxPerUser;
	}

	@Override
	public void save(CredentialRecord credentialRecord) {
		CredentialRecord existing = this.delegate.findByCredentialId(credentialRecord.getCredentialId());
		if (existing == null) {
			if (this.delegate.findByUserId(credentialRecord.getUserEntityUserId()).size() >= this.maxPerUser) {
				throw new IllegalArgumentException("The user already holds the most passkeys allowed");
			}
			this.delegate.save(credentialRecord);
			this.auditLogger.registered(ownerOf(credentialRecord), credentialRecord.getLabel());
			return;
		}
		long previous = existing.getSignatureCount();
		long current = credentialRecord.getSignatureCount();
		if ((previous > 0 || current > 0) && current <= previous) {
			this.auditLogger.counterRegression(ownerOf(existing), existing.getLabel());
			throw new IllegalArgumentException("The passkey's signature counter did not increase");
		}
		this.delegate.save(credentialRecord);
	}

	@Override
	public void delete(Bytes credentialId) {
		CredentialRecord existing = this.delegate.findByCredentialId(credentialId);
		this.delegate.delete(credentialId);
		if (existing != null) {
			this.auditLogger.removed(ownerOf(existing), existing.getLabel());
		}
	}

	/**
	 * Changes a passkey's label. It is not a login, so its signature counter, which is
	 * unchanged, is not compared with the stored one.
	 */
	void rename(CredentialRecord existing, String label) {
		this.delegate.save(ImmutableCredentialRecord.fromCredentialRecord(existing).label(label).build());
	}

	@Override
	public @Nullable CredentialRecord findByCredentialId(Bytes credentialId) {
		return this.delegate.findByCredentialId(credentialId);
	}

	@Override
	public List<CredentialRecord> findByUserId(Bytes userId) {
		return this.delegate.findByUserId(userId);
	}

	private String ownerOf(CredentialRecord credentialRecord) {
		PublicKeyCredentialUserEntity owner = this.userEntities.findById(credentialRecord.getUserEntityUserId());
		return (owner != null) ? owner.getName() : "unknown";
	}

}
