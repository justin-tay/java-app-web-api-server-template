package com.example.commons.security.oauth2;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Provides {@link ProviderTrust}, which gives the calls to an OpenID Provider the trust
 * material of {@code commons.security.oauth2.client.provider.<id>.ssl-bundle} (see
 * docs/adr/0029), and the token client that uses it. A registration that needs the
 * {@code private_key_jwt} client assertion has its token client built by
 * {@link PrivateKeyJwtAutoConfiguration}, from the same {@link ProviderTrust}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ HttpSecurity.class, ClientRegistrationRepository.class })
@ConditionalOnBooleanProperty(name = "commons.security.enabled", matchIfMissing = true)
@EnableConfigurationProperties({ OAuth2ClientProviderProperties.class, OAuth2ClientProperties.class })
public class OAuth2ClientProviderTrustAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	ProviderTrust providerTrust(OAuth2ClientProperties clientProperties, OAuth2ClientProviderProperties properties,
			SslBundles sslBundles) {
		return new ProviderTrust(clientProperties, properties, sslBundles);
	}

	/**
	 * Spring Security uses a token client bean for the authorization code grant. A
	 * provider without a bundle keeps Spring Security's own client.
	 * @param providerTrust the trust of each provider
	 * @return the token client
	 */
	@Bean
	@ConditionalOnMissingBean
	OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeAccessTokenResponseClient(
			ProviderTrust providerTrust) {
		return providerTrust.accessTokenResponseClient(client -> {
		});
	}

}
