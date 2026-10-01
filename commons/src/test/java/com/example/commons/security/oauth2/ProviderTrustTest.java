package com.example.commons.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Provider;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties.Registration;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ProviderTrustTest {

	private final SslBundle privateCa = SslBundle.of(SslStoreBundle.NONE);

	private final DefaultSslBundleRegistry sslBundles = new DefaultSslBundleRegistry("private-ca", this.privateCa);

	private final OAuth2ClientProperties clientProperties = new OAuth2ClientProperties();

	private final OAuth2ClientProviderProperties properties = new OAuth2ClientProviderProperties();

	ProviderTrustTest() {
		provider("keycloak", "https://keycloak.example.test/realms/test");
		provider("other", "https://other.example.test");
		registration("keycloak-login", "keycloak");
		registration("other", null);
	}

	@Test
	void findsTheBundleOfTheProviderOfARegistration() {
		bundle("keycloak", "private-ca");

		ProviderTrust trust = trust();

		assertThat(trust.forRegistration("keycloak-login")).containsSame(this.privateCa);
		assertThat(trust.forIssuerUri("https://keycloak.example.test/realms/test")).containsSame(this.privateCa);
	}

	@Test
	void leavesAProviderWithoutABundleOnTheJvmDefaultTrust() {
		bundle("keycloak", "private-ca");

		ProviderTrust trust = trust();

		assertThat(trust.forRegistration("other")).isEmpty();
		assertThat(trust.forIssuerUri("https://other.example.test")).isEmpty();
		assertThat(trust.forRegistration("unknown")).isEmpty();
	}

	@Test
	void failsStartupForAProviderThatIsNotConfigured() {
		bundle("keycloack", "private-ca");

		assertThatIllegalStateException().isThrownBy(this::trust).withMessageContaining("keycloack");
	}

	@Test
	void failsStartupForABundleThatDoesNotExist() {
		bundle("keycloak", "missing");

		assertThatExceptionOfType(NoSuchSslBundleException.class).isThrownBy(this::trust);
	}

	private ProviderTrust trust() {
		return new ProviderTrust(this.clientProperties, this.properties, this.sslBundles);
	}

	private void provider(String id, String issuerUri) {
		Provider provider = new Provider();
		provider.setIssuerUri(issuerUri);
		this.clientProperties.getProvider().put(id, provider);
	}

	private void registration(String id, String providerId) {
		Registration registration = new Registration();
		registration.setProvider(providerId);
		this.clientProperties.getRegistration().put(id, registration);
	}

	private void bundle(String providerId, String bundleName) {
		OAuth2ClientProviderProperties.Provider provider = new OAuth2ClientProviderProperties.Provider();
		provider.setSslBundle(bundleName);
		this.properties.getProvider().put(providerId, provider);
	}

}
