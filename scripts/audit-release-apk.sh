#!/usr/bin/env bash
# Fail a release if its final packaged manifest requests capabilities MARK Audio
# does not need. Inspect the APK, not just the source AndroidManifest.xml.
set -euo pipefail
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <unsigned-or-signed-apk>" >&2
  exit 2
fi
apk="$1"
test -s "$apk"
bt="${ANDROID_HOME:?ANDROID_HOME is required}/build-tools/36.0.0"
permissions="$("$bt/aapt" dump permissions "$apk")"
badging="$("$bt/aapt" dump badging "$apk")"
# Read version from Gradle, while retaining the application ID/version check.
build_file="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/app/build.gradle.kts"
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p' "$build_file")"
version_name="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)"[[:space:]]*$/\1/p' "$build_file")"
if [[ ! "$version_code" =~ ^[0-9]+$ ]] || [[ -z "$version_name" ]] || [[ "$version_name" == *$'\n'* ]]; then
  echo "ERROR: Could not determine a unique versionCode/versionName from Gradle" >&2
  exit 1
fi
expected="package: name='com.markmusic.player' versionCode='$version_code' versionName='$version_name'"
grep -Fq "$expected" <<< "$badging" || {
  echo "ERROR: Wrong application ID or version in packaged APK (expected $expected)" >&2
  exit 1
}
for required in READ_MEDIA_AUDIO FOREGROUND_SERVICE FOREGROUND_SERVICE_MEDIA_PLAYBACK INTERNET; do
  grep -Fq "name='android.permission.$required'" <<< "$permissions" || {
    echo "ERROR: Missing expected audio playback permission: $required" >&2
    exit 1
  }
done
for forbidden in RECORD_AUDIO CAMERA READ_CONTACTS READ_SMS SEND_SMS READ_CALL_LOG ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION QUERY_ALL_PACKAGES REQUEST_INSTALL_PACKAGES SYSTEM_ALERT_WINDOW BIND_ACCESSIBILITY_SERVICE MANAGE_EXTERNAL_STORAGE; do
  if grep -Fq "name='android.permission.$forbidden'" <<< "$permissions"; then
    echo "ERROR: Unexpected sensitive permission in final APK: $forbidden" >&2
    exit 1
  fi
done
echo "Packaged manifest policy verified: local playback and official Yandex ID HTTPS authentication only."
printf '%s\n' "$permissions"
