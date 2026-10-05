package com.example.commons.accounts.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * Reads and appends the entries of confirmed populations. It extends {@link Repository}
 * rather than {@code JpaRepository} so that it exposes no update or delete.
 */
public interface AccountReviewPopulationEntryRepository
		extends Repository<AccountReviewPopulationEntry, Long>, JpaSpecificationExecutor<AccountReviewPopulationEntry> {

	AccountReviewPopulationEntry save(AccountReviewPopulationEntry entry);

	List<AccountReviewPopulationEntry> saveAll(Iterable<AccountReviewPopulationEntry> entries);

	List<AccountReviewPopulationEntry> findByAttestationId(Long attestationId);

}
