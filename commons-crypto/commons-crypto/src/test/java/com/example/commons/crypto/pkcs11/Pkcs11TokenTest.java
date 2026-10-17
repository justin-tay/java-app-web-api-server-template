package com.example.commons.crypto.pkcs11;

import java.nio.file.Path;

import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import com.example.commons.crypto.PinRequiredException;
import com.example.commons.crypto.PinSupplier;
import com.example.commons.crypto.TokenUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that need no token. The tests that use a token are in
 * {@link Pkcs11TokenIntegrationTest}.
 */
class Pkcs11TokenTest {

	@Test
	void aMissingLibraryIsATokenUnavailableException() {
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/does/not/exist/libpkcs11.so")).build();

		assertThatThrownBy(() -> Pkcs11Token.open(config, (PinSupplier) (token) -> "1234".toCharArray()))
			.isInstanceOf(TokenUnavailableException.class)
			.hasMessageNotContaining("1234");
	}

	@Test
	void thePinIsNotAskedForWhenTheLibraryCannotBeLoaded() {
		int[] calls = new int[1];
		Pkcs11Config config = Pkcs11Config.builder().library(Path.of("/does/not/exist/libpkcs11.so")).build();

		assertThatThrownBy(() -> Pkcs11Token.open(config, (token) -> {
			calls[0]++;
			return "1234".toCharArray();
		})).isInstanceOf(TokenUnavailableException.class);
		assertThat(calls[0]).isZero();
	}

	/**
	 * Neither SoftHSM2 nor NSS locks a token after wrong PINs, so the way a login failure
	 * is told apart is checked with the failures that SunPKCS11 raises.
	 */
	@Test
	void aWrongPinIsAPinProblem() {
		assertThat(Pkcs11Token.loginFailure(new FailedLoginException("CKR_PIN_INCORRECT")))
			.isInstanceOf(PinRequiredException.class)
			.hasMessageContaining("wrong");
	}

	@Test
	void aLockedTokenIsReportedAsLockedSoThatNobodyKeepsTrying() {
		LoginException locked = new LoginException("sun.security.pkcs11.wrapper.PKCS11Exception: CKR_PIN_LOCKED");

		assertThat(Pkcs11Token.loginFailure(new IllegalStateException("login", locked)))
			.isInstanceOf(PinRequiredException.class)
			.hasMessageContaining("locked")
			.hasMessageContaining("security officer");
	}

	@Test
	void aTokenThatIsGoneIsNotAPinProblem() {
		LoginException gone = new LoginException("CKR_DEVICE_REMOVED");

		assertThat(Pkcs11Token.loginFailure(gone)).isInstanceOf(TokenUnavailableException.class);
	}

}
