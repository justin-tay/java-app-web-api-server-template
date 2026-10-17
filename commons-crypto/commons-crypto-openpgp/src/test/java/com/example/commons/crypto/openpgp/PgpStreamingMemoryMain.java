package com.example.commons.crypto.openpgp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.example.commons.crypto.VerifyResult;

/**
 * Signs and encrypts, then decrypts and verifies, far more data than the heap can hold,
 * to show that nothing buffers the message. Run by {@link PgpStreamingMemoryTest} in a
 * JVM with a small heap; it exits with a non-zero status on any failure.
 */
public final class PgpStreamingMemoryMain {

	static final long SIZE = 192L * 1024 * 1024;

	private PgpStreamingMemoryMain() {
	}

	/** A repeating byte stream cut off at a length. */
	static InputStream generated(long length) {
		return new InputStream() {

			private long remaining = length;

			@Override
			public int read() {
				if (this.remaining == 0) {
					return -1;
				}
				this.remaining--;
				return (int) (this.remaining & 0x7f);
			}

			@Override
			public int read(byte[] b, int off, int len) {
				if (this.remaining == 0) {
					return -1;
				}
				int count = (int) Math.min(len, this.remaining);
				for (int i = 0; i < count; i++) {
					b[off + i] = (byte) (this.remaining-- & 0x7f);
				}
				return count;
			}

		};
	}

	/** Counts what is written and keeps none of it. */
	static final class Counter extends OutputStream {

		long count;

		@Override
		public void write(int b) {
			this.count++;
		}

		@Override
		public void write(byte[] b, int off, int len) {
			this.count += len;
		}

	}

	public static void main(String[] args) throws Exception {
		Path file = Files.createTempFile("pgp-streaming", ".pgp");
		try (PgpSecretKeys keys = TestPgpKeys.RSA.secretKeys(TestPgpKeys.PASSPHRASE)) {
			try (OutputStream out = Files.newOutputStream(file)) {
				PgpEncryptor.create()
					.signAndEncrypt(SignRequest.builder().signer(keys).build(),
							EncryptRequest.builder().recipients(TestPgpKeys.RSA.publicKeys()).build(), generated(SIZE),
							out);
			}
			Counter plain = new Counter();
			VerifyResult result;
			try (InputStream in = Files.newInputStream(file)) {
				result = PgpDecryptor.create()
					.decryptAndVerify(DecryptRequest.builder().keys(keys).build(),
							VerifyRequest.builder().keys(TestPgpKeys.RSA.publicKeys()).build(), in, plain);
			}
			if (plain.count != SIZE || !result.allValid()) {
				System.err.println("Unexpected result: " + plain.count + " bytes, valid " + result.allValid());
				System.exit(2);
			}
		}
		catch (IOException | RuntimeException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

}
