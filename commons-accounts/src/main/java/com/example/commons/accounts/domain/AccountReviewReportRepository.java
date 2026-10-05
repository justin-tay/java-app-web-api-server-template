package com.example.commons.accounts.domain;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Reads and appends stored review reports. It extends {@link Repository} rather than
 * {@code JpaRepository} so that it exposes no update or delete.
 */
public interface AccountReviewReportRepository extends Repository<AccountReviewReport, Long> {

	AccountReviewReport save(AccountReviewReport report);

	Optional<AccountReviewReport> findByTaskId(Long taskId);

	boolean existsByTaskId(Long taskId);

}
