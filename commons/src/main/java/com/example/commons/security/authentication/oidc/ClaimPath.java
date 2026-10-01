package com.example.commons.security.authentication.oidc;

import java.util.Map;

/**
 * Resolves a claim named by a provider's {@code user-name-attribute}, which Spring
 * Security reads only as a top-level claim name. A claim whose name is the whole path is
 * used as it is, so a flat name such as {@code https://example.com/username} keeps
 * working; otherwise the path is split on {@code .} and followed through nested objects,
 * so {@code xyz.preferred_username} reads {@code preferred_username} within the
 * {@code xyz} claim.
 */
final class ClaimPath {

	private ClaimPath() {
	}

	/**
	 * Returns the string at the path, or {@code null} if there is none.
	 * @param claims the claims
	 * @param path the claim name, or dotted path to a nested claim
	 * @return the string at the path, or {@code null}
	 */
	static String resolve(Map<String, Object> claims, String path) {
		Object value = claims.get(path);
		if (value == null && path.indexOf('.') >= 0) {
			value = followDottedPath(claims, path);
		}
		return (value instanceof String string) ? string : null;
	}

	private static Object followDottedPath(Map<String, Object> claims, String path) {
		Object current = claims;
		for (String segment : path.split("\\.", -1)) {
			if (!(current instanceof Map<?, ?> map)) {
				return null;
			}
			current = map.get(segment);
		}
		return current;
	}

}
