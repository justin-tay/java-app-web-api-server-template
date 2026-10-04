package com.example.commons.accounts.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppGroupRepository extends JpaRepository<AppGroup, UUID>, JpaSpecificationExecutor<AppGroup> {

	boolean existsByName(String name);

	boolean existsById(UUID id);

	boolean existsByRoles_Id(UUID roleId);

}
