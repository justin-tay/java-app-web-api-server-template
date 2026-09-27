package com.example.commons.security.authentication.passkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.MapPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.MapUserCredentialRepository;

class AuditedUserCredentialRepositoryTest {

	private static final String USER_ID = UUID.randomUUID().toString();

	private static final Bytes HANDLE = PasskeyUserHandle.of(USER_ID);

	private final MapPublicKeyCredentialUserEntityRepository userEntities = new MapPublicKeyCredentialUserEntityRepository();

	private final MapUserCredentialRepository delegate = new MapUserCredentialRepository();

	private final PasskeyAuditLogger auditLogger = mock(PasskeyAuditLogger.class);

	private final AuditedUserCredentialRepository repository = new AuditedUserCredentialRepository(this.delegate,
			this.userEntities, this.auditLogger, 2);

	AuditedUserCredentialRepositoryTest() {
		this.userEntities.save(
				ImmutablePublicKeyCredentialUserEntity.builder().id(HANDLE).name("alice").displayName("Alice").build());
	}

	@Test
	void registeringAPasskeyStoresItAndRecordsTheRegistration() {
		this.repository.save(credential("a", 0));

		assertThat(this.repository.findByUserId(HANDLE)).hasSize(1);
		verify(this.auditLogger).registered("alice", "laptop");
	}

	@Test
	void aUserCannotRegisterMorePasskeysThanTheLimit() {
		this.repository.save(credential("a", 0));
		this.repository.save(credential("b", 0));

		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.save(credential("c", 0)));

		assertThat(this.repository.findByUserId(HANDLE)).hasSize(2);
	}

	@Test
	void aLoginWhoseSignatureCounterIncreasedIsStored() {
		this.repository.save(credential("a", 5));

		this.repository.save(credential("a", 6));

		assertThat(this.repository.findByCredentialId(id("a")).getSignatureCount()).isEqualTo(6);
	}

	@Test
	void aLoginWhoseSignatureCounterDidNotIncreaseIsRefusedAndRecorded() {
		this.repository.save(credential("a", 5));

		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.save(credential("a", 5)));
		assertThatIllegalArgumentException().isThrownBy(() -> this.repository.save(credential("a", 3)));

		assertThat(this.repository.findByCredentialId(id("a")).getSignatureCount()).isEqualTo(5);
		verify(this.auditLogger, org.mockito.Mockito.times(2)).counterRegression("alice", "laptop");
	}

	@Test
	void aSyncedPasskeyThatAlwaysReportsAZeroCounterCanLogIn() {
		this.repository.save(credential("a", 0));

		this.repository.save(credential("a", 0));

		assertThat(this.repository.findByCredentialId(id("a")).getSignatureCount()).isZero();
	}

	@Test
	void renamingAPasskeyKeepsItsCounterAndIsNotALogin() {
		this.repository.save(credential("a", 5));
		CredentialRecord existing = this.repository.findByCredentialId(id("a"));

		this.repository.rename(existing, "phone");

		assertThat(this.repository.findByCredentialId(id("a")).getLabel()).isEqualTo("phone");
		assertThat(this.repository.findByCredentialId(id("a")).getSignatureCount()).isEqualTo(5);
		verifyNoCounterRegression();
	}

	@Test
	void removingAPasskeyDeletesItAndRecordsTheRemoval() {
		this.repository.save(credential("a", 0));

		this.repository.delete(id("a"));

		assertThat(this.repository.findByCredentialId(id("a"))).isNull();
		verify(this.auditLogger).removed("alice", "laptop");
	}

	@Test
	void removingAPasskeyThatDoesNotExistRecordsNothing() {
		this.repository.delete(id("missing"));

		verifyNoInteractions(this.auditLogger);
	}

	private void verifyNoCounterRegression() {
		org.mockito.Mockito.verify(this.auditLogger, org.mockito.Mockito.never())
			.counterRegression(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
	}

	private static Bytes id(String name) {
		return new Bytes(name.getBytes());
	}

	private static CredentialRecord credential(String name, long signatureCount) {
		return ImmutableCredentialRecord.builder()
			.credentialType(PublicKeyCredentialType.PUBLIC_KEY)
			.credentialId(id(name))
			.userEntityUserId(HANDLE)
			.publicKey(new ImmutablePublicKeyCose(new byte[] { 1 }))
			.signatureCount(signatureCount)
			.backupEligible(false)
			.backupState(false)
			.label("laptop")
			.created(Instant.parse("2026-01-01T00:00:00Z"))
			.lastUsed(Instant.parse("2026-01-01T00:00:00Z"))
			.build();
	}

}
