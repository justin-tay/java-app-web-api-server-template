package com.example.commons.accounts.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, String>, JpaSpecificationExecutor<AppUser> {

	@EntityGraph(attributePaths = { "groups", "groups.roles" })
	Optional<AppUser> findByUsernameAndEnabledTrue(String username);

	boolean existsByUsername(String username);

	boolean existsByGroups_Id(String groupId);

	long countByGroups_Id(String groupId);

	@Query("select u.username from AppUser u")
	List<String> findAllUsernames();

}
