# MARK Audio security audit — 0.6.4

This audit concerns the **local Android player** in `VandR-Media/MARK-Music`. It does **not** certify the APK as malware-free or establish why HUAWEI AppGallery displayed a warning.

## Findings from project source and signed 0.6.3 APK

- Application ID: `com.markmusic.player`.
- No project source code for network traffic, arbitrary code download, SMS, contacts, microphone recording, camera, accessibility services, app installation or overlay windows was found.
- Media access is through Android `MediaStore`; playback is through Media3 `ExoPlayer`.
- The `MediaSessionService` is intentionally exported, as recommended by Android for interoperable background playback. Previously it returned its session to **every** requesting controller. Version 0.6.4 limits access to Media3 trusted controllers; system lock-screen/headset and Bluetooth behaviour still needs real-device testing.
- 0.6.3 signed APK contained an Android JAR (v1) signature entry; GitHub release job additionally verified v1/v2/v3 and the pinned signing certificate. This proves signature integrity, **not** acceptance by Huawei's own scanner.
- `scripts/audit-release-apk.sh` now checks the **packaged** permissions and application ID before a signed release is uploaded. It prevents a future dependency manifest merge from silently adding high-risk capabilities.
- CI uses a standard Google/Android build toolchain and a secret-protected keystore on the trusted main branch. Keystore and passwords must never be committed.

## Known limitations

Huawei AppGallery's warning contains only generic wording, without a detection ID, signature reason, offending permission, or file. The source audit cannot identify Huawei's private verdict or prove that a new build will change it.

To investigate a false positive, send Huawei support: the exact screenshot, package name, version, full SHA-256 of the APK, public signing certificate SHA-256, installation source, device model/EMUI version, and a reproducible summary. Do **not** disable or bypass system security checks to install the APK. If possible ask Huawei for the precise detection ID and evidence.

## Release verification

Review `.github/workflows/android.yml` for PR testing and `.github/workflows/signed-release.yml` for signed release checks. Check the final artifact in GitHub Actions. Do not claim device compatibility until tested on Huawei Pura 80 Ultra.
