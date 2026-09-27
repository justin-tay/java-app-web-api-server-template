package com.example.commons.accounts.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.commons.accounts.validation.ResourceId;
import com.example.commons.security.authentication.passkey.PasskeyManager;
import com.example.commons.security.authentication.passkey.PasskeyManager.Passkey;
import com.example.commons.web.problem.ResourceNotFoundException;

/**
 * Lists a user's passkeys and revokes one, for an administrator who needs to lock a lost
 * or stolen authenticator out. Present only when passkeys are enabled (see
 * docs/adr/0024). Like the rest of the administration API, a revocation needs a recent
 * login. Revoking a passkey does not end sessions it already started; end those with
 * {@code DELETE /admin/users/{id}/sessions}.
 */
@RestController
@Validated
@RequestMapping("/admin/users/{id}/passkeys")
@PreAuthorize("hasRole('USER_MANAGE')")
public class UserPasskeyAdminController {

	private final AdministrationService service;

	private final PasskeyManager passkeyManager;

	public UserPasskeyAdminController(AdministrationService service, PasskeyManager passkeyManager) {
		this.service = service;
		this.passkeyManager = passkeyManager;
	}

	@GetMapping
	public List<Passkey> list(@PathVariable @ResourceId String id) {
		return this.passkeyManager.list(this.service.user(id).getId());
	}

	@DeleteMapping("/{credentialId}")
	public ResponseEntity<Void> remove(@PathVariable @ResourceId String id, @PathVariable String credentialId) {
		if (!this.passkeyManager.remove(this.service.user(id).getId(), credentialId)) {
			throw new ResourceNotFoundException("Passkey");
		}
		return ResponseEntity.noContent().build();
	}

}
