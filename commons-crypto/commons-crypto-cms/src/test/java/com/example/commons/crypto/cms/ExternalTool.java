package com.example.commons.crypto.cms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Runs a command line tool and collects what it writes. For the interoperability tests.
 */
final class ExternalTool {

	private ExternalTool() {
	}

	record Result(int exitCode, byte[] output, String errors) {

		String outputText() {
			return new String(this.output, StandardCharsets.UTF_8);
		}

	}

	static Result run(Path directory, byte[] input, String... command) {
		try {
			Process process = new ProcessBuilder(List.of(command)).directory(directory.toFile()).start();
			CompletableFuture<byte[]> output = read(process.getInputStream());
			CompletableFuture<byte[]> errors = read(process.getErrorStream());
			try (var stdin = process.getOutputStream()) {
				if (input != null) {
					stdin.write(input);
				}
			}
			int exitCode = process.waitFor();
			return new Result(exitCode, output.get(), new String(errors.get(), StandardCharsets.UTF_8));
		}
		catch (IOException | InterruptedException | java.util.concurrent.ExecutionException ex) {
			throw new IllegalStateException("Cannot run " + command[0], ex);
		}
	}

	private static CompletableFuture<byte[]> read(InputStream stream) {
		return CompletableFuture.supplyAsync(() -> {
			try (stream) {
				return stream.readAllBytes();
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		});
	}

}
