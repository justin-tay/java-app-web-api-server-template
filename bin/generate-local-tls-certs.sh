#!/bin/bash

set -euo pipefail

project_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
certificate_directory="${project_directory}/.local/certs"
openssl_configuration="${project_directory}/bin/localhost-tls.cnf"

if ! command -v openssl >/dev/null; then
	echo "OpenSSL is required to generate local TLS material." >&2
	exit 1
fi

if [[ "${1:-}" == "--force" ]]; then
	rm -f "${certificate_directory}/local-ca-key.pem" "${certificate_directory}/local-ca.pem" \
		"${certificate_directory}/local-ca.srl" "${certificate_directory}/localhost-key.pem" \
		"${certificate_directory}/localhost.csr" "${certificate_directory}/localhost.pem"
elif [[ -f "${certificate_directory}/localhost.pem" && -f "${certificate_directory}/localhost-key.pem" ]]; then
	echo "Local TLS material already exists in ${certificate_directory}. Use --force to replace it."
	exit 0
fi

mkdir -p "${certificate_directory}"

openssl genrsa -out "${certificate_directory}/local-ca-key.pem" 4096
openssl req -x509 -new -sha256 -days 3650 -key "${certificate_directory}/local-ca-key.pem" \
	-out "${certificate_directory}/local-ca.pem" -config "${openssl_configuration}" -extensions v3_ca \
	-subj "/CN=java-app-web-api-server local development CA"

openssl genrsa -out "${certificate_directory}/localhost-key.pem" 2048
openssl req -new -key "${certificate_directory}/localhost-key.pem" \
	-out "${certificate_directory}/localhost.csr" -config "${openssl_configuration}"
openssl x509 -req -sha256 -days 825 -in "${certificate_directory}/localhost.csr" \
	-CA "${certificate_directory}/local-ca.pem" -CAkey "${certificate_directory}/local-ca-key.pem" \
	-CAcreateserial -out "${certificate_directory}/localhost.pem" -extfile "${openssl_configuration}" \
	-extensions v3_leaf
rm "${certificate_directory}/localhost.csr"

echo "Generated localhost TLS material in ${certificate_directory}."
echo "Trust ${certificate_directory}/local-ca.pem in your operating system or browser before testing HTTPS."
