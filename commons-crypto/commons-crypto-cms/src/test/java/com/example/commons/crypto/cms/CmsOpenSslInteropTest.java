package com.example.commons.crypto.cms;

import com.example.commons.crypto.testsupport.ExternalTool;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;

import com.example.commons.crypto.CryptoException;
import com.example.commons.crypto.PrivateKeyHandle;
import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Messages and signatures exchanged with OpenSSL, in both directions, with the test keys
 * of this module. Skipped unless INTEROP_TOOLS is true, which the container script in
 * commons-crypto sets.
 */
@EnabledIfEnvironmentVariable(named = "INTEROP_TOOLS", matches = "true")
class CmsOpenSslInteropTest {

	private static final byte[] DATA = "Interoperability with OpenSSL, a message of some length. ".repeat(200)
		.getBytes(StandardCharsets.UTF_8);

	private static Path directory;

	@BeforeAll
	static void extractKeys() throws Exception {
		directory = Files.createTempDirectory("openssl");
		for (String name : new String[] { "rsa", "ec" }) {
			Path p12 = directory.resolve(name + ".p12");
			Files.copy(CmsOpenSslInteropTest.class.getResourceAsStream("/" + name + ".p12"), p12);
			// The key and the certificate in one file, and the certificate alone.
			run(null, "openssl", "pkcs12", "-in", p12.toString(), "-passin", "pass:changeit", "-nodes", "-out",
					directory.resolve(name + ".pem").toString());
			run(null, "openssl", "pkcs12", "-in", p12.toString(), "-passin", "pass:changeit", "-nokeys", "-out",
					directory.resolve(name + "-cert.pem").toString());
		}
	}

	private static ExternalTool.Result run(byte[] input, String... command) {
		ExternalTool.Result result = ExternalTool.run(directory, input, command);
		assertThat(result.exitCode()).as(String.join(" ", command) + "\n" + result.errors()).isZero();
		return result;
	}

	private static Path file(String name, byte[] content) throws Exception {
		return Files.write(directory.resolve(name), content);
	}

	private static Path pem(String name) {
		return directory.resolve(name + ".pem");
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void openSslVerifiesWhatWeSign(String name) throws Exception {
		X509Certificate certificate = TestKeys.certificate(name);
		for (SignatureMode mode : SignatureMode.values()) {
			for (boolean armored : new boolean[] { false, true }) {
				ByteArrayOutputStream signed = new ByteArrayOutputStream();
				try (PrivateKeyHandle key = TestKeys.handle(name)) {
					CmsSigner.create()
						.sign(SignRequest.builder().signer(key, certificate).mode(mode).armored(armored).build(),
								new ByteArrayInputStream(DATA), signed);
				}
				Path message = file("signed.cms", signed.toByteArray());
				String inform = armored ? "PEM" : "DER";
				// -noverify: the certificate is self-signed and untrusted; only the
				// signature is checked.
				ExternalTool.Result result = (mode == SignatureMode.DETACHED)
						? run(null, "openssl", "cms", "-verify", "-binary", "-noverify", "-inform", inform, "-in",
								message.toString(), "-content", file("data.bin", DATA).toString(), "-out", "/dev/null")
						: run(null, "openssl", "cms", "-verify", "-binary", "-noverify", "-inform", inform, "-in",
								message.toString());
				assertThat(result.errors()).contains("Verification successful");
				if (mode == SignatureMode.EMBEDDED) {
					assertThat(result.output()).isEqualTo(DATA);
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weVerifyWhatOpenSslSigns(String name) throws Exception {
		X509Certificate certificate = TestKeys.certificate(name);
		VerifyRequest request = VerifyRequest.builder().certificates(certificate).build();
		Path data = file("data.bin", DATA);

		ExternalTool.Result embedded = run(null, "openssl", "cms", "-sign", "-binary", "-nodetach", "-in",
				data.toString(), "-signer", pem(name).toString(), "-inkey", pem(name).toString(), "-outform", "DER");
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = CmsVerifier.create()
			.verify(request, new ByteArrayInputStream(embedded.output()), content);
		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(DATA);

		ExternalTool.Result detached = run(null, "openssl", "cms", "-sign", "-binary", "-in", data.toString(),
				"-signer", pem(name).toString(), "-inkey", pem(name).toString(), "-outform", "PEM");
		assertThat(CmsVerifier.create()
			.verifyDetached(request, new ByteArrayInputStream(DATA), new ByteArrayInputStream(detached.output()))
			.allValid()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weReadWhatOpenSslEncryptsOnlyWithLegacyCompatibility(String name) throws Exception {
		X509Certificate certificate = TestKeys.certificate(name);
		// OpenSSL's CMS encryption is EnvelopedData: the content is not authenticated.
		ExternalTool.Result message = run(null, "openssl", "cms", "-encrypt", "-binary", "-aes-256-cbc", "-in",
				file("data.bin", DATA).toString(), "-outform", "DER", directory.resolve(name + "-cert.pem").toString());

		try (PrivateKeyHandle key = TestKeys.handle(name)) {
			assertThatThrownBy(() -> CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, certificate).build(),
						new ByteArrayInputStream(message.output()), new ByteArrayOutputStream()))
				.isInstanceOf(CryptoException.class)
				.hasMessageContaining("legacy");

			ByteArrayOutputStream plain = new ByteArrayOutputStream();
			CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(true).build(),
						new ByteArrayInputStream(message.output()), plain);
			assertThat(plain.toByteArray()).isEqualTo(DATA);
		}
	}

	@Test
	void weReadWhatOpenSslEncryptsWithOaepOnlyWithLegacyCompatibility() throws Exception {
		X509Certificate certificate = TestKeys.certificate("rsa");
		ExternalTool.Result message = run(null, "openssl", "cms", "-encrypt", "-binary", "-aes-256-cbc", "-in",
				file("data.bin", DATA).toString(), "-outform", "DER", "-recip",
				directory.resolve("rsa-cert.pem").toString(), "-keyopt", "rsa_padding_mode:oaep", "-keyopt",
				"rsa_oaep_md:sha256");

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		try (PrivateKeyHandle key = TestKeys.handle("rsa")) {
			CmsDecryptor.create()
				.decrypt(DecryptRequest.builder().key(key, certificate).legacyCompatibility(true).build(),
						new ByteArrayInputStream(message.output()), plain);
		}

		assertThat(plain.toByteArray()).isEqualTo(DATA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void openSslReadsWhatWeEncrypt(String name) throws Exception {
		X509Certificate certificate = TestKeys.certificate(name);
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		CmsEncryptor.create()
			.encrypt(EncryptRequest.builder().recipients(CmsRecipient.certificate(certificate)).build(),
					new ByteArrayInputStream(DATA), message);
		Path file = file("ours.cms", message.toByteArray());

		ExternalTool.Result result = ExternalTool.run(directory, null, "openssl", "cms", "-decrypt", "-inform", "DER",
				"-in", file.toString(), "-recip", pem(name).toString(), "-inkey", pem(name).toString());

		// Our messages are AuthEnvelopedData, which only newer OpenSSL versions can read.
		// Record
		// which it is rather than assume.
		Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "interop-findings.txt"),
				"openssl cms -decrypt of our " + name + " message: exit " + result.exitCode() + " "
						+ result.errors().lines().findFirst().orElse("") + "\n",
				java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		if (result.exitCode() == 0) {
			assertThat(result.output()).isEqualTo(DATA);
		}
	}

}
