package com.example.commons.accounts.domain;

import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	Optional<Task> findByPublicId(UUID publicId);

	boolean existsByTypeAndStartDate(String type, LocalDate startDate);

	List<Task> findByTypeAndStatus(String type, TaskStatus status);

	/**
	 * Returns the latest task of a type that started before the given date.
	 */
	Optional<Task> findFirstByTypeAndStartDateBeforeOrderByStartDateDesc(String type, LocalDate date);

	long countByStatus(TaskStatus status);

	long countByStatusAndDueDateBefore(TaskStatus status, LocalDate date);

	Optional<Task> findFirstByStatusOrderByDueDateAsc(TaskStatus status);

}
