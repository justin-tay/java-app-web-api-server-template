package com.example.commons.crypto.openpgp;

import com.example.commons.crypto.testsupport.ExternalTool;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import com.example.commons.crypto.SignatureMode;
import com.example.commons.crypto.VerifyResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Messages and signatures exchanged with GnuPG, in both directions. The keys are the test
 * keys of this module, imported into a fresh GnuPG home. Skipped unless INTEROP_TOOLS is
 * true, which the container script in commons-crypto sets.
 */
@EnabledIfEnvironmentVariable(named = "INTEROP_TOOLS", matches = "true")
class PgpGnupgInteropTest {

	private static final byte[] DATA = "Interoperability with GnuPG, a message of some length. ".repeat(200)
		.getBytes(StandardCharsets.UTF_8);

	private static final String[] PASSPHRASE = { "--batch", "--yes", "--no-tty", "--pinentry-mode", "loopback",
			"--passphrase", "changeit" };

	private static Path home;

	@BeforeAll
	static void importKeys() throws Exception {
		home = Files.createTempDirectory("gnupg");
		Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
		for (TestPgpKeys.TestKey key : List.of(TestPgpKeys.RSA, TestPgpKeys.EC)) {
			ExternalTool.Result result = gpg(key.secretArmored().getBytes(StandardCharsets.US_ASCII), "--import");
			assertThat(result.exitCode()).as(result.errors()).isZero();
		}
	}

	private static ExternalTool.Result gpg(byte[] input, String... arguments) {
		String[] command = new String[arguments.length + 6];
		command[0] = "gpg";
		command[1] = "--homedir";
		command[2] = home.toString();
		command[3] = "--batch";
		command[4] = "--yes";
		command[5] = "--no-tty";
		System.arraycopy(arguments, 0, command, 6, arguments.length);
		return ExternalTool.run(home, input, command);
	}

	private static ExternalTool.Result gpgWithPassphrase(byte[] input, String... arguments) {
		String[] all = new String[PASSPHRASE.length + arguments.length];
		System.arraycopy(PASSPHRASE, 0, all, 0, PASSPHRASE.length);
		System.arraycopy(arguments, 0, all, PASSPHRASE.length, arguments.length);
		return gpg(input, all);
	}

	private static TestPgpKeys.TestKey key(String name) {
		return "rsa".equals(name) ? TestPgpKeys.RSA : TestPgpKeys.EC;
	}

	private static String email(String name) {
		return name + "@example.com";
	}

	private static PgpSecretKeys secret(String name) {
		return key(name).secretKeys(TestPgpKeys.PASSPHRASE);
	}

