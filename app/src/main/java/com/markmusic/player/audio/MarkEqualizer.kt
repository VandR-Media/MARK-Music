package com.markmusic.player.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/**
 * Sixteen user-facing EQ points, interpolated across the bands actually supported
 * by the handset's native equalizer. Values are in decibels, conservatively
 * clamped to the supported range to avoid clipping on Huawei audio hardware.
 */
data class MarkEqPreset(val title: String, val gains: List<Float>)

object MarkEqualizer {
    // ISO-like sixteen-band graphic equalizer, with logarithmically spaced controls.
    val centerHz = listOf(32f, 45f, 63f, 90f, 125f, 180f, 250f, 355f,
        500f, 710f, 1000f, 1400f, 2000f, 4000f, 8000f, 16000f)
    val frequencies = centerHz.map { hz ->
        if (hz >= 1000f) "${(hz / 1000f).let { "%.1f".format(java.util.Locale.US, it) }}k"
        else "${hz.toInt()}"
    }

    private val legacyHz = listOf(60f, 230f, 910f, 3600f, 14000f)

    // New presets are converted into real sixteen-point curves (not five sliders
    // replicated across the display). Device EQ bands receive interpolated gains.
    private fun fromFive(name: String, vararg gains: Float) =
        MarkEqPreset(name, centerHz.map { hz -> interpolate(legacyHz, gains.toList(), hz) })

    fun interpolate(frequencies: List<Float>, values: List<Float>, hz: Float): Float {
        if (frequencies.isEmpty() || values.size != frequencies.size) return 0f
        val next = frequencies.indexOfFirst { it >= hz }
        if (next <= 0) return values.first()
        if (next == -1) return values.last()
        val before = next - 1
        val low = kotlin.math.ln(frequencies[before].coerceAtLeast(1f))
        val high = kotlin.math.ln(frequencies[next].coerceAtLeast(1f))
        val portion = ((kotlin.math.ln(hz.coerceAtLeast(1f)) - low) /
            (high - low).coerceAtLeast(.0001f)).coerceIn(0f, 1f)
        return values[before] + (values[next] - values[before]) * portion
    }

    val presets = listOf(
        fromFive("Ровный", 0f, 0f, 0f, 0f, 0f),
        fromFive("Акустика", 2f, 1f, 0f, 2f, 3f),
        fromFive("Классика", 1f, 2f, 0f, 1f, 2f),
        fromFive("Танцевальная", 4f, 2f, -1f, 2f, 4f),
        fromFive("Электроника", 4f, 1f, -1f, 2f, 3f),
        fromFive("Хип-хоп", 5f, 3f, -1f, 1f, 2f),
        fromFive("Джаз", 2f, 1f, 1f, 2f, 2f),
        fromFive("Лаунж", 2f, 1f, 0f, 0f, 2f),
        fromFive("Поп", 1f, 2f, 1f, 2f, 3f),
        fromFive("R&B", 4f, 2f, 0f, 2f, 2f),
        fromFive("Рок", 3f, 2f, -1f, 2f, 3f),
        fromFive("Метал", 3f, 1f, -2f, 3f, 3f),
        fromFive("Регги", 4f, 1f, 0f, 2f, 1f),
        fromFive("Вокал", -1f, 0f, 2f, 4f, 2f),
        fromFive("Басы", 5f, 3f, 0f, -1f, 0f),
        fromFive("MARK Deep Bass", 5f, 2f, -2f, 1f, 2f)
    )
    const val CUSTOM = "Пользовательский"
    private const val PREF = "mark_music"
    val state = MutableStateFlow(presets.first())
    val enabled = MutableStateFlow(false)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        enabled.value = prefs.getBoolean("eq_enabled", false)
        val name = prefs.getString("eq_preset", "Ровный") ?: "Ровный"
        state.value = if (name == CUSTOM) MarkEqPreset(CUSTOM, custom(context))
            else presets.firstOrNull { it.title == name } ?: presets.first()
    }

    fun custom(context: Context): List<Float> {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("eq_custom", null) ?: return List(centerHz.size) { 0f }
        return runCatching {
            val array = JSONArray(json)
            when (array.length()) {
                centerHz.size -> List(centerHz.size) { array.optDouble(it, 0.0).toFloat().coerceIn(-6f, 6f) }
                5 -> centerHz.map { hz ->
                    interpolate(legacyHz, List(5) { array.optDouble(it, 0.0).toFloat() }, hz).coerceIn(-6f, 6f)
                }
                else -> List(centerHz.size) { 0f }
            }
        }.getOrDefault(List(centerHz.size) { 0f })
    }

    fun select(context: Context, preset: MarkEqPreset, saveCustom: Boolean = false) {
        val values = preset.gains.map { it.coerceIn(-6f, 6f) }
        val effective = preset.copy(gains = values)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString("eq_preset", preset.title).putBoolean("eq_enabled", true)
        if (saveCustom) editor.putString("eq_custom", JSONArray(values).toString())
        editor.apply()
        enabled.value = true
        state.value = effective
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean("eq_enabled", value).apply()
        enabled.value = value
    }
}

class MarkEqualizerEngine {
    private var effect: Equalizer? = null
    private var session = -1

    fun update(sessionId: Int, preset: MarkEqPreset, enabled: Boolean) {
        if (sessionId <= 0) return
        if (session != sessionId || effect == null) {
            release()
            session = sessionId
            effect = try { Equalizer(0, sessionId) } catch (e: Exception) {
                Log.w("MarkEqualizer", "Audio equalizer unsupported", e)
                null
            }
        }
        val eq = effect ?: return
        try {
            eq.enabled = false
            val range = eq.bandLevelRange
            val count = eq.numberOfBands.toInt()
            if (count <= 0) return
            for (index in 0 until count) {
                val hz = eq.getCenterFreq(index.toShort()) / 1000f
                val gain = MarkEqualizer.interpolate(MarkEqualizer.centerHz, preset.gains, hz)
                // Limiting to 6 dB avoids extreme distortion on small speakers.
                val millibels = (gain.coerceIn(-6f, 6f) * 100f).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                eq.setBandLevel(index.toShort(), millibels)
            }
            eq.enabled = enabled
        } catch (e: Exception) {
            Log.w("MarkEqualizer", "Unable to apply EQ settings", e)
            runCatching { eq.enabled = false }
        }
    }

    fun release() {
        runCatching { effect?.enabled = false }
        runCatching { effect?.release() }
        effect = null
        session = -1
    }
}
