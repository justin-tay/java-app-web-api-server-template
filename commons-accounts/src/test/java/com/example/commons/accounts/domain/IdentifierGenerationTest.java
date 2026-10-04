package com.example.commons.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.commons.accounts.AccountsJpaTest;

/**
 * Tests that persisting an entity gives it a random version 4 UUID, which carries no
 * creation time.
 */
@AccountsJpaTest
class IdentifierGenerationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistingAnEntityAssignsAVersion4Uuid() {
		AppGroup first = this.entityManager.persistAndFlush(new AppGroup("First"));
		AppGroup second = this.entityManager.persistAndFlush(new AppGroup("Second"));

		assertThat(first.getId().version()).isEqualTo(4);
		assertThat(second.getId().version()).isEqualTo(4);
		assertThat(first.getId()).isNotEqualTo(second.getId());
		assertThat(this.entityManager.find(AppGroup.class, first.getId()).getName()).isEqualTo("First");
	}

}
