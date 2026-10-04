package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppGroupRepository extends JpaRepository<AppGroup, Long>, JpaSpecificationExecutor<AppGroup> {

	boolean existsByName(String name);

	Optional<AppGroup> findByPublicId(UUID publicId);

	List<AppGroup> findAllByPublicIdIn(Collection<UUID> publicIds);

	boolean existsByRoles_PublicId(UUID roleId);

}
