#!/usr/bin/env bash
# MARK Audio: sign the CI-aligned release APK with the OWNER'S existing release keystore.
# Never commit the .jks or passwords to the repository or store them in CI logs.
set -euo pipefail
umask 077

if [[ $# -ne 2 ]]; then
  echo "Usage: MARK_AUDIO_KEYSTORE=<path> MARK_AUDIO_STORE_PASSWORD=<hidden> MARK_AUDIO_KEY_PASSWORD=<hidden> $0 <aligned-unsigned.apk> <signed.apk>" >&2
  exit 2
fi
: "${MARK_AUDIO_KEYSTORE:?Set MARK_AUDIO_KEYSTORE to the new MARK Audio release keystore path}"
: "${MARK_AUDIO_STORE_PASSWORD:?Set MARK_AUDIO_STORE_PASSWORD privately}"
: "${MARK_AUDIO_KEY_PASSWORD:?Set MARK_AUDIO_KEY_PASSWORD privately}"

unsigned="$1"
signed="$2"
test -s "$unsigned"
test -f "$MARK_AUDIO_KEYSTORE"

if [[ -n "${ANDROID_HOME:-}" ]] && [[ -x "$ANDROID_HOME/build-tools/36.0.0/apksigner" ]]; then
  apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
elif [[ -n "${MARK_AUDIO_APKSIGNER:-}" ]]; then
  apksigner="$MARK_AUDIO_APKSIGNER"
else
  echo "Android SDK Build Tools 36.0.0 are required. Set ANDROID_HOME or MARK_AUDIO_APKSIGNER." >&2
  exit 2
fi

# We require official apksigner on the already zipaligned unsigned CI artifact.
# v1 is enabled explicitly to make the certificate visible to JAR-based scanners.
"$apksigner" sign \
  --ks "$MARK_AUDIO_KEYSTORE" \
  --ks-key-alias mark_audio_release \
  --ks-pass env:MARK_AUDIO_STORE_PASSWORD \
  --key-pass env:MARK_AUDIO_KEY_PASSWORD \
  --v1-signer-name CERT \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --min-sdk-version 26 \
  --out "$signed" "$unsigned"

# API 23 forces JAR v1 verification; API 26+ normally chooses v2/v3 and
# reports v1 as "false" even when the v1 signature is present and valid.
report="$("$apksigner" verify --min-sdk-version 23 --verbose --print-certs "$signed")"
"$apksigner" verify --min-sdk-version 26 "$signed"
printf '%s\n' "$report" | grep -Fq 'Verifies'
for scheme in v1 v2 v3; do
  printf '%s\n' "$report" | grep -Eq "Verified using ${scheme} scheme .*: true$" || {
    echo "ERROR: ${scheme} signature is not verified" >&2
    exit 1
  }
done
# Do not accidentally ship an update signed with a new or incorrect key.
fingerprint='f02b5efb88a28dddccca3148bc88b472365e76bb345a8a750b09482cd686858d'
printf '%s\n' "$report" | grep -Fqi "Signer #1 certificate SHA-256 digest: $fingerprint" || {
  echo 'ERROR: signing certificate does not match new MARK Audio release key' >&2
  exit 1
}

echo 'MARK Audio release signed and verified (v1/v2/v3).'
printf '%s\n' "$report" | grep -E 'Verifies|Verified using v[123] scheme|Signer #1 certificate SHA-256 digest'
sha256sum "$signed"
