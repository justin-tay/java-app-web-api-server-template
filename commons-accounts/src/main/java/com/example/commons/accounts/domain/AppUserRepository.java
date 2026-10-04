package com.example.commons.accounts.domain;

import java.util.UUID;
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

public interface AppUserRepository extends JpaRepository<AppUser, UUID>, JpaSpecificationExecutor<AppUser> {

	@EntityGraph(attributePaths = { "groups", "groups.roles" })
	Optional<AppUser> findByUsernameAndStatus(String username, AccountStatus status);

	Optional<AppUser> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByGroups_Id(UUID groupId);

	long countByGroups_Id(UUID groupId);

	/**
	 * Records a sign-in without touching the audit columns, which track administrative
	 * changes.
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update AppUser u set u.lastLoginAt = :at where u.username = :username")
	void recordLogin(@Param("username") String username, @Param("at") Instant at);

	/**
	 * Returns the IDs of the active accounts last in use before the cutoff, which the
	 * inactivity job suspends. See {@link AppUser#lastActivityAt()}.
	 */
	@Query("select u.id from AppUser u where u.status = com.example.commons.accounts.domain.AccountStatus.ACTIVE and u.inactivityClockStartedAt < :cutoff and (u.lastLoginAt is null or u.lastLoginAt < :cutoff)")
	List<UUID> findActiveIdsInactiveSince(@Param("cutoff") Instant cutoff);

	/**
	 * Returns the IDs of the accounts, active or suspended, last in use before the
	 * cutoff, which the inactivity job removes.
	 */
	@Query("select u.id from AppUser u where u.inactivityClockStartedAt < :cutoff and (u.lastLoginAt is null or u.lastLoginAt < :cutoff)")
	List<UUID> findIdsInactiveSince(@Param("cutoff") Instant cutoff);

	@Query("select u.username from AppUser u")
	List<String> findAllUsernames();

}
