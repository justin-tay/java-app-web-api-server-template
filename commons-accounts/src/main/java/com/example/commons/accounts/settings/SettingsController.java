package com.example.commons.accounts.settings;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The application settings, for settings administrators.
 */
@RestController
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('SETTINGS_MANAGE')")
public class SettingsController {

	private final SettingsService service;

	public SettingsController(SettingsService service) {
		this.service = service;
	}

	@GetMapping
	public Settings get() {
		return this.service.get();
	}

	@PutMapping
	public Settings update(@Valid @RequestBody Settings settings) {
		return this.service.update(settings);
	}

}
