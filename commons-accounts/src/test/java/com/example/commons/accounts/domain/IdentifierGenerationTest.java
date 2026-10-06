package com.example.commons.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.commons.accounts.AccountsJpaTest;

/**
 * Tests that an entity gets a sequence number as its primary key and, apart from it, a
 * random version 4 UUID as its public identifier, which carries no creation time.
 */
@AccountsJpaTest
class IdentifierGenerationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistingAnEntityAssignsASequenceNumberAndAVersion4PublicId() {
		AppRole first = this.entityManager.persistAndFlush(new AppRole("First"));
		AppRole second = this.entityManager.persistAndFlush(new AppRole("Second"));

		assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
		assertThat(first.getPublicId().version()).isEqualTo(4);
		assertThat(second.getPublicId().version()).isEqualTo(4);
		assertThat(first.getPublicId()).isNotEqualTo(second.getPublicId());
		assertThat(this.entityManager.find(AppRole.class, first.getId()).getName()).isEqualTo("First");
	}

	@Test
	void thePublicIdIsKnownBeforeTheEntityIsPersisted() {
		AppRole role = new AppRole("Unsaved");

		assertThat(role.getPublicId()).isNotNull();
		assertThat(role.getId()).isNull();
	}

}
