package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppRoleRepository extends JpaRepository<AppRole, Long>, JpaSpecificationExecutor<AppRole> {

	Optional<AppRole> findByPublicId(UUID publicId);

	List<AppRole> findAllByPublicIdIn(Collection<UUID> publicIds);

	boolean existsByName(String name);

}
