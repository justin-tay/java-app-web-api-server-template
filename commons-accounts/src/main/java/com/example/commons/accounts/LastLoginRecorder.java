package com.example.commons.accounts;

import java.time.Clock;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;

import com.example.commons.accounts.domain.AppUserRepository;

/**
 * Records when a user last signed in. Both OIDC and passkey logins publish an
 * {@link InteractiveAuthenticationSuccessEvent}, so one listener covers both. Only the
 * sign-in time is stored, not where from or with what (see docs/adr/0028).
 */
public class LastLoginRecorder {

	private final AppUserRepository users;

	private final Clock clock;

	public LastLoginRecorder(AppUserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	@EventListener
	void onAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
		this.users.recordLogin(event.getAuthentication().getName(), this.clock.instant());
	}

}
