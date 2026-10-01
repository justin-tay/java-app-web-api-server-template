package com.example.commons.security.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslStoreBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An HTTPS server on {@code localhost} whose certificate no one but its
 * {@link #trustingBundle() bundle} trusts, as an identity provider behind a private CA
 * is.
 */
final class TlsTestServer implements AutoCloseable {

	private static final char[] PASSWORD = "changeit".toCharArray();

	private final HttpsServer server;

	private final SslBundle trustingBundle;

	private TlsTestServer(HttpsServer server, SslBundle trustingBundle) {
		this.server = server;
		this.trustingBundle = trustingBundle;
	}

	static TlsTestServer start(Path directory) throws Exception {
		Path keystoreFile = directory.resolve("server.p12");
		generateKeyPair(keystoreFile);
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream in = Files.newInputStream(keystoreFile)) {
			keyStore.load(in, PASSWORD);
		}
		KeyStore trustStore = KeyStore.getInstance("PKCS12");
		trustStore.load(null, null);
		trustStore.setCertificateEntry("server", keyStore.getCertificate("server"));
		KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		keyManagers.init(keyStore, PASSWORD);
		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(keyManagers.getKeyManagers(), null, null);
		HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
		server.start();
		return new TlsTestServer(server, SslBundle.of(SslStoreBundle.of(null, null, trustStore)));
	}

	/**
	 * Gets the server's address, such as {@code https://localhost:8443}.
	 * @return the base URL
	 */
	String baseUrl() {
		return "https://localhost:" + this.server.getAddress().getPort();
	}

	SslBundle trustingBundle() {
		return this.trustingBundle;
	}

	void respondWithJson(String path, Supplier<String> body) {
		this.server.createContext(path, exchange -> {
			byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
	}

	@Override
	public void close() {
		this.server.stop(0);
	}

	private static void generateKeyPair(Path keystoreFile) throws IOException, InterruptedException {
		String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
		Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize",
				"2048", "-dname", "CN=localhost", "-ext", "san=dns:localhost,ip:127.0.0.1", "-validity", "2",
				"-storetype", "PKCS12", "-keystore", keystoreFile.toString(), "-storepass", new String(PASSWORD))
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as(output).isZero();
	}

}
