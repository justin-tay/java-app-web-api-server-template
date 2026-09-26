package com.example.app.web.server.config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Rest client configuration.
 */
@Configuration
public class RestClientConfiguration {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	@Bean
	RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
		// Never follow a redirect: the calls go to the configured issuer only, and a
		// redirect elsewhere would carry the user's access token with it (ASVS V15.3.2).
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {

			@Override
			protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
				super.prepareConnection(connection, httpMethod);
				connection.setInstanceFollowRedirects(false);
			}

		};
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder()
			.requestFactory(requestFactory)
			.requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager))
			.build();
	}

}
