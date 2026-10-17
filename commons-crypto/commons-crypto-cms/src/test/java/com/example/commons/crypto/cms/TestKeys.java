package com.example.commons.crypto.cms;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.KeyStores;
import com.example.commons.crypto.PrivateKeyHandle;

/**
 * Test keys from the PKCS#12 files in the test resources, all with the password changeit.
 */
final class TestKeys {

	static final char[] PASSWORD = "changeit".toCharArray();

	private TestKeys() {
	}

	static PrivateKeyHandle handle(String name) throws Exception {
		return KeyStores.pkcs12(Path.of(TestKeys.class.getResource("/" + name + ".p12").toURI()), PASSWORD, name);
	}

	static X509Certificate certificate(String name) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = TestKeys.class.getResourceAsStream("/" + name + ".p12")) {
			keyStore.load(in, PASSWORD);
		}
		return (X509Certificate) keyStore.getCertificate(name);
	}

}
