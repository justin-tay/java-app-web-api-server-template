package com.example.commons.accounts.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One application setting, keyed by name, such as {@code inactivity.suspendAfterDays}.
 * The settings and their allowed values are defined by {@code SettingsService}.
 */
@Entity
@Table(name = "app_setting")
public class AppSetting {

	@Id
	private String name;

	@Column(name = "setting_value")
	private String value;

	private Instant updatedAt;

	private String updatedBy;

	protected AppSetting() {
	}

	public AppSetting(String name, String value) {
		this.name = name;
		this.value = value;
		this.updatedAt = Instant.now();
		this.updatedBy = Auditor.current();
	}

	public String getName() {
		return this.name;
	}

	public String getValue() {
		return this.value;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public String getUpdatedBy() {
		return this.updatedBy;
	}

	public void change(String value) {
		this.value = value;
		this.updatedAt = Instant.now();
		this.updatedBy = Auditor.current();
	}

}
