package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, String>, JpaSpecificationExecutor<ReviewItem> {

	List<ReviewItem> findByTaskIdAndIdIn(String taskId, Collection<String> ids);

	long countByTaskIdAndReviewStatus(String taskId, ReviewStatus reviewStatus);

	/**
	 * Returns the undecided items of one account in tasks that are still open.
	 */
	@Query("select i from ReviewItem i where i.userId = :userId and i.reviewStatus = com.example.commons.accounts.domain.ReviewStatus.PENDING_VERIFICATION "
			+ "and i.taskId in (select t.id from Task t where t.status = com.example.commons.accounts.domain.TaskStatus.OPEN)")
	List<ReviewItem> findPendingInOpenTasks(@Param("userId") String userId);

	@Query("select i.reviewStatus, count(i) from ReviewItem i where i.taskId = :taskId group by i.reviewStatus")
	List<Object[]> countByStatus(@Param("taskId") String taskId);

}
