package com.example.commons.accounts.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AppUserRepository extends JpaRepository<AppUser, String>, JpaSpecificationExecutor<AppUser> {

	@EntityGraph(attributePaths = { "groups", "groups.roles" })
	Optional<AppUser> findByUsernameAndEnabledTrue(String username);

	boolean existsByUsername(String username);

	boolean existsByGroups_Id(String groupId);

	long countByGroups_Id(String groupId);

	/**
	 * Records a sign-in without touching the audit columns, which track administrative
	 * changes.
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update AppUser u set u.lastLoginAt = :at where u.username = :username")
	void recordLogin(@Param("username") String username, @Param("at") Instant at);

	/**
	 * Returns the enabled users with no sign-in, creation, or administrative change since
	 * the cutoff.
	 */
	@EntityGraph(attributePaths = { "groups", "groups.roles" })
	@Query("select u from AppUser u where u.enabled = true and u.createdAt < :cutoff and u.updatedAt < :cutoff and (u.lastLoginAt is null or u.lastLoginAt < :cutoff)")
	List<AppUser> findEnabledInactiveSince(@Param("cutoff") Instant cutoff);

	@Query("select u.username from AppUser u")
	List<String> findAllUsernames();

}
