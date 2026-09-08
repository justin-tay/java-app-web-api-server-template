package com.example.app.web.server.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppRoleRepository extends JpaRepository<AppRole, String>, JpaSpecificationExecutor<AppRole> {

	boolean existsByName(String name);

}
