package com.example.commons.crypto.testsupport;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.example.commons.crypto.pkcs11.Pkcs11Config;

/**
 * Which token the token tests use, from the environment, so that the same tests run against
 * different PKCS#11 implementations. The container script in commons-crypto/src/test/docker/pkcs11
 * sets it up. The tests are skipped unless PKCS11_LIBRARY is set.
 */
public final class TestToken {

	/** The alias of an RSA key (with its certificate) on the token. */
	public static final String RSA_ALIAS = env("PKCS11_RSA_ALIAS", "k");

	/** The alias of an EC P-256 key (with its certificate) on the token. */
	public static final String EC_ALIAS = env("PKCS11_EC_ALIAS", "ec");

	private TestToken() {
	}

	/**
	 * The aliases of more keys on the token (bigger RSA keys, other curves), from
	 * PKCS11_EXTRA_ALIASES as a comma separated list. Empty if the token has none.
	 */
	public static List<String> extraAliases() {
		String value = System.getenv("PKCS11_EXTRA_ALIASES");
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(",")).map(String::trim).filter((alias) -> !alias.isEmpty()).toList();
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return (value != null && !value.isBlank()) ? value : fallback;
	}

	public static Pkcs11Config config() {
		Pkcs11Config.Builder builder = Pkcs11Config.builder().library(Path.of(System.getenv("PKCS11_LIBRARY")));
		String slotListIndex = System.getenv("PKCS11_SLOT_LIST_INDEX");
		if (slotListIndex != null && !slotListIndex.isBlank()) {
			builder.slotListIndex(Integer.parseInt(slotListIndex));
		}
		String extra = System.getenv("PKCS11_EXTRA_SETTINGS");
		if (extra != null && !extra.isBlank()) {
			builder.extraSettings(extra.replace("\\n", "\n"));
		}
		return builder.build();
	}

	public static char[] pin() {
		return env("PKCS11_PIN", "1234").toCharArray();
	}

}
