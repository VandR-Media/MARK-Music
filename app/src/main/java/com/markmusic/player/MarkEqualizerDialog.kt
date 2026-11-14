package com.markmusic.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.markmusic.player.audio.MarkEqPreset
import com.markmusic.player.audio.MarkEqualizer
import kotlin.math.roundToInt

/** Full-height, readable sixteen-band EQ with immediate preset previews.
 * The Android hardware effect might have fewer physical bands. MarkEqualizerEngine
 * interpolates these sixteen points onto the bands supported by the device.
 */
@Composable
internal fun MarkEqualizerDialog(palette: MarkPalette, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected by MarkEqualizer.state.collectAsState()
    val enabled by MarkEqualizer.enabled.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var manualChanged by remember { mutableStateOf(false) }
    var gains by remember { mutableStateOf(selected.gains) }
    LaunchedEffect(selected) {
        if (!manualChanged) gains = selected.gains
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xEF0E1B3F),
            shadowElevation = 18.dp
        ) {
            Column(Modifier.padding(horizontal = 17.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Эквалайзер", Modifier.weight(1f),
                        color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = onDismiss) { Text("Готово", color = palette.accent) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Обработка звука", Modifier.weight(1f), color = Color.White)
                    Switch(enabled, onCheckedChange = { MarkEqualizer.setEnabled(context, it) })
                }
                Box {
                    NeonGlass(palette, Modifier.fillMaxWidth(), radius = 16.dp,
                        onClick = { menuExpanded = true }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Активная настройка", color = NeonSubtle, fontSize = 11.sp)
                                Text(selected.title, color = palette.accent, fontWeight = FontWeight.Bold)
                            }
                            Text("▾", color = Color.White, fontSize = 22.sp)
                        }
                    }
                    DropdownMenu(expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF142448))) {
                        MarkEqualizer.presets.forEach { preset ->
                            DropdownMenuItem(text = {
                                Text(preset.title,
                                    color = if (preset.title == selected.title) palette.accent else Color.White)
                            }, onClick = {
                                manualChanged = false
                                gains = preset.gains
                                MarkEqualizer.select(context, preset)
                                menuExpanded = false
                            })
                        }
                        DropdownMenuItem(text = { Text("Мой сохранённый пресет", color = palette.accent) },
                            onClick = {
                                val custom = MarkEqualizer.custom(context)
                                manualChanged = false
                                gains = custom
                                MarkEqualizer.select(context, MarkEqPreset(MarkEqualizer.CUSTOM, custom))
                                menuExpanded = false
                            })
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("16 частотных полос", Modifier.weight(1f), color = Color.White,
                        fontWeight = FontWeight.Bold)
                    Text("−6…+6 dB", color = NeonSubtle, fontSize = 11.sp)
                }
                Text("Выбор пресета сразу обновляет все полосы и звук",
                    color = NeonSubtle, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    itemsIndexed(MarkEqualizer.centerHz) { index, _ ->
                        val value = gains.getOrElse(index) { 0f }
                        Row(Modifier.fillMaxWidth().height(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(MarkEqualizer.frequencies[index], Modifier.width(48.dp),
                                color = NeonSubtle, fontSize = 11.sp)
                            Slider(value = value,
                                onValueChange = { next ->
                                    val updated = gains.toMutableList()
                                    updated[index] = next
                                    gains = updated
                                    manualChanged = true
                                    MarkEqualizer.select(context,
                                        MarkEqPreset(MarkEqualizer.CUSTOM, updated))
                                },
                                valueRange = -6f..6f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = palette.accent,
                                    activeTrackColor = palette.accent,
                                    inactiveTrackColor = palette.secondary.copy(alpha = .25f)))
                            Text("${if (value > 0f) "+" else ""}${value.roundToInt()}",
                                Modifier.width(30.dp), color = Color.White,
                                fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                NeonPill("Сохранить мой пресет", palette, onClick = {
                    MarkEqualizer.select(context,
                        MarkEqPreset(MarkEqualizer.CUSTOM, gains), saveCustom = true)
                    manualChanged = false
                })
                Text("16 управляющих полос адаптируются к возможностям аудиочипа.",
                    color = NeonSubtle, fontSize = 10.sp)
            }
        }
    }
}
