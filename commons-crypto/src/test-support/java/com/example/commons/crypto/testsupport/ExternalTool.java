package com.example.commons.crypto.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Runs a command line tool and collects what it writes. For the interoperability tests.
 */
public final class ExternalTool {

	private ExternalTool() {
	}

	/**
	 * What a tool wrote and how it ended.
	 * @param exitCode the exit code
	 * @param output what it wrote to standard output
	 * @param errors what it wrote to standard error
	 */
	public record Result(int exitCode, byte[] output, String errors) {

		public String outputText() {
			return new String(this.output, StandardCharsets.UTF_8);
		}

	}

	public static Result run(Path directory, byte[] input, String... command) {
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
		catch (IOException | InterruptedException | ExecutionException ex) {
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
