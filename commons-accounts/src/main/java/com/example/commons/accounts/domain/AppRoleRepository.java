package com.example.commons.accounts.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppRoleRepository extends JpaRepository<AppRole, String>, JpaSpecificationExecutor<AppRole> {

	boolean existsByName(String name);

}
