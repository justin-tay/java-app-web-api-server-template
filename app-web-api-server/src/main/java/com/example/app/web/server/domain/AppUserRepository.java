package com.example.app.web.server.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository extends JpaRepository<AppUser, String>, JpaSpecificationExecutor<AppUser> {

	@EntityGraph(attributePaths = { "groups", "groups.roles" })
	Optional<AppUser> findByUsernameAndEnabledTrue(String username);

	boolean existsByUsername(String username);

	boolean existsByGroups_Id(String groupId);

}
