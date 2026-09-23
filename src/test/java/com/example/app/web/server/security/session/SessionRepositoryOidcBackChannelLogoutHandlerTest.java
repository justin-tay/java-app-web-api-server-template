package com.example.app.web.server.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.InMemoryOidcSessionRegistry;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

class SessionRepositoryOidcBackChannelLogoutHandlerTest {

	private static final String ISSUER = "https://provider.example";

	private final MapSessionRepository sessionRepository = new MapSessionRepository(new ConcurrentHashMap<>());

	private final InMemoryOidcSessionRegistry oidcSessionRegistry = new InMemoryOidcSessionRegistry();

	private final SessionRepositoryOidcBackChannelLogoutHandler handler = new SessionRepositoryOidcBackChannelLogoutHandler(
			this.oidcSessionRegistry, this.sessionRepository, new SessionLifecycleAuditLogger());

	private final Logger logger = (Logger) LoggerFactory.getLogger(SessionLifecycleAuditLogger.class);

	private ListAppender<ILoggingEvent> logEvents;

	@BeforeEach
	void setUpAppender() {
		this.logEvents = new ListAppender<>();
		this.logEvents.start();
		this.logger.addAppender(this.logEvents);
	}

	@AfterEach
	void removeAppender() {
		this.logger.detachAppender(this.logEvents);
		this.logEvents.stop();
	}

	@Test
	void deletesTheLinkedSessionAndLogsItsAuditIdentifier() {
		MapSession session = linkedSession("provider-session");
		MapSession otherSession = linkedSession("other-provider-session");

		logout("provider-session");

		assertThat(this.sessionRepository.findById(session.getId())).isNull();
		assertThat(this.sessionRepository.findById(otherSession.getId())).isNotNull();
		assertThat(this.logEvents.list).singleElement().satisfies(event -> {
			assertThat(event.getKeyValuePairs().toString()).doesNotContain(session.getId())
				.contains("session.id=\"audit-provider-session\"")
				.contains("session.termination_reason=\"back_channel_logout\"");
		});
	}

	@Test
	void ignoresAnAuthenticationThatIsNotALogoutToken() {
		MapSession session = linkedSession("provider-session");

		this.handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new TestingAuthenticationToken("user", null));

		assertThat(this.sessionRepository.findById(session.getId())).isNotNull();
		assertThat(this.logEvents.list).isEmpty();
	}

	@Test
	void doesNothingWhenTheLinkedSessionHasAlreadyEnded() {
		MapSession session = linkedSession("provider-session");
		this.sessionRepository.deleteById(session.getId());

		logout("provider-session");

		assertThat(this.logEvents.list).isEmpty();
	}

	private MapSession linkedSession(String providerSessionId) {
		MapSession session = this.sessionRepository.createSession();
		session.setAttribute(SessionLifecycleAuditLogger.AUDIT_SESSION_ID_ATTRIBUTE, "audit-" + providerSessionId);
		this.sessionRepository.save(session);
		OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
			.issuer(ISSUER)
			.subject("subject")
			.audience(List.of("client"))
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("sid", providerSessionId)
			.build();
		this.oidcSessionRegistry.saveSessionInformation(
				new OidcSessionInformation(session.getId(), Map.of(), new DefaultOidcUser(List.of(), idToken)));
		return session;
	}

	private void logout(String providerSessionId) {
		OidcLogoutToken logoutToken = OidcLogoutToken.withTokenValue("logout-token")
			.issuer(ISSUER)
			.subject("subject")
			.audience(List.of("client"))
			.issuedAt(Instant.now())
			.jti("jti")
			.sessionId(providerSessionId)
			.events(Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()))
			.build();
		this.handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new TestingAuthenticationToken(logoutToken, null));
	}

}
