package com.example.commons.security.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;

/**
 * The application's private JWKS, read from one or more resource locations and read again
 * on a schedule so that keys rotated at the source, such as a {@code cdk-jwks-secret}
 * secret in AWS Secrets Manager, are picked up without a restart (see docs/adr/0020).
 *
 * <p>
 * Each location holds the keys of one {@code use}, {@code sig} or {@code enc}, or both
 * when it is the only location, and each use comes from one location. Within a use the
 * keys are in the order the rotation keeps them:
 * <ul>
 * <li>{@code sig}: every key is published; the first key with a private part signs. A
 * retired key keeps only its public part, so it is never the signing key.</li>
 * <li>{@code enc}: every key keeps its private part and decrypts a JWE whose {@code kid}
 * names it; every key is published except the first when there are three, the key the
 * next rotation deletes.</li>
 * </ul>
 *
 * <p>
 * The first read happens on construction and fails startup if a location cannot be read
 * or is not a valid JWKS; an empty JWKS is accepted, since a rotated secret is empty
 * until its first rotation, and {@link JwksHealthIndicator} keeps the application out of
 * readiness until it has keys. A later read that fails keeps the last good keys. A caller
 * that finds no signing key, or no key for a JWE's {@code kid}, may ask for an immediate
 * read with {@link #refreshNow()}, which is rate-limited.
 */
public class RefreshingJwks implements SmartLifecycle {

	/**
	 * Minimum time between two reads asked for by {@link #refreshNow()}.
	 */
	static final Duration ON_DEMAND_REFRESH_INTERVAL = Duration.ofSeconds(30);

	private static final Logger logger = LoggerFactory.getLogger(RefreshingJwks.class);

	private final List<Resource> locations;

	private final Duration refreshInterval;

	private final Clock clock;

	private final Object refreshLock = new Object();

	private volatile Keys keys;

	private volatile Instant lastRefreshFailure;

	private Instant lastRefreshAttempt;

	private ScheduledExecutorService scheduler;

	/**
	 * Reads the JWKS locations for the first time.
	 * @param locations the JWKS locations
	 * @param refreshInterval the interval between scheduled reads
	 * @param clock the clock
	 * @throws IllegalStateException if a location cannot be read or its keys are invalid
	 */
	public RefreshingJwks(List<Resource> locations, Duration refreshInterval, Clock clock) {
		Assert.notEmpty(locations, "locations must not be empty");
		this.locations = List.copyOf(locations);
		this.refreshInterval = refreshInterval;
		this.clock = clock;
		this.keys = read();
		logger.info("Loaded the private JWKS: {}", this.keys.summary());
	}

	/**
	 * Returns the key that signs {@code private_key_jwt} client assertions: the first
	 * {@code sig} key with a private part. When there is none, reads the locations again
	 * first, subject to the rate limit of {@link #refreshNow()}.
	 * @return the signing key, if any
	 */
	public Optional<JWK> signingKey() {
		Optional<JWK> signingKey = this.keys.signingKey();
		if (signingKey.isEmpty() && refreshNow()) {
			signingKey = this.keys.signingKey();
		}
		return signingKey;
	}

	/**
	 * Returns the {@code enc} keys a JWE with the given {@code kid} may be decrypted
	 * with, which is every {@code enc} key when {@code kid} is {@code null}.
	 * @param kid the JWE's {@code kid}, or {@code null}
	 * @return the decryption keys, empty when none matches
	 */
	public List<JWK> decryptionKeys(String kid) {
		return this.keys.encryption()
			.stream()
			.filter(JWK::isPrivate)
			.filter(key -> kid == null || kid.equals(key.getKeyID()))
			.toList();
	}

	/**
	 * Returns whether any {@code enc} key is configured, in which case ID tokens must be
	 * encrypted.
	 * @return whether there are {@code enc} keys
	 */
	public boolean hasEncryptionKeys() {
		return !this.keys.encryption().isEmpty();
	}

	/**
	 * Returns the public JWKS to publish at {@value JwksController#JWKS_PATH}: the public
	 * parts of every {@code sig} key and of every {@code enc} key but the first when
	 * there are three.
	 * @return the public JWKS
	 */
	public JWKSet publicJwks() {
		List<JWK> encryption = this.keys.encryption();
		List<JWK> publishedEncryption = (encryption.size() == 3) ? encryption.subList(1, 3) : encryption;
		return new JWKSet(Stream.concat(this.keys.signature().stream(), publishedEncryption.stream())
			.map(JWK::toPublicJWK)
			.toList());
	}

	/**
	 * Returns the state {@link JwksHealthIndicator} reports.
	 * @return the current state
	 */
	public State state() {
		Keys current = this.keys;
		return new State(current.signingKey().map(JWK::getKeyID).orElse(null), current.signature().size(),
				current.encryption().size(), current.emptyLocations(), current.readAt(), this.lastRefreshFailure);
	}

	/**
	 * Reads the locations again now, unless a refresh happened within the last
	 * {@link #ON_DEMAND_REFRESH_INTERVAL}. The first read, on construction, does not
	 * count, so a key missing just after startup is read at once.
	 * @return whether the locations were read and the keys replaced
	 */
	public boolean refreshNow() {
		synchronized (this.refreshLock) {
			if (this.lastRefreshAttempt != null
					&& this.clock.instant().isBefore(this.lastRefreshAttempt.plus(ON_DEMAND_REFRESH_INTERVAL))) {
				return false;
			}
			return refresh();
		}
	}

