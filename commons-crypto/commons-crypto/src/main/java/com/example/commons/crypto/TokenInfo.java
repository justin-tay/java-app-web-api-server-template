package com.example.commons.crypto;

import java.nio.file.Path;

/**
 * Identifies the token a PIN is asked for.
 *
 * @param name the name the library gave this token connection
 * @param library the PKCS#11 module the token is reached through
 */
public record TokenInfo(String name, Path library) {
}
