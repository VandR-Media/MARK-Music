# MARK Audio — current player visualization inventory

The Huawei player includes four original machine views and one additional user-approved stereo VU instrument:

1. VINYL — `VinylTurntable.kt`, rotating record and tonearm.
2. MAYAK_203 — `MayakReelToReel.kt`, animated reel-to-reel machine.
3. VESNA_202_1 — `VesnaCassettePlayer.kt`, animated cassette deck.
4. RADIO — `TubeRadioDeck.kt`, lamp glow driven by actual decoded PCM.
5. STEREO_VU — `MarkStereoVuMeter.kt`, vintage dual analogue needle instruments with MARK AUDIO branding, warm ivory scales, brass hardware and brushed-metal chassis. Each pointer responds independently to ExoPlayer decoded PCM L/R meter values, with ~170ms position smoothing; silence/pause returns both to rest. The stationary instrument is the bundled photo-derived WebP based on the user-approved artwork (R.drawable.mark_stereo_vu_photo); only two mechanical pointers are drawn over it by native Android Canvas (no embedded WebView or extra media player).

The rejected Analog Studio, Spectrum Matrix and MARK Stereo Collection variants have been removed from the player carousel, from Android source integrations, and from bundled HTML assets. The unsuccessful native 2D replacement has also been deleted. MARK 3D Dancer was removed earlier.

Legacy saved mode names `MARK_3D`, `ANALOG_STUDIO`, `SPECTRUM_MATRIX` and `MARK_STEREO_COLLECTION` migrate to VINYL without removing library data or changing playback settings. Swipe/arrow navigation cycles through exactly five views.

The existing Media3 ExoPlayer remains the only audio playback engine. PCM metering is retained for radio lamp response and the new stereo VU meters. The 25 color themes and general cosmic/gradient/glass UI effects are unchanged.

**Hardware verification is required:** this change is compiled and tested by CI, but actual player behavior should be checked on Huawei Pura 80 Ultra / EMUI 15.

## Stereo VU art direction

Inspired by the user's stereo vintage VU photograph and the approved MARK AUDIO branded concept: paired mechanical pointers, engraved scales, red overload arcs, ivory dial lighting, dark brushed-metal enclosure and a center plaque. The approved photographic cabinet and dials are preserved in a bitmap; the two original needles are retouched out and independently redrawn on top. The bitmap is offline and proportionally scaled without stretching. Only the two pointers animate. No synthetic demo response or waveform is generated. The frame is a designed three-dimensional-looking instrument, **not** a real 3D/OpenGL scene.
