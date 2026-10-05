package com.example.commons.accounts.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Reads and appends population confirmations. It extends {@link Repository} rather than
 * {@code JpaRepository} so that it exposes no update or delete.
 */
public interface AccountReviewAttestationRepository extends Repository<AccountReviewAttestation, Long> {

	AccountReviewAttestation save(AccountReviewAttestation attestation);

	List<AccountReviewAttestation> findByTaskId(Long taskId);

	Optional<AccountReviewAttestation> findByTaskIdAndPopulation(Long taskId, ReviewPopulation population);

}
