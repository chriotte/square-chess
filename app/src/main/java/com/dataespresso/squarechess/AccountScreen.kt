package com.dataespresso.squarechess

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** The Settings row that opens the account screen. */
@Composable fun AccountSettingsRow(account: AccountUi, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sync_settings_row), fontSize = 16.sp)
            Text(if (account.signedIn) stringResource(R.string.sync_signed_in_as, account.username.orEmpty())
                else stringResource(R.string.sync_settings_row_help), fontSize = 12.sp, color = LocalPalette.current.muted)
        }
        Text("›", fontSize = 24.sp)
    }
}

/** What the account screen asks the activity to do with files; both run off the main thread. */
interface BackupFiles {
    /** Writes a backup to [uri]. */
    suspend fun save(uri: Uri)
    /** Restores [uri] and returns what was restored. */
    suspend fun restore(uri: Uri): Backup.Restored
}

@StringRes private fun AccountProblem.text(): Int = when (this) {
    AccountProblem.OFFLINE -> R.string.sync_problem_offline
    AccountProblem.SESSION_ENDED -> R.string.sync_problem_session
    AccountProblem.SIGN_IN_FAILED -> R.string.sync_problem_sign_in
    AccountProblem.SIGN_IN_CANCELLED -> R.string.sync_problem_cancelled
    AccountProblem.WRONG_PASSWORD -> R.string.sync_problem_password
    AccountProblem.TOO_MANY_ATTEMPTS -> R.string.sync_problem_attempts
    AccountProblem.SERVER -> R.string.sync_problem_server
    AccountProblem.NO_BROWSER -> R.string.sync_problem_browser
}

/**
 * Optional sign-in, sync and backups. Reached only from Settings, away from the Playroom.
 * Plain text and buttons, without progress animations, so it reads the same in e-ink mode.
 */
@Composable fun AccountScreen(account: AccountUi, sync: SyncManager, files: BackupFiles, onExit: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }.isSuccess
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = try { files.save(uri); context.getString(R.string.backup_saved) }
                catch (e: Exception) { context.getString(R.string.backup_failed, e.message ?: context.getString(R.string.io_error)) }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            backupMessage = try {
                val restored = files.restore(uri)
                context.getString(R.string.backup_restored, restored.games, restored.puzzles)
            } catch (e: Exception) { context.getString(R.string.backup_restore_failed, e.message ?: context.getString(R.string.io_error)) }
        }
    }
    // The account was deleted: say so once the confirmation closes.
    LaunchedEffect(account.deleted) { if (account.deleted) { confirmDelete = false; password = "" } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onExit) { Text("‹ " + stringResource(R.string.help_back)) }
        Text(stringResource(R.string.sync_title), fontFamily = FontFamily.Serif, fontSize = 30.sp, modifier = Modifier.semantics { heading() })
        val status = when {
            account.deleted -> stringResource(R.string.sync_deleted)
            account.problem != null -> stringResource(account.problem.text())
            account.status == SyncStatus.SYNCING -> stringResource(R.string.sync_running)
            else -> null
        }
        if (status != null) Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (account.signedIn) {
            Text(stringResource(R.string.sync_signed_in_as, account.username.orEmpty()), fontSize = 18.sp)
            Text(if (account.lastSync > 0) stringResource(R.string.sync_last,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(account.lastSync)))
                else stringResource(R.string.sync_never), color = LocalPalette.current.muted)
            AccountButton(R.string.sync_now, enabled = account.status != SyncStatus.SYNCING) { sync.syncNow() }
            AccountButton(R.string.sync_sign_out) { sync.signOut() }
            Text(stringResource(R.string.sync_sign_out_help), fontSize = 12.sp, color = LocalPalette.current.muted)
            HorizontalDivider()
            Text(stringResource(R.string.sync_delete), fontSize = 16.sp, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.sync_delete_help), fontSize = 12.sp, color = LocalPalette.current.muted)
            AccountButton(R.string.sync_delete_open) { sync.clearProblem(); password = ""; confirmDelete = true }
        } else {
            Text(stringResource(R.string.sync_intro))
            if (account.status == SyncStatus.SIGNING_IN) {
                Text(stringResource(R.string.sync_signing_in))
                AccountButton(R.string.cancel) { sync.clearProblem(); sync.signOut() }
            }
            AccountButton(R.string.sync_sign_in, primary = true) {
                val url = sync.startSignIn()
                if (!open(url)) sync.browserMissing()
            }
            Text(stringResource(R.string.sync_what_is_sent), fontSize = 12.sp, color = LocalPalette.current.muted)
        }
        HorizontalDivider()
        Text(stringResource(R.string.backup_title), fontSize = 16.sp, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.backup_help), fontSize = 12.sp, color = LocalPalette.current.muted)
        AccountButton(R.string.backup_save) { saver.launch(Backup.fileName()) }
        AccountButton(R.string.backup_restore) { opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    }

    if (confirmDelete && account.signedIn) AppAlertDialog(
        onDismissRequest = { if (!account.deleting) { confirmDelete = false; password = "" } },
        title = { Text(stringResource(R.string.sync_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sync_delete_text))
                OutlinedTextField(password, { password = it.take(200) }, label = { Text(stringResource(R.string.sync_delete_password)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !account.deleting,
                    modifier = Modifier.fillMaxWidth())
                when {
                    account.deleting -> Text(stringResource(R.string.sync_deleting))
                    account.problem != null -> Text(stringResource(account.problem.text()), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { open(SignInConfig.accountDeleteUrl) }) { Text(stringResource(R.string.sync_delete_web)) }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !account.deleting, onClick = { sync.deleteAccount(password) }) {
                Text(stringResource(R.string.sync_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(enabled = !account.deleting, onClick = { confirmDelete = false; password = ""; sync.clearProblem() }) {
                Text(stringResource(R.string.cancel))
            }
        })

    backupMessage?.let { message ->
        AppAlertDialog(onDismissRequest = { backupMessage = null }, title = { Text(stringResource(R.string.backup_title)) },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { backupMessage = null }) { Text(stringResource(R.string.ok)) } })
    }
}

@Composable private fun AccountButton(@StringRes label: Int, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    // In e-ink mode the theme draws both kinds in black and white.
    if (primary) Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(label)) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(stringResource(label)) }
}
