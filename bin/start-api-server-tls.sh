#!/bin/bash

set -euo pipefail

project_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
certificate_directory="${project_directory}/.local/certs"

"${project_directory}/bin/generate-local-tls-certs.sh"

file_uri() {
	if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
		printf 'file:///%s' "$(cygpath -m "$1")"
	else
		printf 'file:%s' "$1"
	fi
}

export CERTIFICATE_PEM="$(file_uri "${certificate_directory}/localhost.pem")"
export PRIVATE_KEY_PEM="$(file_uri "${certificate_directory}/localhost-key.pem")"
export CA_BUNDLE_PEM="$(file_uri "${certificate_directory}/local-ca.pem")"
export SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_KEYCLOAK_CLIENT_ID="java-app-web-api-server-secure"
# This run uses the default (production-like) Spring profile so TLS stays enabled, so it
# supplies the development-only settings that the local profile would otherwise set:
# the development JWKS and the Liquibase context for the development seed users
# (see docs/adr/0018).
export COMMONS_SECURITY_OAUTH2_JWKS="$(file_uri "${project_directory}/app-web-api-server/src/test/resources/jwks.json")"
export SPRING_LIQUIBASE_CONTEXTS="dev"

cd "${project_directory}"

if [[ "$(uname -s)" == MINGW* || "$(uname -s)" == MSYS* ]]; then
	java_home=""
	if [[ -n "${JAVA_HOME:-}" ]]; then
		candidate_java_home="$(cygpath -u "${JAVA_HOME}")"
		if "${candidate_java_home}/bin/java.exe" -version >/dev/null 2>&1; then
			java_home="${candidate_java_home}"
		fi
	fi
	if [[ -z "${java_home}" ]]; then
		for candidate_java_home in /c/Program\ Files/Java/jdk-*; do
			if "${candidate_java_home}/bin/java.exe" -version >/dev/null 2>&1; then
				java_home="${candidate_java_home}"
				break
			fi
		done
	fi
	if [[ -z "${java_home}" ]]; then
		echo "Set JAVA_HOME to a JDK installation before running this script." >&2
		exit 1
	fi
	export JAVA_HOME="$(cygpath -m "${java_home}")"
	exec cmd.exe /c mvn.cmd -Plocal -pl app-web-api-server -am spring-boot:run "$@"
fi

exec mvn -Plocal -pl app-web-api-server -am spring-boot:run "$@"
