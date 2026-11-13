package com.markmusic.player.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

/**
 * Five user-facing EQ points, interpolated across the bands actually supported
 * by the handset's native equalizer. Values are in decibels, conservatively
 * clamped to the supported range to avoid clipping on Huawei audio hardware.
 */
data class MarkEqPreset(val title: String, val gains: List<Float>)

object MarkEqualizer {
    val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val presets = listOf(
        MarkEqPreset("Ровный", listOf(0f, 0f, 0f, 0f, 0f)),
        MarkEqPreset("Акустика", listOf(2f, 1f, 0f, 2f, 3f)),
        MarkEqPreset("Классика", listOf(1f, 2f, 0f, 1f, 2f)),
        MarkEqPreset("Танцевальная", listOf(4f, 2f, -1f, 2f, 4f)),
        MarkEqPreset("Электроника", listOf(4f, 1f, -1f, 2f, 3f)),
        MarkEqPreset("Хип-хоп", listOf(5f, 3f, -1f, 1f, 2f)),
        MarkEqPreset("Джаз", listOf(2f, 1f, 1f, 2f, 2f)),
        MarkEqPreset("Лаунж", listOf(2f, 1f, 0f, 0f, 2f)),
        MarkEqPreset("Поп", listOf(1f, 2f, 1f, 2f, 3f)),
        MarkEqPreset("R&B", listOf(4f, 2f, 0f, 2f, 2f)),
        MarkEqPreset("Рок", listOf(3f, 2f, -1f, 2f, 3f)),
        MarkEqPreset("Метал", listOf(3f, 1f, -2f, 3f, 3f)),
        MarkEqPreset("Регги", listOf(4f, 1f, 0f, 2f, 1f)),
        MarkEqPreset("Вокал", listOf(-1f, 0f, 2f, 4f, 2f)),
        MarkEqPreset("Басы", listOf(5f, 3f, 0f, -1f, 0f)),
        // Controlled sub-bass emphasis, not maximum gain: reduce low mid muddiness.
        MarkEqPreset("MARK Deep Bass", listOf(5f, 2f, -2f, 1f, 2f))
    )
    const val CUSTOM = "Пользовательский"
    private const val PREF = "mark_music"
    val state = MutableStateFlow(MarkEqPreset("Ровный", List(5) { 0f }))
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
            .getString("eq_custom", null) ?: return List(5) { 0f }
        return runCatching {
            val array = JSONArray(json)
            if (array.length() != 5) return@runCatching List(5) { 0f }
            List(5) { array.optDouble(it, 0.0).toFloat().coerceIn(-6f, 6f) }
        }.getOrDefault(List(5) { 0f })
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
                val anchor = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
                val values = preset.gains
                val upper = anchor.indexOfFirst { it >= hz }
                val gain = when {
                    upper <= 0 -> values.first()
                    upper >= anchor.size -> values.last()
                    else -> {
                        val lower = upper - 1
                        val ratio = ((hz - anchor[lower]) / (anchor[upper] - anchor[lower])).coerceIn(0f,1f)
                        values[lower] * (1f - ratio) + values[upper] * ratio
                    }
                }
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
