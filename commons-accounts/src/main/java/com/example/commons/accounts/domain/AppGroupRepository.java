package com.example.commons.accounts.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppGroupRepository extends JpaRepository<AppGroup, String>, JpaSpecificationExecutor<AppGroup> {

	boolean existsByName(String name);

	boolean existsById(String id);

	boolean existsByRoles_Id(String roleId);

}
