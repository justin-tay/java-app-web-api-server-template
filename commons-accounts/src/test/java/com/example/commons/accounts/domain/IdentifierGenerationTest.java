package com.example.commons.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.example.commons.accounts.AccountsJpaTest;

/**
 * Tests that persisting an entity gives it a version 7 UUID, which is time-ordered so
 * that new rows are added at the end of the primary key index.
 */
@AccountsJpaTest
class IdentifierGenerationTest {

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistingAnEntityAssignsAVersion7Uuid() {
		AppGroup first = this.entityManager.persistAndFlush(new AppGroup("First"));
		AppGroup second = this.entityManager.persistAndFlush(new AppGroup("Second"));

		assertThat(first.getId().version()).isEqualTo(7);
		assertThat(second.getId().version()).isEqualTo(7);
		assertThat(first.getId()).isNotEqualTo(second.getId());
		assertThat(this.entityManager.find(AppGroup.class, first.getId()).getName()).isEqualTo("First");
	}

}
