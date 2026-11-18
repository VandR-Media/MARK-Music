package com.markmusic.player.visual.dance

import kotlin.random.Random

/** These are choreographic families, NOT guaranteed audio genre predictions. */
internal enum class DanceStyle(val label: String) {
    HIP_HOP("Hip-hop Groove"), OLD_SCHOOL("Old School"), POPPING("Popping"),
    LOCKING("Locking"), BREAKING("Breaking Footwork"), HOUSE("House"),
    SHUFFLE("Shuffle"), ELECTRO("Electro"), WAACKING("Waacking"),
    TUTTING("Tutting"), DANCEHALL("Dancehall"), AFRO("Afro Dance"),
    AMAPIANO("Amapiano"), REGGAETON("Reggaeton"), SALSA("Salsa"),
    BACHATA("Bachata"), DISCO("Disco"), FUNK("Funk"),
    JAZZ_SWING("Jazz Swing"), K_POP("K-pop")
}

internal data class DanceMusicFeatures(
    val bpm: Float?, val beatConfidence: Float,
    val energy: Float, val genreTag: String? = null
)

/** Tag first, then choose a TEMPO-SUITABLE choreography, not an alleged genre. */
internal fun chooseDanceStyle(input: DanceMusicFeatures): DanceStyle {
    val genres = mapOf(
        "amapiano" to DanceStyle.AMAPIANO, "reggaeton" to DanceStyle.REGGAETON,
        "bachata" to DanceStyle.BACHATA, "dancehall" to DanceStyle.DANCEHALL,
        "waacking" to DanceStyle.WAACKING, "locking" to DanceStyle.LOCKING,
        "popping" to DanceStyle.POPPING, "tutting" to DanceStyle.TUTTING,
        "breaking" to DanceStyle.BREAKING, "breakdance" to DanceStyle.BREAKING,
        "k-pop" to DanceStyle.K_POP, "kpop" to DanceStyle.K_POP,
        "shuffle" to DanceStyle.SHUFFLE, "electro" to DanceStyle.ELECTRO,
        "house" to DanceStyle.HOUSE, "hip-hop" to DanceStyle.HIP_HOP,
        "hip hop" to DanceStyle.HIP_HOP, "old school" to DanceStyle.OLD_SCHOOL,
        "afro" to DanceStyle.AFRO, "salsa" to DanceStyle.SALSA,
        "swing" to DanceStyle.JAZZ_SWING, "jazz" to DanceStyle.JAZZ_SWING,
        "disco" to DanceStyle.DISCO, "funk" to DanceStyle.FUNK
    )
    val tag = input.genreTag?.trim()?.lowercase().orEmpty()
    genres.entries.firstOrNull { tag.contains(it.key) }?.let { return it.value }
    val bpm = input.bpm?.takeIf { it.isFinite() && it in 45f..240f }
    if (bpm == null || input.beatConfidence < .35f) return DanceStyle.HIP_HOP
    val energy = input.energy.coerceIn(0f, 1f)
    return when {
        bpm < 88f -> DanceStyle.HIP_HOP
        bpm < 112f && energy < .45f -> DanceStyle.OLD_SCHOOL
        bpm < 112f -> DanceStyle.FUNK
        bpm < 130f && energy < .45f -> DanceStyle.DISCO
        bpm < 134f -> DanceStyle.HOUSE
        energy > .57f -> DanceStyle.SHUFFLE
        else -> DanceStyle.ELECTRO
    }
}

internal enum class DanceState {
    NPC_IDLE, ENTERING_DANCE, DANCING, NEUTRAL_BRIDGE, EXITING_DANCE
}

internal enum class NpcGesture {
    NONE, BLINK, LOOK_AROUND, HEAD_TILT, WAVE, THUMBS_UP,
    PEACE_SIGN, STRETCH, WEIGHT_SHIFT
}

internal data class DanceDirection(
    val state: DanceState,
    val style: DanceStyle,
    /** Target motion clip index within the style's eventual approved rig. */
    val clipIndex: Int,
    val danceWeight: Float,
    val neutralWeight: Float,
    val idleGesture: NpcGesture,
    val idleProgress: Float
)

