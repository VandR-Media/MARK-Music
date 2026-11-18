# MARK Dance Engine foundation (experimental; not enabled in the released UI)

This branch implements Kotlin timeline logic, a lightweight onset-tempo estimator,
a deterministic dance selector with neutral transitions, a strict preliminary GLB
container validator, and a versioned manifest contract. **No approved MARK Cosmic
character GLB exists in this repository.** Do not ship the procedural dancer as the
approved character.

The existing `audio/AudioMeterBus.kt` reads decoded PCM using Media3's
`TeeAudioProcessor`. Its existing `PcmBeatDetector` returns impulse count
and strength, **not a timestamped beat grid**. It is deliberately unchanged.
BeatTracker requires media-timestamped onsets: do not supply wall-clock timestamps
from the PCM audio callback. Timestamping must be added outside the audio thread,
with source/seek reset and latency correction, before enabling live choreography.

DancePlaybackClock is updated by Media3ClockBridge on the player application thread,
and offers monotonic extrapolation for a read-only renderer. Refresh periodically
on that thread to bound drift, and update immediately on discontinuities, player
speed changes, transitions and pause/resume. Render callbacks must not access Player.

DanceDirector is an initial deterministic state machine. It operates on absolute
beat indices, not per-frame counters. Future revisions must add phrase alignment,
clip contact constraints, crossfades, energy hysteresis, and real asset-driven poses.
Pausing currently enters IDLE immediately in scheduling state; visible easing to
neutral and NPC behavior are future renderer responsibilities.

The manifest schema in `docs/dance_manifest.schema.json` is a proposed v1
contract for Studio exports. A GLB container passing GlbAssetValidator does NOT
prove that the glTF scene, PBR materials, bone weights or animations are valid.
A glTF parser/validator and actual playback/render roundtrip are required.

Run `gradle :app:testDebugUnitTest :app:assembleDebug`.
Do not merge or publish a user APK until model approval and device testing.
