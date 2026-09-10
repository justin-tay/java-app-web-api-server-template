package com.example.app.web.server.config;

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
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder()
			.requestFactory(requestFactory)
			.requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager))
			.build();
	}

}
