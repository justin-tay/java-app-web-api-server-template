package com.example.commons.security.session;

import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.oidc.authentication.logout.LogoutTokenClaimNames;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * An {@link OidcSessionRegistry} kept in the {@code OIDC_SESSION} table (see
 * {@code com/example/commons/session/oidc/jdbc/schema.yaml}), so a back-channel logout
 * token that reaches any instance resolves the local sessions that logged in through any
 * other. It matches the way {@code InMemoryOidcSessionRegistry} does: by issuer and
 * provider session ID when the logout token has one, otherwise by issuer and subject, and
 * in both cases only when the audiences overlap.
 *
 * <p>
 * The link is written when the session logs in, before Spring Session has saved the
 * session row, so the table cannot reference {@code SPRING_SESSION}. A row whose session
 * has since expired is harmless, because a logout that resolves it finds no session to
 * end. Saving a link deletes such rows older than a grace period, which keeps the table
 * to the size of the session table.
 */
public class JdbcOidcSessionRegistry implements OidcSessionRegistry {

	static final Duration ORPHAN_GRACE_PERIOD = Duration.ofMinutes(10);

	private static final String INSERT = """
			INSERT INTO OIDC_SESSION (SESSION_ID, CREATION_TIME, ISSUER, SUBJECT, PROVIDER_SESSION_ID,
			SESSION_INFORMATION) VALUES (?, ?, ?, ?, ?, ?)""";

	private final JdbcClient jdbcClient;

	private final Clock clock;

	private final String sessionTableName;

	private final SerializingConverter serializer = new SerializingConverter();

	private final DeserializingConverter deserializer = new DeserializingConverter(
			JdbcOidcSessionRegistry.class.getClassLoader());

	/**
	 * Creates a registry.
	 * @param jdbcClient the JDBC client of the database that holds the session tables
	 * @param clock the clock that dates each link
	 * @param sessionTableName the Spring Session table, to tell which links have expired
	 */
	public JdbcOidcSessionRegistry(JdbcClient jdbcClient, Clock clock, String sessionTableName) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
		this.sessionTableName = sessionTableName;
	}

	@Override
	public void saveSessionInformation(OidcSessionInformation info) {
		OidcUser principal = info.getPrincipal();
		URL issuer = principal.getIssuer();
		if (issuer == null) {
			// No logout token can match a session without an issuer.
			return;
		}
		long now = this.clock.millis();
		purgeExpiredLinks(now);
		this.jdbcClient.sql("DELETE FROM OIDC_SESSION WHERE SESSION_ID = ?").param(info.getSessionId()).update();
		this.jdbcClient.sql(INSERT)
			.param(info.getSessionId())
			.param(now)
			.param(issuer.toString())
			.param(principal.getSubject())
			.param(principal.getClaimAsString(LogoutTokenClaimNames.SID))
			.param(this.serializer.convert(info))
			.update();
	}

	@Override
	public OidcSessionInformation removeSessionInformation(String clientSessionId) {
		Optional<byte[]> stored = this.jdbcClient
			.sql("SELECT SESSION_INFORMATION FROM OIDC_SESSION WHERE SESSION_ID = ?")
			.param(clientSessionId)
			.query(byte[].class)
			.optional();
		if (stored.isEmpty() || !remove(clientSessionId)) {
			return null;
		}
		return deserialize(stored.get());
	}

	@Override
	public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token) {
		String issuer = token.getIssuer().toString();
		String providerSessionId = token.getSessionId();
		List<byte[]> candidates = (providerSessionId != null) ? this.jdbcClient
			.sql("SELECT SESSION_INFORMATION FROM OIDC_SESSION WHERE ISSUER = ? AND PROVIDER_SESSION_ID = ?")
			.params(issuer, providerSessionId)
			.query(byte[].class)
			.list() : subjectCandidates(issuer, token.getSubject());
		List<OidcSessionInformation> removed = new ArrayList<>();
		for (byte[] stored : candidates) {
			OidcSessionInformation info = deserialize(stored);
			// Removing the row is what claims the session, so when logout tokens for the
			// same session reach two instances at once, only one of them ends it.
			if (audiencesOverlap(token, info) && remove(info.getSessionId())) {
				removed.add(info);
			}
		}
		return removed;
	}

	private List<byte[]> subjectCandidates(String issuer, String subject) {
		if (subject == null) {
			return List.of();
		}
		return this.jdbcClient.sql("SELECT SESSION_INFORMATION FROM OIDC_SESSION WHERE ISSUER = ? AND SUBJECT = ?")
			.params(issuer, subject)
			.query(byte[].class)
			.list();
	}

	private boolean remove(String sessionId) {
		return this.jdbcClient.sql("DELETE FROM OIDC_SESSION WHERE SESSION_ID = ?").param(sessionId).update() == 1;
	}

	private void purgeExpiredLinks(long now) {
		this.jdbcClient.sql("DELETE FROM OIDC_SESSION WHERE CREATION_TIME < ? AND NOT EXISTS (SELECT 1 FROM "
				+ this.sessionTableName + " WHERE " + this.sessionTableName + ".SESSION_ID = OIDC_SESSION.SESSION_ID)")
			.param(now - ORPHAN_GRACE_PERIOD.toMillis())
			.update();
	}

	private OidcSessionInformation deserialize(byte[] stored) {
		return (OidcSessionInformation) this.deserializer.convert(stored);
	}

	private static boolean audiencesOverlap(OidcLogoutToken token, OidcSessionInformation info) {
		List<String> thatAudience = info.getPrincipal().getAudience();
		return thatAudience != null && !Collections.disjoint(token.getAudience(), thatAudience);
	}

}
