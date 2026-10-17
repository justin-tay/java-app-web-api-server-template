package com.example.commons.crypto.cms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that the module has only the Bouncy Castle artifacts for its own format on the
 * classpath, and does not depend on the other format's module, so a consumer of one
 * format does not pull in the other.
 */
class DependencyIsolationTest {

	@Test
	void hasItsOwnBouncyCastleClasses() throws ClassNotFoundException {
		assertThat(Class.forName("org.bouncycastle.cms.CMSSignedData")).isNotNull();
	}

	@Test
	void doesNotHaveOpenPgp() {
		assertThatThrownBy(() -> Class.forName("org.bouncycastle.openpgp.PGPPublicKey"))
			.isInstanceOf(ClassNotFoundException.class);
	}

	@Test
	void doesNotDependOnTheOpenPgpModule() {
		assertThatThrownBy(() -> Class.forName("com.example.commons.crypto.openpgp.Pgp"))
			.isInstanceOf(ClassNotFoundException.class);
	}

}
