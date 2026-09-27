package com.example.commons.security.authentication.passkey;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.security.authentication.passkey.PasskeyManager.Passkey;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * The signed-in user's own passkeys. Registering and removing a passkey are Spring
 * Security's {@code POST /webauthn/register} and {@code DELETE /webauthn/register/{id}};
 * this lists them and renames them.
 */
@RestController
@RequestMapping("/account/passkeys")
class PasskeyController {

	private final PasskeyManager passkeyManager;

	private final PasskeyUserDirectory directory;

	PasskeyController(PasskeyManager passkeyManager, PasskeyUserDirectory directory) {
		this.passkeyManager = passkeyManager;
		this.directory = directory;
	}

	@GetMapping
	List<Passkey> list(Authentication authentication) {
		return this.passkeyManager.list(userId(authentication));
	}

	@PatchMapping("/{credentialId}")
	ResponseEntity<Void> rename(Authentication authentication, @PathVariable String credentialId,
			@Valid @RequestBody Rename rename) {
		if (!this.passkeyManager.rename(userId(authentication), credentialId, rename.label())) {
			throw new ResourceNotFoundException("Passkey not found");
		}
		return ResponseEntity.noContent().build();
	}

	private String userId(Authentication authentication) {
		return this.directory.findByUsername(authentication.getName())
			.orElseThrow(() -> new ResourceNotFoundException("User not found"))
			.id();
	}

	record Rename(@NotBlank @Size(max = 100) String label) {
	}

}
