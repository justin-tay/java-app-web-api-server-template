package com.example.commons.security.oauth2;

import java.util.Map;

/**
 * Fetches the discovery metadata of an OpenID Provider.
 */
@FunctionalInterface
public interface MetadataFetcher {

	/**
	 * Fetches the metadata.
	 * @param issuerUri the configured issuer URI
	 * @return the discovery metadata, whose {@code issuer} equals the issuer URI
	 * @throws DiscoveryException when it cannot be fetched or is not valid
	 */
	Map<String, Object> fetch(String issuerUri);

}
