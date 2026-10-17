package com.example.commons.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that the module has only the Bouncy Castle artifacts for its own format on the
 * classpath, so a consumer of one format does not pull in the other.
 */
class DependencyIsolationTest {

	@Test
	void hasItsOwnBouncyCastleClasses() throws ClassNotFoundException {
		assertThat(Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")).isNotNull();
	}

	@Test
	void doesNotHaveOpenPgp() {
		assertThatThrownBy(() -> Class.forName("org.bouncycastle.openpgp.PGPPublicKey"))
			.isInstanceOf(ClassNotFoundException.class);
	}

	@Test
	void doesNotHaveCms() {
		assertThatThrownBy(() -> Class.forName("org.bouncycastle.cms.CMSSignedData"))
			.isInstanceOf(ClassNotFoundException.class);
	}

}
