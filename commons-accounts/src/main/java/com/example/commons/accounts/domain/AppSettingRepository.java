package com.example.commons.accounts.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

	Optional<AppSetting> findByName(String name);

}
