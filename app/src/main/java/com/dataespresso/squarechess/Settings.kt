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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectable
import androidx.annotation.StringRes

enum class BoardTheme(@StringRes val label: Int, val light: Color, val dark: Color) {
    GREEN(R.string.theme_green, Color(0xFFF2E7CF), Color(0xFF526D62)),
    WALNUT(R.string.theme_walnut, Color(0xFFF0D9B5), Color(0xFFB58863)),
    SLATE(R.string.theme_slate, Color(0xFFDEE3E6), Color(0xFF788A94))
}

data class AppSettings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val coordinates: Boolean = true,
    val legalMoves: Boolean = true,
    val boardTheme: BoardTheme = BoardTheme.GREEN,
    val eink: Boolean = false
)

/** Small per-device preferences; saved games stay in Room. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun load() = AppSettings(
        sound = prefs.getBoolean("sound", true),
        haptics = prefs.getBoolean("haptics", true),
        coordinates = prefs.getBoolean("coordinates", true),
        legalMoves = prefs.getBoolean("legalMoves", true),
        boardTheme = BoardTheme.entries.firstOrNull { it.name == prefs.getString("boardTheme", null) } ?: BoardTheme.GREEN,
        eink = prefs.getBoolean("eink", isKnownEinkDevice())
    )
    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("sound", settings.sound)
            .putBoolean("haptics", settings.haptics)
            .putBoolean("coordinates", settings.coordinates)
            .putBoolean("legalMoves", settings.legalMoves)
            .putString("boardTheme", settings.boardTheme.name)
            .putBoolean("eink", settings.eink)
            .apply()
    }
}

@Composable fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onExit: () -> Unit,
                               language: AppLanguage? = null, onLanguage: (String) -> Unit = {}) {
    var languageDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onExit) { Text(stringResource(R.string.home_back)) }
        Text(stringResource(R.string.settings_title), fontFamily = FontFamily.Serif, fontSize = 30.sp)
        Spacer(Modifier.height(12.dp))
        SettingSwitch(stringResource(R.string.move_sounds), stringResource(R.string.move_sounds_help), settings.sound) { onChange(settings.copy(sound = it)) }
        SettingSwitch(stringResource(R.string.vibration), stringResource(R.string.vibration_help), settings.haptics) { onChange(settings.copy(haptics = it)) }
        SettingSwitch(stringResource(R.string.legal_moves), stringResource(R.string.legal_moves_help), settings.legalMoves) { onChange(settings.copy(legalMoves = it)) }
        SettingSwitch(stringResource(R.string.coordinates), stringResource(R.string.coordinates_help), settings.coordinates) { onChange(settings.copy(coordinates = it)) }
        SettingSwitch(stringResource(R.string.eink_mode), stringResource(R.string.eink_mode_help), settings.eink) { onChange(settings.copy(eink = it)) }
        // Language names are written in their own language, so a player can always find theirs.
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { languageDialog = true }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.language), fontSize = 16.sp)
                Text(language?.name ?: stringResource(R.string.language_system), fontSize = 12.sp, color = LocalPalette.current.muted)
            }
            Text("›", fontSize = 24.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.board_colours), fontSize = 16.sp)
        if (settings.eink) Text(stringResource(R.string.eink_board_note),
            fontSize = 12.sp, color = LocalPalette.current.muted, modifier = Modifier.padding(vertical = 8.dp))
        else Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoardTheme.entries.forEach { theme ->
                val selected = settings.boardTheme == theme
                val label = stringResource(theme.label)
                val description = stringResource(R.string.board_theme_description, label) +
                    if (selected) stringResource(R.string.selected_suffix) else ""
                Column(
                    Modifier.clickable(role = Role.RadioButton) { onChange(settings.copy(boardTheme = theme)) }
                        .semantics { contentDescription = description }
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
                    Text(label, fontSize = 13.sp)
                }
            }
        }
    }
    if (languageDialog) AppAlertDialog(onDismissRequest = { languageDialog = false },
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = listOf(null to stringResource(R.string.language_system)) + APP_LANGUAGES.map { it to it.name }
                options.forEach { (option, name) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = option == language, role = Role.RadioButton) {
                                languageDialog = false
                                if (option != language) onLanguage(option?.tag.orEmpty())
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = LocalPalette.current.muted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
