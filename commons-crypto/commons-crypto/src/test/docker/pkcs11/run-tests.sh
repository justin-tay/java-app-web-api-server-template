#!/bin/sh
# Runs inside the container made from the Dockerfile in this directory, with the repository
# mounted at /repo and the Maven cache at /root/.m2. It sets up two software PKCS#11 tokens, runs the
# whole build against the first, which also turns on the interoperability tests that run gpg and
# openssl, and then runs the tests that use a token against the second. Each token holds an RSA key
# and an EC P-256 key with their certificates, and has the PIN 1234:
#
#   SoftHSM2: the aliases k (RSA) and ec
#   NSS softoken: the aliases rsa and ec, in an NSS database
#
# The test keys come from the shared test fixtures. The build happens in a copy so the mounted
# repository is left untouched.
set -e

# ---- SoftHSM2
export SOFTHSM2_CONF=/tmp/softhsm2.conf
mkdir -p /tmp/tokens
echo "directories.tokendir = /tmp/tokens" > "$SOFTHSM2_CONF"
SOFTHSM2_LIBRARY=/usr/lib/softhsm/libsofthsm2.so

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

# ---- NSS softoken, with the test keys of the modules imported from their PKCS#12 files
NSS_LIBRARY=/usr/lib/x86_64-linux-gnu/libsoftokn3.so
KEYS=/repo/commons-crypto/src/test-support/resources
rm -rf /tmp/nssdb
mkdir /tmp/nssdb
echo 1234 > /tmp/nss-password
certutil -N -d sql:/tmp/nssdb -f /tmp/nss-password
pk12util -i "$KEYS/rsa.p12" -d sql:/tmp/nssdb -k /tmp/nss-password -W changeit > /dev/null
pk12util -i "$KEYS/ec.p12" -d sql:/tmp/nssdb -k /tmp/nss-password -W changeit > /dev/null

# ---- build in a copy
rm -rf /tmp/build
mkdir /tmp/build
cd /repo
tar --exclude=./.git --exclude='*/target' --exclude=./.claude -cf - . | tar -xf - -C /tmp/build
cd /tmp/build

report() {
  grep -E "Tests run:|BUILD|ERROR|FAIL" "$1" || true
}

failures() {
  for report in $(grep -l -E "FAILURE|ERROR" $(find /tmp/build -path '*surefire-reports/*.txt') | head -3); do
    echo "== $report"
    sed -n '1,45p' "$report"
  done
}

status=0

echo "##### SoftHSM2: the whole build, with the interoperability tests"
export PKCS11_LIBRARY="$SOFTHSM2_LIBRARY"
export PKCS11_RSA_ALIAS=k
export PKCS11_EC_ALIAS=ec
export INTEROP_TOOLS=true
mvn -B -pl commons-crypto -amd verify "$@" > /tmp/mvn-softhsm.log 2>&1 || status=$?
report /tmp/mvn-softhsm.log
if [ "$status" -ne 0 ]; then failures; fi
cat /tmp/interop-findings.txt 2> /dev/null || true

echo "##### NSS softoken: the tests that use a token"
export PKCS11_LIBRARY="$NSS_LIBRARY"
export PKCS11_SLOT_LIST_INDEX=1
export PKCS11_EXTRA_SETTINGS="nssArgs=\"configdir='sql:/tmp/nssdb' certPrefix='' keyPrefix='' secmod='secmod.db' flags=readWrite\""
export PKCS11_RSA_ALIAS=rsa
export PKCS11_EC_ALIAS=ec
unset INTEROP_TOOLS
nss_status=0
mvn -B -pl commons-crypto -amd verify -Dtest='Pkcs11TokenIntegrationTest,CmsTokenIntegrationTest,PgpTokenIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false "$@" > /tmp/mvn-nss.log 2>&1 || nss_status=$?
report /tmp/mvn-nss.log
if [ "$nss_status" -ne 0 ]; then failures; status=$nss_status; fi

openssl version
gpg --version | head -1
nss_version=$(certutil --version 2> /dev/null | head -1)
echo "$nss_version"
exit $status
