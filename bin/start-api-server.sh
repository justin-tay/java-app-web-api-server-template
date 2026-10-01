#!/bin/bash

set -euo pipefail

# Starts the API server over plain HTTP for local development: the local Maven profile
# (H2 database) and the local Spring profile, which disables TLS, uses the development JWKS,
# and applies the dev and demo Liquibase contexts (see docs/adr/0018). For TLS, use
# bin/start-api-server-tls.sh. Extra arguments go to spring-boot:run.

project_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

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
	exec cmd.exe /c mvn.cmd -Plocal -pl app-web-api-server -am spring-boot:run -Dspring-boot.run.profiles=local "$@"
fi

exec mvn -Plocal -pl app-web-api-server -am spring-boot:run -Dspring-boot.run.profiles=local "$@"