	private static void assertGoodSignature(ExternalTool.Result result) {
		assertThat(result.exitCode()).as(result.errors()).isZero();
		assertThat(result.errors()).contains("Good signature");
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void gnuPgDecryptsWhatWeEncrypt(String name) {
		for (boolean armored : new boolean[] { false, true }) {
			ByteArrayOutputStream message = new ByteArrayOutputStream();
			PgpEncryptor.create()
				.encrypt(EncryptRequest.builder().recipients(key(name).publicKeys()).armored(armored).build(),
						new ByteArrayInputStream(DATA), message);

			ExternalTool.Result result = gpgWithPassphrase(message.toByteArray(), "--decrypt");

			assertThat(result.exitCode()).as(result.errors()).isZero();
			assertThat(result.output()).isEqualTo(DATA);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weDecryptWhatGnuPgEncrypts(String name) {
		for (String armor : new String[] { "--armor", "--no-armor" }) {
			ExternalTool.Result encrypted = gpg(DATA, armor, "--trust-model", "always", "--recipient", email(name),
					"--encrypt");
			assertThat(encrypted.exitCode()).as(encrypted.errors()).isZero();

			ByteArrayOutputStream plain = new ByteArrayOutputStream();
			PgpDecryptor.create()
				.decrypt(DecryptRequest.builder().keys(secret(name)).build(),
						new ByteArrayInputStream(encrypted.output()), plain);

			assertThat(plain.toByteArray()).isEqualTo(DATA);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void gnuPgVerifiesWhatWeSign(String name) throws Exception {
		for (SignatureMode mode : SignatureMode.values()) {
			for (boolean armored : new boolean[] { false, true }) {
				ByteArrayOutputStream signed = new ByteArrayOutputStream();
				PgpSigner.create()
					.sign(SignRequest.builder().signer(secret(name)).mode(mode).armored(armored).build(),
							new ByteArrayInputStream(DATA), signed);
				if (mode == SignatureMode.DETACHED) {
					Path data = Files.write(home.resolve("data.bin"), DATA);
					Path signature = Files.write(home.resolve("data.sig"), signed.toByteArray());
					assertGoodSignature(gpg(null, "--verify", signature.toString(), data.toString()));
				}
				else {
					ExternalTool.Result result = gpg(signed.toByteArray(), "--decrypt");
					assertGoodSignature(result);
					assertThat(result.output()).isEqualTo(DATA);
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void gnuPgVerifiesACleartextSignatureWeMake(String name) {
		byte[] text = "A line\n- a line with a dash\nA line with trailing spaces   \nThe last line"
			.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream signed = new ByteArrayOutputStream();
		PgpSigner.create()
			.sign(SignRequest.builder().signer(secret(name)).cleartext().build(), new ByteArrayInputStream(text),
					signed);

		ExternalTool.Result result = gpg(signed.toByteArray(), "--verify");

		assertGoodSignature(result);
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weVerifyWhatGnuPgSigns(String name) {
		PgpPublicKeys keys = key(name).publicKeys();
		VerifyRequest request = VerifyRequest.builder().keys(keys).build();
		for (String armor : new String[] { "--armor", "--no-armor" }) {
			ExternalTool.Result embedded = gpgWithPassphrase(DATA, armor, "--local-user", email(name), "--sign");
			assertThat(embedded.exitCode()).as(embedded.errors()).isZero();
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			VerifyResult result = PgpVerifier.create()
				.verify(request, new ByteArrayInputStream(embedded.output()), content);
			assertThat(result.allValid()).isTrue();
			assertThat(content.toByteArray()).isEqualTo(DATA);

			ExternalTool.Result detached = gpgWithPassphrase(DATA, armor, "--local-user", email(name), "--detach-sign");
			assertThat(detached.exitCode()).as(detached.errors()).isZero();
			assertThat(PgpVerifier.create()
				.verifyDetached(request, new ByteArrayInputStream(DATA), new ByteArrayInputStream(detached.output()))
				.allValid()).isTrue();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weVerifyACleartextSignatureGnuPgMakes(String name) {
		byte[] text = "A line\n- a line with a dash\nA line with trailing spaces   \nThe last line\n"
			.getBytes(StandardCharsets.UTF_8);
		ExternalTool.Result signed = gpgWithPassphrase(text, "--local-user", email(name), "--clearsign");
		assertThat(signed.exitCode()).as(signed.errors()).isZero();

		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = PgpVerifier.create()
			.verify(VerifyRequest.builder().keys(key(name).publicKeys()).build(),
					new ByteArrayInputStream(signed.output()), content);

		assertThat(result.allValid()).isTrue();
		assertThat(content.toString(StandardCharsets.UTF_8))
			.isEqualTo("A line\n- a line with a dash\nA line with trailing spaces   \nThe last line");
	}

	@Test
	void gnuPgDecryptsAndVerifiesWhatWeSignAndEncrypt() {
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.signAndEncrypt(SignRequest.builder().signer(secret("ec")).build(),
					EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build(),
					new ByteArrayInputStream(DATA), message);

		ExternalTool.Result result = gpgWithPassphrase(message.toByteArray(), "--decrypt");

		assertGoodSignature(result);
		assertThat(result.output()).isEqualTo(DATA);
	}

	@Test
	void weDecryptAndVerifyWhatGnuPgSignsAndEncrypts() {
		ExternalTool.Result message = gpgWithPassphrase(DATA, "--trust-model", "always", "--local-user", email("rsa"),
				"--recipient", email("ec"), "--sign", "--encrypt");
		assertThat(message.exitCode()).as(message.errors()).isZero();

		ByteArrayOutputStream content = new ByteArrayOutputStream();
		VerifyResult result = PgpDecryptor.create()
			.decryptAndVerify(DecryptRequest.builder().keys(secret("ec")).build(),
					VerifyRequest.builder().keys(TestPgpKeys.RSA.publicKeys()).build(),
					new ByteArrayInputStream(message.output()), content);

		assertThat(result.allValid()).isTrue();
		assertThat(content.toByteArray()).isEqualTo(DATA);
	}

	@Test
	void gnuPgDecryptsAPassphraseMessageWeEncrypt() {
		ByteArrayOutputStream message = new ByteArrayOutputStream();
		PgpEncryptor.create()
			.encrypt(EncryptRequest.builder().passphrase("correct horse".toCharArray()).build(),
					new ByteArrayInputStream(DATA), message);

		ExternalTool.Result result = gpg(message.toByteArray(), "--pinentry-mode", "loopback", "--passphrase",
				"correct horse", "--decrypt");

		assertThat(result.exitCode()).as(result.errors()).isZero();
		assertThat(result.output()).isEqualTo(DATA);
	}

	@Test
	void weDecryptAPassphraseMessageGnuPgEncrypts() {
		ExternalTool.Result encrypted = gpg(DATA, "--pinentry-mode", "loopback", "--passphrase", "correct horse",
				"--symmetric");
		assertThat(encrypted.exitCode()).as(encrypted.errors()).isZero();

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		PgpDecryptor.create()
			.decrypt(DecryptRequest.builder().passphrase("correct horse".toCharArray()).build(),
					new ByteArrayInputStream(encrypted.output()), plain);

		assertThat(plain.toByteArray()).isEqualTo(DATA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "rsa", "ec" })
	void weDecryptAMessageGnuPgEncryptsToAHiddenRecipient(String name) {
		ExternalTool.Result encrypted = gpg(DATA, "--throw-keyids", "--trust-model", "always", "--recipient",
				email(name), "--encrypt");
		assertThat(encrypted.exitCode()).as(encrypted.errors()).isZero();

		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		PgpDecryptor.create()
			.decrypt(DecryptRequest.builder().keys(secret(name)).build(), new ByteArrayInputStream(encrypted.output()),
					plain);

		assertThat(plain.toByteArray()).isEqualTo(DATA);
	}

}
