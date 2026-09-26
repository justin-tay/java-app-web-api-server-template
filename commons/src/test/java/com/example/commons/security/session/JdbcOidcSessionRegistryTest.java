package com.example.commons.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * Tests {@link JdbcOidcSessionRegistry} against the tables the Liquibase changelogs
 * create, run as plain SQL scripts on an in-memory H2 database.
 */
class JdbcOidcSessionRegistryTest {

	private static final String ISSUER = "https://provider.example";

	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

	private EmbeddedDatabase database;

	private JdbcClient jdbcClient;

	private JdbcOidcSessionRegistry registry;

	@BeforeEach
	void createDatabase() {
		this.database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
			.setName(UUID.randomUUID().toString())
			.addScript("classpath:db/changelog/003-spring-session-schema.sql")
			.addScript("classpath:db/changelog/005-oidc-session-registry.sql")
			.build();
		this.jdbcClient = JdbcClient.create((DataSource) this.database);
		this.registry = registry(NOW);
	}

	@AfterEach
	void shutDownDatabase() {
		this.database.shutdown();
	}

	@Test
	void removesTheSessionsOfAProviderSession() {
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));
		this.registry.saveSessionInformation(session("local-2", "provider-2", "subject"));

		Iterable<OidcSessionInformation> removed = this.registry
			.removeSessionInformation(logoutToken("provider-1", "subject"));

		assertThat(removed).extracting(OidcSessionInformation::getSessionId).containsExactly("local-1");
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void removesEverySessionOfASubjectWhenTheTokenHasNoProviderSession() {
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));
		this.registry.saveSessionInformation(session("local-2", "provider-2", "subject"));
		this.registry.saveSessionInformation(session("local-3", "provider-3", "other-subject"));

		Iterable<OidcSessionInformation> removed = this.registry.removeSessionInformation(logoutToken(null, "subject"));

		assertThat(removed).extracting(OidcSessionInformation::getSessionId)
			.containsExactlyInAnyOrder("local-1", "local-2");
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void ignoresATokenFromAnotherIssuerOrForAnotherAudience() {
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));

		assertThat(this.registry
			.removeSessionInformation(logoutToken("provider-1", "subject", "https://other.example", "client")))
			.isEmpty();
		assertThat(this.registry.removeSessionInformation(logoutToken("provider-1", "subject", ISSUER, "other-client")))
			.isEmpty();
		assertThat(rowCount()).isEqualTo(1);
	}

	@Test
	void aLogoutHandledByAnotherInstanceEndsTheSessionOnlyOnce() {
		JdbcOidcSessionRegistry otherInstance = registry(NOW);
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));
		OidcLogoutToken token = logoutToken("provider-1", "subject");

		Iterable<OidcSessionInformation> first = otherInstance.removeSessionInformation(token);
		Iterable<OidcSessionInformation> second = this.registry.removeSessionInformation(token);

		assertThat(first).extracting(OidcSessionInformation::getSessionId).containsExactly("local-1");
		assertThat(second).isEmpty();
	}

	@Test
	void removesTheLinkOfALocalSession() {
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));

		assertThat(this.registry.removeSessionInformation("local-1")).isNotNull()
			.extracting(OidcSessionInformation::getSessionId)
			.isEqualTo("local-1");
		assertThat(this.registry.removeSessionInformation("local-1")).isNull();
	}

	@Test
	void replacesTheLinkWhenTheSameSessionIsSavedAgain() {
		this.registry.saveSessionInformation(session("local-1", "provider-1", "subject"));
		this.registry.saveSessionInformation(session("local-1", "provider-2", "subject"));

		assertThat(this.registry.removeSessionInformation(logoutToken("provider-1", "subject"))).isEmpty();
		assertThat(this.registry.removeSessionInformation(logoutToken("provider-2", "subject"))).hasSize(1);
	}

	@Test
	void purgesTheLinksOfExpiredSessionsOnceTheyAreOlderThanTheGracePeriod() {
		this.registry.saveSessionInformation(session("expired", "provider-1", "subject"));
		this.registry.saveSessionInformation(session("live", "provider-2", "subject"));
		insertSessionRow("live");

		registry(NOW.plus(JdbcOidcSessionRegistry.ORPHAN_GRACE_PERIOD).plusSeconds(1))
			.saveSessionInformation(session("new", "provider-3", "subject"));

		assertThat(linkedSessionIds()).containsExactlyInAnyOrder("live", "new");
	}

	@Test
	void keepsTheLinkOfASessionThatHasNotBeenSavedYetWithinTheGracePeriod() {
		this.registry.saveSessionInformation(session("pending", "provider-1", "subject"));

		registry(NOW.plusSeconds(1)).saveSessionInformation(session("new", "provider-2", "subject"));

		assertThat(linkedSessionIds()).containsExactlyInAnyOrder("pending", "new");
	}

	private JdbcOidcSessionRegistry registry(Instant now) {
		return new JdbcOidcSessionRegistry(this.jdbcClient, Clock.fixed(now, ZoneOffset.UTC), "SPRING_SESSION");
	}

	private long rowCount() {
		return this.jdbcClient.sql("SELECT COUNT(*) FROM OIDC_SESSION").query(Long.class).single();
	}

	private List<String> linkedSessionIds() {
		return this.jdbcClient.sql("SELECT SESSION_ID FROM OIDC_SESSION").query(String.class).list();
	}

	private void insertSessionRow(String sessionId) {
		this.jdbcClient.sql("""
				INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME,
				MAX_INACTIVE_INTERVAL, EXPIRY_TIME, PRINCIPAL_NAME) VALUES (?, ?, 0, 0, 1800, 0, 'user')""")
			.param(UUID.randomUUID().toString())
			.param(sessionId)
			.update();
	}

	private static OidcSessionInformation session(String localSessionId, String providerSessionId, String subject) {
		OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
			.issuer(ISSUER)
			.subject(subject)
			.audience(List.of("client"))
			.issuedAt(NOW)
			.expiresAt(NOW.plusSeconds(300))
			.claim("sid", providerSessionId)
			.build();
		return new OidcSessionInformation(localSessionId, Map.of(), new DefaultOidcUser(List.of(), idToken));
	}

	private static OidcLogoutToken logoutToken(String providerSessionId, String subject) {
		return logoutToken(providerSessionId, subject, ISSUER, "client");
	}

	private static OidcLogoutToken logoutToken(String providerSessionId, String subject, String issuer,
			String audience) {
		OidcLogoutToken.Builder builder = OidcLogoutToken.withTokenValue("logout-token")
			.issuer(issuer)
			.subject(subject)
			.audience(List.of(audience))
			.issuedAt(NOW)
			.jti("jti")
			.events(Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
		if (providerSessionId != null) {
			builder.sessionId(providerSessionId);
		}
		return builder.build();
	}

}
