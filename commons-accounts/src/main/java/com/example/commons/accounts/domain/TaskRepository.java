package com.example.commons.accounts.domain;

import java.util.UUID;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

	boolean existsByTypeAndStartDate(String type, LocalDate startDate);

	boolean existsByTypeAndStatus(String type, TaskStatus status);

	long countByStatus(TaskStatus status);

	long countByStatusAndDueDateBefore(TaskStatus status, LocalDate date);

	Optional<Task> findFirstByStatusOrderByDueDateAsc(TaskStatus status);

}
