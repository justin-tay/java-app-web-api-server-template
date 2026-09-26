package com.example.app.web.server.api;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Account endpoint: the caller's account from the identity provider, limited to the
 * fields that describe it. Anything else Keycloak's account representation carries, such
 * as its attributes and user profile metadata, is never returned.
 */
@RestController
public class AccountController {

	/**
	 * The account fields returned to the caller.
	 *
	 * @param username the username
	 * @param firstName the first name
	 * @param lastName the last name
	 * @param email the email address
	 * @param emailVerified whether the email address is verified
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AccountResponse(String username, String firstName, String lastName, String email,
			Boolean emailVerified) {
	}

	private final RestClient restClient;

	public AccountController(RestClient restClient) {
		this.restClient = restClient;
	}

	@GetMapping(path = "/account", produces = MediaType.APPLICATION_JSON_VALUE)
	public AccountResponse account(
			@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
		String issuerUri = authorizedClient.getClientRegistration().getProviderDetails().getIssuerUri();
		String resourceUri = issuerUri + "/account/";
		return this.restClient.get()
			.uri(resourceUri)
			.accept(MediaType.APPLICATION_JSON)
			.attributes(clientRegistrationId(authorizedClient.getClientRegistration().getRegistrationId()))
			.retrieve()
			.body(AccountResponse.class);
	}

}