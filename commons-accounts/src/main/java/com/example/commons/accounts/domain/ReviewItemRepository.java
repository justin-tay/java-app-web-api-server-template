package com.example.commons.accounts.domain;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long>, JpaSpecificationExecutor<ReviewItem> {

	Optional<ReviewItem> findByPublicId(UUID publicId);

	List<ReviewItem> findByTaskIdAndPublicIdIn(Long taskId, Collection<UUID> publicIds);

	long countByTaskIdAndReviewStatus(Long taskId, ReviewStatus reviewStatus);

	/**
	 * Returns the undecided items of one account in tasks that are still open.
	 */
	@Query("select i from ReviewItem i where i.userPublicId = :userPublicId and i.reviewStatus = com.example.commons.accounts.domain.ReviewStatus.PENDING_VERIFICATION "
			+ "and i.taskId in (select t.id from Task t where t.status = com.example.commons.accounts.domain.TaskStatus.OPEN)")
	List<ReviewItem> findPendingInOpenTasks(@Param("userPublicId") UUID userPublicId);

	@Query("select i.reviewStatus, count(i) from ReviewItem i where i.taskId = :taskId group by i.reviewStatus")
	List<Object[]> countByStatus(@Param("taskId") Long taskId);

}
