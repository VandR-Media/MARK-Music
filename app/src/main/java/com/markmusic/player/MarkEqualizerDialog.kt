package com.markmusic.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markmusic.player.audio.MarkEqPreset
import com.markmusic.player.audio.MarkEqualizer

@Composable
internal fun MarkEqualizerDialog(palette: MarkPalette, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by MarkEqualizer.state.collectAsState()
    val enabled by MarkEqualizer.enabled.collectAsState()
    var custom by remember { mutableStateOf(MarkEqualizer.custom(context)) }
    var editing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Эквалайзер MARK Audio", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Включить обработку звука", color = Color.White,
                        modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = {
                        MarkEqualizer.setEnabled(context, it)
                    })
                }
                Text("16 готовых настроек, включая MARK Deep Bass",
                    color = NeonSubtle)
                LazyColumn(modifier = Modifier.heightIn(max = 235.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(MarkEqualizer.presets) { preset ->
                        NeonGlass(palette, Modifier.fillMaxWidth(), radius = 13.dp,
                            onClick = {
                                MarkEqualizer.select(context, preset)
                                editing = false
                            }) {
                            Text(preset.title + if (current.title == preset.title) "  ✓" else "",
                                modifier = Modifier.padding(12.dp),
                                color = if (current.title == preset.title) palette.accent else Color.White)
                        }
                    }
                }
                HorizontalDivider(color = palette.secondary.copy(alpha = .45f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Свой пресет", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        custom = MarkEqualizer.custom(context)
                        editing = !editing
                    }) { Text(if (editing) "Скрыть" else "Настроить", color = palette.accent) }
                }
                if (editing) {
                    MarkEqualizer.frequencies.forEachIndexed { index, frequency ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(frequency, color = NeonSubtle, modifier = Modifier.width(66.dp))
                            Slider(
                                value = custom[index],
                                onValueChange = { value ->
                                    custom = custom.toMutableList().also { it[index] = value }
                                },
                                valueRange = -6f..6f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${custom[index].toInt()} dB", color = Color.White,
                                modifier = Modifier.width(44.dp))
                        }
                    }
                    NeonPill("Сохранить мой пресет", palette, onClick = {
                        MarkEqualizer.select(context, MarkEqPreset(MarkEqualizer.CUSTOM, custom),
                            saveCustom = true)
                        editing = false
                    })
                } else if (current.title == MarkEqualizer.CUSTOM) {
                    Text("Активен сохранённый пользовательский пресет",
                        color = palette.accent)
                }
                Text("Доступность и диапазоны зависят от аудиосистемы устройства. Для басов лучше использовать наушники.", color = NeonSubtle)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово", color = palette.accent) } },
        containerColor = Color(0xFF101D40),
        shape = RoundedCornerShape(26.dp)
    )
}
