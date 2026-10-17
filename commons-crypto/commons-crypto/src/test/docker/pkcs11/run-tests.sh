#!/bin/sh
# Runs inside the container made from the Dockerfile in this directory, with the repository
# mounted at /repo and the Maven cache at /root/.m2. It sets up a SoftHSM2 token that holds an
# RSA key and its certificate under the alias k and an EC P-256 key and its certificate under
# the alias ec, with the PIN 1234, and turns on the interoperability tests that run gpg and
# openssl, then runs the tests that use
# it. The build happens in a copy so the mounted repository is left untouched.
set -e

export SOFTHSM2_CONF=/tmp/softhsm2.conf
mkdir -p /tmp/tokens
echo "directories.tokendir = /tmp/tokens" > "$SOFTHSM2_CONF"
export SOFTHSM2_LIBRARY=/usr/lib/softhsm/libsofthsm2.so
# Turns on the tests that run gpg and openssl.
export INTEROP_TOOLS=true

softhsm2-util --init-token --free --label test --pin 1234 --so-pin 5678 > /dev/null

cd /tmp
openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -subj "/CN=pkcs11 test" -days 3650 2> /dev/null
openssl pkcs8 -topk8 -nocrypt -in key.pem -outform DER -out key.der
openssl x509 -in cert.pem -outform DER -out cert.der
pkcs11-tool --module "$SOFTHSM2_LIBRARY" --login --pin 1234 --write-object key.der --type privkey --id 01 --label k > /dev/null
pkcs11-tool --module "$SOFTHSM2_LIBRARY" --login --pin 1234 --write-object cert.der --type cert --id 01 --label k > /dev/null

openssl ecparam -name prime256v1 -genkey -noout -out eckey.pem
openssl pkcs8 -topk8 -nocrypt -in eckey.pem -outform DER -out eckey.der
openssl req -x509 -new -key eckey.pem -subj "/CN=pkcs11 ec test" -days 3650 -out eccert.pem 2> /dev/null
openssl x509 -in eccert.pem -outform DER -out eccert.der
pkcs11-tool --module "$SOFTHSM2_LIBRARY" --login --pin 1234 --write-object eckey.der --type privkey --id 02 --label ec --usage-sign --usage-derive > /dev/null
pkcs11-tool --module "$SOFTHSM2_LIBRARY" --login --pin 1234 --write-object eccert.der --type cert --id 02 --label ec > /dev/null

rm -rf /tmp/build
mkdir /tmp/build
cd /repo
tar --exclude=./.git --exclude='*/target' --exclude=./.claude -cf - . | tar -xf - -C /tmp/build
cd /tmp/build
status=0
mvn -B -pl commons-crypto -amd verify "$@" > /tmp/mvn.log 2>&1 || status=$?
grep -E "Tests run:|BUILD|ERROR|FAIL" /tmp/mvn.log || true
# What the tests recorded about the installed tools, for example whether OpenSSL reads AuthEnvelopedData.
cat /tmp/interop-findings.txt 2> /dev/null || true
openssl version
gpg --version | head -1
if [ "$status" -ne 0 ]; then
  for report in $(grep -l -E "FAILURE|ERROR" $(find /tmp/build -path '*surefire-reports/*.txt') | head -3); do
    echo "== $report"
    sed -n '1,45p' "$report"
  done
fi
exit $status
