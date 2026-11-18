# MARK Audio 0.7.14 — visualizations, PCM source and real 3D

This inventory describes the implementation added to MARK Audio, **not** an art-approval claim. All 3D app assets are bundled offline; the existing Media3 ExoPlayer remains the only playback engine.

## Selectable player decks (swipe through 8 positions)

| Deck | Implementation | Animation / actual signal |
|---|---|---|
| VINYL | `VinylTurntable.kt` | Stylized physical vinyl, rotating record, tonearm, highlights; transport/progress driven |
| MAYAK_203 | `MayakReelToReel.kt` | Public-domain straight-on equipment photograph with precisely masked rotating reel hubs and tape-pack changes |
| VESNA_202_1 | `VesnaCassettePlayer.kt` | Photo-aligned rotating cassette hubs and stationary frame/paper labels |
| RADIO | `TubeRadioDeck.kt` | Tube-radio image with warm valve-lamp glow reacting to real playback PCM |
| MARK_3D | `MarkDancer3D.kt` | True native OpenGL ES 2.0 3D character, joint movement/lighting reacting to measured beat and stereo energy |
| ANALOG_STUDIO | `MarkVisualV2WebView.kt` | True multi-mesh Three.js/WebGL2 3D VU with two animated mechanical needles and six L/R LOW/MID/HIGH LED tracks |
| SPECTRUM_MATRIX | `MarkVisualV2WebView.kt` | True Three.js/WebGL2 3D frame, glass, six separate frequency LED columns, gain/peak effects |
| MARK_STEREO_COLLECTION | `MarkStereoCollection.kt` | Six hybrid CSS/2D image-based preview modes (listed below), driven by real ExoPlayer levels; not six fully editable WebGL3D meshes |

All eight are selected by horizontal swipes. The selected deck is saved.

## Six hybrid visual styles within MARK Stereo Collection

1. Analog Studio — photo-backed stereo VU style.
2. Spectrum Matrix — stereo LED spectrum panel.
3. Neon Towers — glowing tower array.
4. Hi-Fi Deck — stereo hi-fi machine.
5. Pulse Rings — ring visualization.
6. Oscilloscope — wave/scope style.

**Important:** the separate ANALOG_STUDIO and SPECTRUM_MATRIX deck entries above use true WebGL2 3D geometry; the six-mode collection is a hybrid prototype and must not be called six complete real-time 3D instruments. Rendering accuracy and Huawei performance require on-device testing.

## Visual styling beyond the decks

- `CosmicVoyageBackground.kt` — 3-layer slowly moving starfield/dust parallax and thematic gradients.
- `FloatingGlassMotion.kt` — device-sensor-based moving/tilting glass surfaces with configurable depth and sensitivity.
- `NeonDesign.kt` — glassmorphism, specular gradients, neon glow, highlights, animated pill/buttons, touch feedback.
- `MarkScreens.kt` — 25 palette/theme choices, animated directional deck swipes, overlay indicators and responsive controls.

## Live PCM architecture

`PlaybackService` (Media3 ExoPlayer) → `TeeAudioProcessor(MeterAudioBufferSink)` → `AudioMeterBus.levels` (RMS/peaks/beat) and `StereoSpectrumWorker` (non-blocking 2048 Hann FFT) → `AudioMeterBus.frequencyBands` → `MarkVisualV2Frame` (`mark.audio.stereo/1`) → offline Three.js WebView.

Source must be real decoded PCM, not a microphone, synthetic DEMO, a second player or external network. Native 3D preview hides the Studio file picker/editor and renders only the instrument. In standalone Studio HTML, the file picker remains present because it has no access to native audio.

## Test acceptance

- Gradle `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`; signed production APK created by existing `signed-release.yml` only after merge into `main`.
- Inspect built APK: embedded `mark_visual_engine_v2.html`, `mark_stereo_visual_collection.html`, correct package and signing certificate, no INTERNET/RECORD_AUDIO added.
- **Huawei Pura 80 Ultra / EMUI 15 remains an external hardware test:** playback, actual left/right signals, pause/seek and background, GPU/memory and thermal behavior; leave quality gates open until measured.
