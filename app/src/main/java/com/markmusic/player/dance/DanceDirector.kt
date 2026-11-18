package com.markmusic.player.dance

enum class DanceMode { IDLE, DANCING, TRANSITION }
data class DanceClip(
    val id: String,
    val style: String,
    val beats: Int,
    val startPose: String,
    val endPose: String,
    val energy: Float,
    val neutral: Boolean = false
) {
    init {
        require(id.isNotBlank() && style.isNotBlank() && beats > 0)
        require(energy in 0f..1f)
    }
}
data class DanceDecision(val mode: DanceMode, val clipId: String?, val startBeat: Long)

/** Deterministic choreography. Plans on beat boundaries, independent of render FPS. */
class DanceDirector(
    private val clips: List<DanceClip>,
    private val neutralClipId: String
) {
    init {
        require(clips.map { it.id }.distinct().size == clips.size)
        require(clips.any { it.id == neutralClipId && it.neutral })
    }
    private val neutral = clips.first { it.id == neutralClipId }
    private var current: DanceClip? = null
    private var startBeat = 0L
    private var requestedStyle: String? = null
    private var pending: DanceClip? = null
    private var trackId = ""

    fun reset(mediaId: String) {
        trackId = mediaId
        current = null
        pending = null
        requestedStyle = null
        startBeat = 0L
    }

    fun requestStyle(style: String) { requestedStyle = style }

    /** Absolute beat index based on the externally measured song beat grid. */
    fun decide(mediaId: String, playing: Boolean, absoluteBeat: Long, energy: Float): DanceDecision {
        require(absoluteBeat >= 0)
        if (trackId.isEmpty()) trackId = mediaId
        else if (trackId != mediaId) reset(mediaId)
        if (!playing) {
            current = null
            pending = null
            return DanceDecision(DanceMode.IDLE, null, absoluteBeat)
        }
        val old = current
        if (old != null && absoluteBeat < startBeat) {
            current = null
            pending = null // seek backwards: no stale transition
        }
        val active = current
        if (active != null && absoluteBeat < startBeat + active.beats) {
            return DanceDecision(if (active.neutral) DanceMode.TRANSITION else DanceMode.DANCING,
                active.id, startBeat)
        }
        if (active?.neutral == true && pending != null) {
            current = pending
            pending = null
        } else {
            val targetStyle = requestedStyle ?: active?.style
            val candidates = clips.filter { !it.neutral &&
                (targetStyle == null || it.style == targetStyle) }
            val target = candidates.minWithOrNull(compareBy<DanceClip> {
                kotlin.math.abs(it.energy - energy.coerceIn(0f, 1f))
            }.thenBy { it.id })
            current = when {
                target == null -> neutral
                active != null && !active.neutral &&
                    (active.style != target.style || active.endPose != target.startPose) -> {
                    pending = target
                    neutral
                }
                else -> target
            }
        }
        startBeat = absoluteBeat
        val chosen = current!!
        return DanceDecision(if (chosen.neutral) DanceMode.TRANSITION else DanceMode.DANCING,
            chosen.id, startBeat)
    }
}
