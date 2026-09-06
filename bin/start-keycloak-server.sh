#!/usr/bin/env sh

set -eu

: "${KC_HOME:?KC_HOME must point to the Keycloak installation directory}"

exec "$KC_HOME/bin/kc.sh" start-dev \
  --spi-connections-http-client--default--disable-trust-manager=true \
  "$@"
