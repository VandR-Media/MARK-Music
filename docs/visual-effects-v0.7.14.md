# MARK Audio — current player visualization inventory

The Huawei player intentionally includes only the four original machine views:

1. VINYL — `VinylTurntable.kt`, rotating record and tonearm.
2. MAYAK_203 — `MayakReelToReel.kt`, animated reel-to-reel machine.
3. VESNA_202_1 — `VesnaCassettePlayer.kt`, animated cassette deck.
4. RADIO — `TubeRadioDeck.kt`, lamp glow driven by actual decoded PCM.

The rejected Analog Studio, Spectrum Matrix and MARK Stereo Collection variants have been removed from the player carousel, from Android source integrations, and from bundled HTML assets. The unsuccessful native 2D replacement has also been deleted. MARK 3D Dancer was removed earlier.

Legacy saved mode names `MARK_3D`, `ANALOG_STUDIO`, `SPECTRUM_MATRIX` and `MARK_STEREO_COLLECTION` migrate to VINYL without removing library data or changing playback settings. Swipe/arrow navigation cycles through exactly four views.

The existing Media3 ExoPlayer remains the only audio playback engine. PCM metering is retained for radio lamp response. The 25 color themes and general cosmic/gradient/glass UI effects are unchanged.

**Hardware verification is required:** this change is compiled and tested by CI, but actual player behavior should be checked on Huawei Pura 80 Ultra / EMUI 15.
