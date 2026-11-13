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
grep -Fq "package: name='com.markmusic.player' versionCode='12' versionName='0.6.4'" <<< "$badging" || {
  echo "ERROR: Wrong application ID or version in packaged APK" >&2
  exit 1
}
for required in READ_MEDIA_AUDIO FOREGROUND_SERVICE FOREGROUND_SERVICE_MEDIA_PLAYBACK; do
  grep -Fq "name='android.permission.$required'" <<< "$permissions" || {
    echo "ERROR: Missing expected audio playback permission: $required" >&2
    exit 1
  }
done
for forbidden in INTERNET RECORD_AUDIO CAMERA READ_CONTACTS READ_SMS SEND_SMS READ_CALL_LOG ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION QUERY_ALL_PACKAGES REQUEST_INSTALL_PACKAGES SYSTEM_ALERT_WINDOW BIND_ACCESSIBILITY_SERVICE MANAGE_EXTERNAL_STORAGE; do
  if grep -Fq "name='android.permission.$forbidden'" <<< "$permissions"; then
    echo "ERROR: Unexpected sensitive permission in final APK: $forbidden" >&2
    exit 1
  fi
done
echo "Packaged manifest policy verified: local music playback only."
printf '%s\n' "$permissions"