/**
 * Pure Kotlin, render-independent orchestration:
 * - Beat-quantized clip changes and phrase-boundary style switches
 * - Short neutral bridge before changing choreographic families
 * - NPC idle scheduler with rare greeting gestures on pause
 *
 * No actual mesh, skinning or authored dance clips are provided by this class.
 * That content must be approved before replacing the 0.7.12 robot.
 */
internal class MarkDanceDirector(
    seed: Int = 2026,
    private val phraseBeats: Int = 16,
    private val clipsPerStyle: Int = 12
) {
    init {
        require(phraseBeats >= 4 && phraseBeats % 4 == 0)
        require(clipsPerStyle in 10..15)
    }

    private val random = Random(seed)
    private var mode = DanceState.NPC_IDLE
    private var style = DanceStyle.HIP_HOP
    private var wantedStyle = style
    private var clip = 0
    private var lastBeat = 0L
    private var sinceMs = 0L
    private var first = true
    private var wasPlaying = false
    private var gesture = NpcGesture.NONE
    private var gestureSinceMs = 0L
    private var nextGestureMs = 0L

    fun update(
        timeMs: Long, playing: Boolean, beatCount: Long, preferred: DanceStyle
    ): DanceDirection {
        val now = timeMs.coerceAtLeast(0)
        if (first) {
            first = false
            sinceMs = now
            nextGestureMs = now + 6000L
        }
        wantedStyle = preferred

        if (playing != wasPlaying) {
            wasPlaying = playing
            sinceMs = now
            mode = if (playing) DanceState.ENTERING_DANCE else DanceState.EXITING_DANCE
            if (playing) { style = preferred; clip = 0 }
            gesture = NpcGesture.NONE
        }

        val beatAdvanced = beatCount > lastBeat
        if (beatCount < lastBeat) clip = 0 // Audio processor reset / new track
        lastBeat = beatCount

        if (playing && mode == DanceState.DANCING && beatAdvanced) {
            if (wantedStyle != style && beatCount % phraseBeats == 0L) {
                mode = DanceState.NEUTRAL_BRIDGE
                sinceMs = now
            } else if (beatCount % 4L == 0L) {
                clip = (clip + 1) % clipsPerStyle
            }
        }

        val elapsed = (now - sinceMs).coerceAtLeast(0)
        when (mode) {
            DanceState.ENTERING_DANCE -> if (elapsed >= 360) mode = DanceState.DANCING
            DanceState.EXITING_DANCE -> if (elapsed >= 420) {
                mode = DanceState.NPC_IDLE
                sinceMs = now
                nextGestureMs = now + 6000
            }
            DanceState.NEUTRAL_BRIDGE -> if (elapsed >= 620) {
                style = wantedStyle
                clip = 0
                mode = DanceState.ENTERING_DANCE
                sinceMs = now
            }
            else -> Unit
        }

        if (mode == DanceState.NPC_IDLE) {
            if (gesture != NpcGesture.NONE && now - gestureSinceMs >= 1200L)
                gesture = NpcGesture.NONE
            if (gesture == NpcGesture.NONE && now >= nextGestureMs) {
                val options = NpcGesture.entries.drop(1)
                gesture = options[random.nextInt(options.size)]
                gestureSinceMs = now
                nextGestureMs = now + 12000 + random.nextInt(14001)
            }
        } else gesture = NpcGesture.NONE

        val weight = when (mode) {
            DanceState.DANCING -> 1f
            DanceState.ENTERING_DANCE -> easing(
                (now - sinceMs).toFloat() / 360f
            )
            DanceState.EXITING_DANCE -> 1f - easing(
                (now - sinceMs).toFloat() / 420f
            )
            else -> 0f
        }
        return DanceDirection(
            state = mode, style = style, clipIndex = clip,
            danceWeight = weight,
            neutralWeight = if (mode == DanceState.NEUTRAL_BRIDGE) 1f
                else if (mode == DanceState.ENTERING_DANCE ||
                    mode == DanceState.EXITING_DANCE) 1f - weight else 0f,
            idleGesture = gesture,
            idleProgress = if (gesture == NpcGesture.NONE) 0f else
                ((now - gestureSinceMs).toFloat() / 1200f).coerceIn(0f, 1f)
        )
    }

    private fun easing(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
