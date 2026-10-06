package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountReviewItemRepository
		extends JpaRepository<AccountReviewItem, Long>, JpaSpecificationExecutor<AccountReviewItem> {

	Optional<AccountReviewItem> findByPublicId(UUID publicId);

	List<AccountReviewItem> findByTaskIdAndPublicIdIn(Long taskId, Collection<UUID> publicIds);

	/**
	 * Returns the undecided items of one account in tasks that are still open.
	 */
	@Query("select i from AccountReviewItem i where i.userPublicId = :userPublicId and i.outcome = com.example.commons.accounts.domain.AccountReviewOutcome.PENDING "
			+ "and i.taskId in (select t.id from Task t where t.status = com.example.commons.accounts.domain.TaskStatus.OPEN)")
	List<AccountReviewItem> findPendingInOpenTasks(@Param("userPublicId") UUID userPublicId);

	/**
	 * Returns every item of a task with its live account and the account's roles, so a
	 * row can be shown without further queries.
	 */
	@Query("select distinct i from AccountReviewItem i left join fetch i.user u left join fetch u.roles r left join fetch r.permissions where i.taskId = :taskId")
	List<AccountReviewItem> findAllWithAccount(@Param("taskId") Long taskId);

	/**
	 * Returns the number of undecided items whose account is active, the ones a task
	 * still waits for. An undecided item whose account is suspended is not counted.
	 */
	@Query("select count(i) from AccountReviewItem i join i.user u where i.taskId = :taskId and i.outcome = com.example.commons.accounts.domain.AccountReviewOutcome.PENDING and u.status = com.example.commons.accounts.domain.AccountStatus.ACTIVE")
	long countPendingWithActiveAccount(@Param("taskId") Long taskId);

	@Query("select i.outcome, count(i) from AccountReviewItem i where i.taskId = :taskId group by i.outcome")
	List<Object[]> countByOutcome(@Param("taskId") Long taskId);

}
