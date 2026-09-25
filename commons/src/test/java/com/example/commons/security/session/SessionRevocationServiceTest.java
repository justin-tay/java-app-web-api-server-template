package com.example.commons.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

@ExtendWith(OutputCaptureExtension.class)
class SessionRevocationServiceTest {

	private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();

	private final InMemorySessionRepository sessionRepository = new InMemorySessionRepository();

	private final SessionRevocationService service = new SessionRevocationService(this.sessionRegistry,
			this.sessionRepository, new SessionLifecycleAuditLogger());

	@Test
	void expiresAndAuditsEverySessionForTheUsername(CapturedOutput output) {
		this.sessionRegistry.registerNewSession("session-1", "test-user");
		this.sessionRegistry.registerNewSession("session-2", "test-user");
		this.sessionRegistry.registerNewSession("session-3", "other-user");
		this.sessionRepository.save(auditedSession("session-1", "audit-1"));
		this.sessionRepository.save(auditedSession("session-2", "audit-2"));

		this.service.revoke("test-user", "privilege_change");

		assertThat(this.sessionRegistry.getAllSessions("test-user", true)).allMatch(SessionInformation::isExpired)
			.hasSize(2);
		assertThat(this.sessionRegistry.getSessionInformation("session-3").isExpired()).isFalse();
		assertThat(output).contains("session.id=\"audit-1\"")
			.contains("session.id=\"audit-2\"")
			.contains("session.termination_reason=\"privilege_change\"")
			.doesNotContain("session-1")
			.doesNotContain("session-2");
	}

	@Test
	void doesNothingWhenTheUsernameHasNoActiveSessions(CapturedOutput output) {
		this.sessionRegistry.registerNewSession("session-3", "other-user");

		this.service.revoke("test-user", "privilege_change");

		assertThat(this.sessionRegistry.getSessionInformation("session-3").isExpired()).isFalse();
		assertThat(output).doesNotContain("destroy_session");
	}

	private static MapSession auditedSession(String id, String auditId) {
		MapSession session = new MapSession(id);
		session.setAttribute(SessionLifecycleAuditLogger.AUDIT_SESSION_ID_ATTRIBUTE, auditId);
		return session;
	}

	/**
	 * A {@link FindByIndexNameSessionRepository} held in memory, standing in for the JDBC
	 * one.
	 */
	static final class InMemorySessionRepository implements FindByIndexNameSessionRepository<MapSession> {

		private final Map<String, MapSession> sessions = new HashMap<>();

		@Override
		public MapSession createSession() {
			return new MapSession();
		}

		@Override
		public void save(MapSession session) {
			this.sessions.put(session.getId(), session);
		}

		@Override
		public MapSession findById(String id) {
			return this.sessions.get(id);
		}

		@Override
		public void deleteById(String id) {
			this.sessions.remove(id);
		}

		@Override
		public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
			return Map.of();
		}

	}

}
