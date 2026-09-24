package com.example.app.web.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.stream.Stream;

import com.example.commons.CommonsDefaultsEnvironmentPostProcessor;
import com.example.commons.accounts.AccountsAutoConfiguration;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;

/**
 * Tests that the application ships no private key material of its own. See docs/adr/0018.
 */
class NoPackagedPrivateKeyMaterialTest {

	/**
	 * Scans the compiled main output, which is what the jar packages, for any JWKS
	 * carrying a private key or any PEM private key. The executable jar also packages the
	 * {@code commons} and {@code commons-accounts} jars, so their main outputs are
	 * scanned too, whether the build resolves them as directories or as jars.
	 */
	@Test
	void productionClasspathContainsNoPrivateKeyMaterial() throws IOException, URISyntaxException {
		Path mainOutput = codeSource(Application.class);
		assertThat(mainOutput).isDirectory();
		assertContainsNoPrivateKeyMaterial(mainOutput);
		assertContainsNoPrivateKeyMaterial(codeSource(CommonsDefaultsEnvironmentPostProcessor.class));
		assertContainsNoPrivateKeyMaterial(codeSource(AccountsAutoConfiguration.class));
	}

	private static Path codeSource(Class<?> type) throws URISyntaxException {
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

	private static void assertContainsNoPrivateKeyMaterial(Path codeSource) throws IOException {
		if (Files.isDirectory(codeSource)) {
			assertTreeContainsNoPrivateKeyMaterial(codeSource, codeSource.toString());
			return;
		}
		assertThat(codeSource).isRegularFile();
		try (FileSystem jar = FileSystems.newFileSystem(codeSource)) {
			assertTreeContainsNoPrivateKeyMaterial(jar.getPath("/"), codeSource.toString());
		}
	}

	private static void assertTreeContainsNoPrivateKeyMaterial(Path root, String location) throws IOException {
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			files = walk.filter(Files::isRegularFile).filter(file -> !file.toString().endsWith(".class")).toList();
		}

		assertThat(files).as(location).isNotEmpty().allSatisfy(file -> {
			String description = location + "!" + file;
			String content = Files.readString(file, StandardCharsets.ISO_8859_1);
			assertThat(content).as(description).doesNotContain("PRIVATE KEY");
			assertThat(containsPrivateJwk(content)).as(description).isFalse();
		});
	}

	private static boolean containsPrivateJwk(String content) {
		try {
			return JWKSet.parse(content).getKeys().stream().anyMatch(JWK::isPrivate);
		}
		catch (ParseException ex) {
			return false;
		}
	}

}
