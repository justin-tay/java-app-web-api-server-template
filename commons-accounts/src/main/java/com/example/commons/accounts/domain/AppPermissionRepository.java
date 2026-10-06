package com.example.commons.accounts.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppPermissionRepository
		extends JpaRepository<AppPermission, Long>, JpaSpecificationExecutor<AppPermission> {

	Optional<AppPermission> findByPublicId(UUID publicId);

	List<AppPermission> findAllByPublicIdIn(Collection<UUID> publicIds);

}
