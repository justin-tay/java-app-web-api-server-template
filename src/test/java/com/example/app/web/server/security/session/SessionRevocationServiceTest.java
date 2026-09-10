package com.example.app.web.server.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

class SessionRevocationServiceTest {

	private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

	@SuppressWarnings("unchecked")
	private final FindByIndexNameSessionRepository<Session> sessionRepository = mock(
			FindByIndexNameSessionRepository.class);

	private final SessionLifecycleAuditLogger sessionLifecycleAuditLogger = mock(SessionLifecycleAuditLogger.class);

	private final SessionRevocationService service = new SessionRevocationService(this.sessionRegistry,
			this.sessionRepository, this.sessionLifecycleAuditLogger);

	@Test
	void expiresAndAuditsEverySessionForTheUsername() {
		SessionInformation first = new SessionInformation("test-user", "session-1", new Date());
		SessionInformation second = new SessionInformation("test-user", "session-2", new Date());
		when(this.sessionRegistry.getAllSessions("test-user", false)).thenReturn(List.of(first, second));
		MapSession firstSession = new MapSession("session-1");
		MapSession secondSession = new MapSession("session-2");
		when(this.sessionRepository.findById("session-1")).thenReturn(firstSession);
		when(this.sessionRepository.findById("session-2")).thenReturn(secondSession);

		this.service.revoke("test-user", "privilege_change");

		assertThat(first.isExpired()).isTrue();
		assertThat(second.isExpired()).isTrue();
		verify(this.sessionLifecycleAuditLogger).logSessionDestroyed(firstSession, "privilege_change");
		verify(this.sessionLifecycleAuditLogger).logSessionDestroyed(secondSession, "privilege_change");
	}

	@Test
	void doesNothingWhenTheUsernameHasNoActiveSessions() {
		when(this.sessionRegistry.getAllSessions("test-user", false)).thenReturn(List.of());

		this.service.revoke("test-user", "privilege_change");

		verifyNoInteractions(this.sessionLifecycleAuditLogger);
		verifyNoInteractions(this.sessionRepository);
	}

}
