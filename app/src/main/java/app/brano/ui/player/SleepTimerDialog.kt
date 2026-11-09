package app.brano.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.brano.R
import app.brano.playback.SleepTimerState

private val presets = listOf(15, 30, 45, 60)

/** Minutes from the custom field, or null when blank, not a number, or outside 1..600. */
internal fun parseCustomMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..600 }

@Composable
fun SleepTimerDialog(
    sleep: SleepTimerState,
    onStartMinutes: (Int) -> Unit,
    onEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val minutes = parseCustomMinutes(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_title)) },
        text = {
            Column {
                if (custom) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.sleep_custom_minutes)) },
                        singleLine = true,
                        isError = text.isNotEmpty() && minutes == null,
                        supportingText = if (text.isNotEmpty() && minutes == null) {
                            { Text(stringResource(R.string.sleep_custom_invalid)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else {
                    presets.forEach { m ->
                        Option(stringResource(R.string.sleep_minutes_option, m)) { onStartMinutes(m); onDismiss() }
                    }
                    Option(stringResource(R.string.sleep_custom)) { custom = true }
                    Option(stringResource(R.string.sleep_end_of_track)) { onEndOfTrack(); onDismiss() }
                    if (sleep != SleepTimerState.Off) {
                        Option(stringResource(R.string.sleep_cancel)) { onCancelTimer(); onDismiss() }
                    }
                }
            }
        },
        confirmButton = {
            if (custom) {
                TextButton(
                    onClick = { minutes?.let { onStartMinutes(it); onDismiss() } },
                    enabled = minutes != null,
                ) { Text(stringResource(R.string.sleep_start)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Option(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}
