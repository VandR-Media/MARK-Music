# MARK Audio 0.7.14 — visualizations, PCM source and real 3D

This inventory describes the implementation added to MARK Audio, **not** an art-approval claim. All 3D app assets are bundled offline; the existing Media3 ExoPlayer remains the only playback engine.

## Selectable player decks (7 positions; swipe or navigation buttons)

| Deck | Implementation | Animation / actual signal |
|---|---|---|
| VINYL | `VinylTurntable.kt` | Stylized physical vinyl, rotating record, tonearm, highlights; transport/progress driven |
| MAYAK_203 | `MayakReelToReel.kt` | Public-domain straight-on equipment photograph with precisely masked rotating reel hubs and tape-pack changes |
| VESNA_202_1 | `VesnaCassettePlayer.kt` | Photo-aligned rotating cassette hubs and stationary frame/paper labels |
| RADIO | `TubeRadioDeck.kt` | Tube-radio image with warm valve-lamp glow reacting to real playback PCM |
| ANALOG_STUDIO | `MarkNativeStereoInstrument.kt` | Native Android Canvas VU needles and six independently driven PCM/FFT LEDs; temporary Huawei-compatible replacement for WebGL 3D |
| SPECTRUM_MATRIX | `MarkNativeStereoInstrument.kt` | Native six-column FFT LED matrix; no WebView dependency |
| MARK_STEREO_COLLECTION | `MarkNativeStereoInstrument.kt` | Six native depth-styled Canvas modes, switched by tapping the instrument. Oscilloscope is explicitly a stereo level indicator until raw samples are available |

All seven can be selected using horizontal swipes or dedicated previous/next buttons (necessary when a WebView consumes touches). The selected deck is saved. Retired MARK_3D preferences migrate to VINYL.

## Six hybrid visual styles within MARK Stereo Collection

1. Analog Studio — photo-backed stereo VU style.
2. Spectrum Matrix — stereo LED spectrum panel.
3. Neon Towers — glowing tower array.
4. Hi-Fi Deck — stereo hi-fi machine.
5. Pulse Rings — ring visualization.
6. Oscilloscope — wave/scope style.

**Important:** the original WebGL2 source is retained but is not currently used by the Huawei player because all three WebView instrument screens appeared black on real hardware; the six-mode collection is a hybrid prototype and must not be called six complete real-time 3D instruments. Native Canvas visibility, audio reaction, and Huawei performance still require another on-device test.

## Visual styling beyond the decks

- `CosmicVoyageBackground.kt` — 3-layer slowly moving starfield/dust parallax and thematic gradients.
- `FloatingGlassMotion.kt` — device-sensor-based moving/tilting glass surfaces with configurable depth and sensitivity.
- `NeonDesign.kt` — glassmorphism, specular gradients, neon glow, highlights, animated pill/buttons, touch feedback.
- `MarkScreens.kt` — 25 palette/theme choices, animated directional deck swipes, WebView-safe previous/next buttons, overlay indicators and responsive controls.

## Live PCM architecture

`PlaybackService` (Media3 ExoPlayer) → `TeeAudioProcessor(MeterAudioBufferSink)` → `AudioMeterBus.levels` (RMS/peaks/beat) and `StereoSpectrumWorker` (non-blocking 2048 Hann FFT) → `AudioMeterBus.frequencyBands` → `MarkVisualV2Frame` (`mark.audio.stereo/1`) → offline Three.js WebView.

Source must be real decoded PCM, not a microphone, synthetic DEMO, a second player or external network. Native 3D preview hides the Studio file picker/editor and renders only the instrument. In standalone Studio HTML, the file picker remains present because it has no access to native audio.

## Test acceptance

- Gradle `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`; signed production APK created by existing `signed-release.yml` only after merge into `main`.
- Inspect built APK: embedded `mark_visual_engine_v2.html`, `mark_stereo_visual_collection.html`, correct package and signing certificate, no INTERNET/RECORD_AUDIO added.
- **Huawei Pura 80 Ultra / EMUI 15 remains an external hardware test:** playback, actual left/right signals, pause/seek and background, GPU/memory and thermal behavior; leave quality gates open until measured.

## 2026-11-18 Huawei hotfix

User-provided screenshots confirmed black WebView panes at positions 5–7. Replaced their UI with native Compose Canvas rendering while preserving the original offline WebGL/HTML files for future GPU diagnostics. Native drawings are depth-styled 2D, not genuine editable WebGL 3D. The six-mode collection cycles by tapping its face; the waveform mode uses real stereo level lines rather than a fabricated PCM waveform. No new permissions, separate player, microphone or network access.
