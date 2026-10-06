package com.example.commons.accounts.settings;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.Permissions;

/**
 * The application settings. Reading them needs {@value Permissions#SETTINGS_READ} and
 * changing them {@value Permissions#SETTINGS_UPDATE}, which is privileged.
 */
@RestController
@RequestMapping("/admin/settings")
public class SettingsController {

	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('settings:read')")
	public Settings get() {
		return this.service.get();
	}

	@PutMapping
	@PreAuthorize("hasAuthority('settings:update')")
	public Settings update(@Valid @RequestBody Settings settings) {
		return this.service.update(settings);
	}

}