	/**
	 * Reads the locations again, keeping the current keys if the read fails.
	 * @return whether the keys were replaced
	 */
	boolean refresh() {
		synchronized (this.refreshLock) {
			this.lastRefreshAttempt = this.clock.instant();
			try {
				Keys refreshed = read();
				if (!refreshed.keyIds().equals(this.keys.keyIds())) {
					logger.info("Refreshed the private JWKS: {}", refreshed.summary());
				}
				this.keys = refreshed;
				this.lastRefreshFailure = null;
				return true;
			}
			catch (RuntimeException ex) {
				this.lastRefreshFailure = this.lastRefreshAttempt;
				logger.warn("Unable to refresh the private JWKS, keeping the keys read at {}: {}", this.keys.readAt(),
						ex.getMessage());
				return false;
			}
		}
	}

	@Override
	public void start() {
		synchronized (this.refreshLock) {
			if (this.scheduler != null) {
				return;
			}
			this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "jwks-refresh");
				thread.setDaemon(true);
				return thread;
			});
			long interval = this.refreshInterval.toMillis();
			this.scheduler.scheduleWithFixedDelay(this::refresh, interval, interval, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void stop() {
		synchronized (this.refreshLock) {
			if (this.scheduler != null) {
				this.scheduler.shutdownNow();
				this.scheduler = null;
			}
		}
	}

	@Override
	public boolean isRunning() {
		synchronized (this.refreshLock) {
			return this.scheduler != null;
		}
	}

	private Keys read() {
		List<JWK> signature = new ArrayList<>();
		List<JWK> encryption = new ArrayList<>();
		List<String> emptyLocations = new ArrayList<>();
		Map<KeyUse, String> useLocations = new HashMap<>();
		Set<String> keyIds = new HashSet<>();
		for (Resource location : this.locations) {
			List<JWK> locationKeys = load(location);
			if (locationKeys.isEmpty()) {
				emptyLocations.add(location.getDescription());
			}
			for (JWK key : locationKeys) {
				String description = location.getDescription();
				if (key.getKeyID() == null || !keyIds.add(key.getKeyID())) {
					throw new IllegalStateException(
							description + " has a key without a kid, or with a kid another key has");
				}
				KeyUse use = key.getKeyUse();
				if (!KeyUse.SIGNATURE.equals(use) && !KeyUse.ENCRYPTION.equals(use)) {
					throw new IllegalStateException(
							description + " has key " + key.getKeyID() + " whose use is not \"sig\" or \"enc\"");
				}
				String useLocation = useLocations.putIfAbsent(use, description);
				if (useLocation != null && !useLocation.equals(description)) {
					throw new IllegalStateException("Both " + useLocation + " and " + description + " have \""
							+ use.identifier() + "\" keys; each use must come from one location");
				}
				(KeyUse.SIGNATURE.equals(use) ? signature : encryption).add(key);
			}
		}
		if (this.locations.size() > 1) {
			for (Resource location : this.locations) {
				long uses = useLocations.values().stream().filter(location.getDescription()::equals).count();
				if (uses > 1) {
					throw new IllegalStateException(location.getDescription()
							+ " has both \"sig\" and \"enc\" keys; with more than one location, each holds one use");
				}
			}
		}
		return new Keys(List.copyOf(signature), List.copyOf(encryption), List.copyOf(emptyLocations),
				this.clock.instant());
	}

	private static List<JWK> load(Resource location) {
		try (InputStream inputStream = location.getInputStream()) {
			return JWKSet.load(inputStream).getKeys();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to read " + location.getDescription() + ": " + ex.getMessage(), ex);
		}
		catch (ParseException ex) {
			// The parser's message may quote the content, which is private key material.
			throw new IllegalStateException(location.getDescription() + " is not a valid JWKS");
		}
	}

	/**
	 * The keys of one read, by use, in the order of their locations.
	 */
	private record Keys(List<JWK> signature, List<JWK> encryption, List<String> emptyLocations, Instant readAt) {

		Optional<JWK> signingKey() {
			return this.signature.stream().filter(JWK::isPrivate).findFirst();
		}

		List<String> keyIds() {
			return Stream.concat(this.signature.stream(), this.encryption.stream()).map(JWK::getKeyID).toList();
		}

		String summary() {
			return "signing key " + signingKey().map(JWK::getKeyID).orElse("(none)") + ", sig keys "
					+ ids(this.signature) + ", enc keys " + ids(this.encryption);
		}

		private static String ids(List<JWK> keys) {
			return keys.stream().map(JWK::getKeyID).collect(Collectors.joining(", ", "[", "]"));
		}

	}

	/**
	 * The JWKS state reported by {@link JwksHealthIndicator}. Holds key IDs and counts
	 * only, never key material.
	 *
	 * @param signingKeyId the {@code kid} of the signing key, or {@code null} if none
	 * @param signatureKeys the number of {@code sig} keys
	 * @param encryptionKeys the number of {@code enc} keys
	 * @param emptyLocations the descriptions of locations that have no keys yet
	 * @param refreshedAt when the keys were last read successfully
	 * @param lastRefreshFailure when the last read failed, or {@code null} if it
	 * succeeded
	 */
	public record State(String signingKeyId, int signatureKeys, int encryptionKeys, List<String> emptyLocations,
			Instant refreshedAt, Instant lastRefreshFailure) {

	}

}
