package com.example.app.web.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;
import java.util.stream.Stream;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import com.example.app.web.server.Application;

/**
 * Tests that the application requires an explicitly configured private JWKS and ships
 * none of its own. See docs/adr/0018.
 */
class ApplicationPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
				ValidationAutoConfiguration.class))
		.withUserConfiguration(ApplicationProperties.class);

	@Test
	void startupFailsWithAClearMessageWhenTheJwksLocationIsNotSet() {
		this.contextRunner.run(context -> assertThat(context).hasFailed()
			.getFailure()
			.rootCause()
			.hasMessageContaining("app.jwks")
			.hasMessageContaining(ApplicationProperties.JWKS_REQUIRED_MESSAGE));
	}

	@Test
	void startupFailsWhenTheJwksLocationIsBlank() {
		this.contextRunner.withPropertyValues("app.jwks= ")
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.hasMessageContaining(ApplicationProperties.JWKS_REQUIRED_MESSAGE));
	}

	@Test
	void startsWhenTheJwksLocationIsSet() {
		this.contextRunner.withPropertyValues("app.jwks=file:/run/secrets/jwks.json")
			.run(context -> assertThat(context).hasNotFailed()
				.getBean(ApplicationProperties.class)
				.extracting(ApplicationProperties::getJwks)
				.isEqualTo("file:/run/secrets/jwks.json"));
	}

	/**
	 * Scans the compiled main output, which is what the jar packages, for any JWKS
	 * carrying a private key or any PEM private key.
	 */
	@Test
	void productionClasspathContainsNoPrivateKeyMaterial() throws IOException, URISyntaxException {
		Path mainOutput = Path.of(Application.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		assertThat(mainOutput).isDirectory();
		List<Path> files;
		try (Stream<Path> walk = Files.walk(mainOutput)) {
			files = walk.filter(Files::isRegularFile).filter(file -> !file.toString().endsWith(".class")).toList();
		}

		assertThat(files).isNotEmpty().allSatisfy(file -> {
			String content = Files.readString(file, StandardCharsets.ISO_8859_1);
			assertThat(content).as(file.toString()).doesNotContain("PRIVATE KEY");
			assertThat(containsPrivateJwk(content)).as(file.toString()).isFalse();
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
