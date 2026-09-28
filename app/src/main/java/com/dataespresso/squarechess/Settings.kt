package com.dataespresso.squarechess

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BoardTheme(val label: String, val light: Color, val dark: Color) {
    GREEN("Green", Color(0xFFF2E7CF), Color(0xFF526D62)),
    WALNUT("Walnut", Color(0xFFF0D9B5), Color(0xFFB58863)),
    SLATE("Slate", Color(0xFFDEE3E6), Color(0xFF788A94))
}

data class AppSettings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val coordinates: Boolean = true,
    val legalMoves: Boolean = true,
    val boardTheme: BoardTheme = BoardTheme.GREEN
)

/** Small per-device preferences; saved games stay in Room. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun load() = AppSettings(
        sound = prefs.getBoolean("sound", true),
        haptics = prefs.getBoolean("haptics", true),
        coordinates = prefs.getBoolean("coordinates", true),
        legalMoves = prefs.getBoolean("legalMoves", true),
        boardTheme = BoardTheme.entries.firstOrNull { it.name == prefs.getString("boardTheme", null) } ?: BoardTheme.GREEN
    )
    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("sound", settings.sound)
            .putBoolean("haptics", settings.haptics)
            .putBoolean("coordinates", settings.coordinates)
            .putBoolean("legalMoves", settings.legalMoves)
            .putString("boardTheme", settings.boardTheme.name)
            .apply()
    }
}

@Composable fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onExit) { Text("‹ Home") }
        Text("Settings", fontFamily = FontFamily.Serif, fontSize = 30.sp)
        Spacer(Modifier.height(12.dp))
        SettingSwitch("Move sounds", "Sound for moves, captures and check", settings.sound) { onChange(settings.copy(sound = it)) }
        SettingSwitch("Vibration", "Short vibration when a move is played", settings.haptics) { onChange(settings.copy(haptics = it)) }
        SettingSwitch("Show legal moves", "Dots and rings on the squares a selected piece can move to", settings.legalMoves) { onChange(settings.copy(legalMoves = it)) }
        SettingSwitch("Show coordinates", "Files and ranks on the board edge", settings.coordinates) { onChange(settings.copy(coordinates = it)) }
        Spacer(Modifier.height(12.dp))
        Text("Board colours", fontSize = 16.sp)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardTheme.entries.forEach { theme ->
                val selected = settings.boardTheme == theme
                Column(
                    Modifier.clickable(role = Role.RadioButton) { onChange(settings.copy(boardTheme = theme)) }
                        .semantics { contentDescription = "${theme.label} board${if (selected) ", selected" else ""}" }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.size(56.dp).background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)
                    ).padding(3.dp)) {
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f).fillMaxWidth().background(theme.light))
                            Box(Modifier.weight(1f).fillMaxWidth().background(theme.dark))
                        }
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f).fillMaxWidth().background(theme.dark))
                            Box(Modifier.weight(1f).fillMaxWidth().background(theme.light))
                        }
                    }
                    Text(theme.label, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFFAFBCB4))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
