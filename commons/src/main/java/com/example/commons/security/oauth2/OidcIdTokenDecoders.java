package com.example.commons.security.oauth2;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.Key;
import java.text.ParseException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.SignedJWT;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Builds the {@link JwtDecoder} for the ID token of one client registration: signature
 * verified against the provider's JWK Set, OpenID Connect claims validated, and, when an
 * {@link IdTokenDecryption} exists, encrypted ID tokens decrypted first.
 */
public final class OidcIdTokenDecoders {

	private OidcIdTokenDecoders() {
	}

	/**
	 * Creates the ID token decoder for a client registration.
	 * @param clientRegistration the client registration that receives the ID token
	 * @param decryption the ID token decryption, or {@code null} when ID tokens are not
	 * encrypted
	 * @return the decoder
	 */
	public static JwtDecoder create(ClientRegistration clientRegistration, IdTokenDecryption decryption) {
		return create(clientRegistration, decryption, null);
	}

	/**
	 * Creates the ID token decoder for a client registration, fetching the provider's JWK
	 * Set with the trust material of a bundle.
	 * @param clientRegistration the client registration that receives the ID token
	 * @param decryption the ID token decryption, or {@code null} when ID tokens are not
	 * encrypted
	 * @param sslBundle the bundle that validates the provider's TLS certificate, or
	 * {@code null} for the JVM's default trust
	 * @return the decoder
	 */
	public static JwtDecoder create(ClientRegistration clientRegistration, IdTokenDecryption decryption,
			SslBundle sslBundle) {
		JWKSource<SecurityContext> jwkSource = jwkSource(clientRegistration, sslBundle);
		DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
		jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
				(jwkSelector, context) -> jwkSource.get(jwkSelector, context)
					.stream()
					.filter(jwk -> KeyUse.SIGNATURE.equals(jwk.getKeyUse()))
					.toList()));
		if (decryption != null) {
			jwtProcessor.setJWEKeySelector(decryption.keySelector());
		}
		NimbusJwtDecoder jwtDecoder = new NimbusJwtDecoder(jwtProcessor);
		jwtDecoder.setJwtValidator(oidcIdTokenValidator(clientRegistration));
		if (decryption == null) {
			return jwtDecoder;
		}
		return token -> {
			boolean encrypted = isJwe(token);
			if (decryption.isRequired() && !encrypted) {
				throw new BadJwtException(
						"The ID token is not encrypted, but this client requires encrypted ID tokens");
			}
			Jwt jwt = jwtDecoder.decode(token);
			return encrypted ? withSignedTokenValue(jwt, token, decryption) : jwt;
		};
	}

	/**
	 * Gives an ID token that arrived encrypted the signed JWT nested in it as its token
	 * value, in place of the JWE. Spring Security keeps the token string it was given,
	 * but the token value is what the application sends the provider as the
	 * {@code id_token_hint} at logout, and a JWE is encrypted to the application's key,
	 * so the provider could not read it. OpenID Connect Core (section 3.1.2.1) has the
	 * client decrypt the ID token to use it as a hint. The token has already been
	 * decrypted and verified by then, so this only unwraps it again.
	 */
	private static Jwt withSignedTokenValue(Jwt jwt, String jwe, IdTokenDecryption decryption) {
		try {
			EncryptedJWT encryptedJwt = EncryptedJWT.parse(jwe);
			for (Key key : decryption.keySelector().selectJWEKeys(encryptedJwt.getHeader(), null)) {
				try {
					encryptedJwt
						.decrypt(new DefaultJWEDecrypterFactory().createJWEDecrypter(encryptedJwt.getHeader(), key));
				}
				catch (JOSEException ex) {
					continue;
				}
				SignedJWT signedJwt = encryptedJwt.getPayload().toSignedJWT();
				if (signedJwt != null) {
					return Jwt.withTokenValue(signedJwt.serialize())
						.headers(headers -> headers.putAll(signedJwt.getHeader().toJSONObject()))
						.claims(claims -> claims.putAll(jwt.getClaims()))
						.build();
				}
			}
		}
		catch (ParseException | KeySourceException ex) {
			throw new BadJwtException("The encrypted ID token could not be unwrapped", ex);
		}
		throw new BadJwtException("The encrypted ID token does not contain a signed JWT");
	}

	/**
	 * Gets the OpenID Connect validator for an ID token.
	 * @param clientRegistration the client registration that received the ID token
	 * @return the validator for required OpenID Connect ID token claims
	 */
	public static OAuth2TokenValidator<Jwt> oidcIdTokenValidator(ClientRegistration clientRegistration) {
		return new OidcIdTokenValidator(clientRegistration);
	}

	/**
	 * Returns whether a token is in JWE compact serialization, which has five parts where
	 * a JWS has three.
	 * @param token the token
	 * @return whether the token is a JWE
	 */
	private static boolean isJwe(String token) {
		return token.chars().filter(character -> character == '.').count() == 4;
	}

	private static JWKSource<SecurityContext> jwkSource(ClientRegistration clientRegistration, SslBundle sslBundle) {
		String jwkSetUri = clientRegistration.getProviderDetails().getJwkSetUri();
		try {
			URL url = URI.create(jwkSetUri).toURL();
			JWKSourceBuilder<SecurityContext> builder = (sslBundle == null) ? JWKSourceBuilder.create(url)
					: JWKSourceBuilder.create(url,
							new DefaultResourceRetriever(JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT,
									JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT,
									JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT, true,
									sslBundle.createSslContext().getSocketFactory()));
			return builder.retrying(true).build();
		}
		catch (MalformedURLException | IllegalArgumentException ex) {
			throw new IllegalArgumentException("Invalid JWK Set URI for client registration "
					+ clientRegistration.getRegistrationId() + ": " + jwkSetUri, ex);
		}
	}

}
