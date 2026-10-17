package com.example.commons.crypto.openpgp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Text as OpenPGP signs it in a cleartext signature: lines ending in CR LF, with trailing
 * spaces and tabs removed and no line ending after the last line.
 */
final class PgpText {

	private PgpText() {
	}

	/**
	 * Splits text into lines at LF or CR LF, ignoring one line ending at the very end.
	 */
	static List<String> lines(String text) {
		List<String> lines = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
		if (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()) {
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	/** The bytes that are signed for these lines. */
	static byte[] canonical(List<String> lines) {
		StringBuilder canonical = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				canonical.append("\r\n");
			}
			canonical.append(stripTrailingWhitespace(lines.get(i)));
		}
		return canonical.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String stripTrailingWhitespace(String line) {
		int end = line.length();
		while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
			end--;
		}
		return line.substring(0, end);
	}

}
