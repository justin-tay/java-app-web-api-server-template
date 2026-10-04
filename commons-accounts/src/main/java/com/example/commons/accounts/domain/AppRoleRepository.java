package com.example.commons.accounts.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppRoleRepository extends JpaRepository<AppRole, UUID>, JpaSpecificationExecutor<AppRole> {

	boolean existsByName(String name);

}
