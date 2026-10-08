package com.lapel.app.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lapel.app.R

@Composable
fun SyncCard(viewModel: SyncViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var server by remember(status.serverUrl) { mutableStateOf(status.serverUrl) }
    var password by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
            if (!status.loggedIn) {
                Text(stringResource(R.string.sync_description), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(server, { server = it }, label = { Text(stringResource(R.string.sync_server)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it },
                    label = { Text(stringResource(R.string.sync_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { viewModel.connect(server, password) }, enabled = !ui.busy && server.isNotBlank() && password.isNotEmpty()) {
                    Text(stringResource(R.string.sync_connect))
                }
                if (status.lastError == "login") Text(stringResource(R.string.sync_login_expired), color = MaterialTheme.colorScheme.error)
            } else {
                val last = status.lastSyncAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: stringResource(R.string.sync_never)
                Text(stringResource(R.string.sync_connected, last), style = MaterialTheme.typography.bodyMedium)
                if (ui.pending > 0) Text(stringResource(R.string.sync_pending, ui.pending), style = MaterialTheme.typography.bodySmall)
                status.lastError?.takeIf { it != "login" }?.let {
                    Text(stringResource(R.string.sync_last_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = viewModel::syncNow, enabled = !ui.busy) { Text(stringResource(R.string.sync_now)) }
                    TextButton(onClick = viewModel::logout, enabled = !ui.busy) { Text(stringResource(R.string.sync_logout)) }
                }
            }
            if (ui.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.padding(4.dp))
                Text(stringResource(R.string.sync_working))
            }
            when (val m = ui.message) {
                SyncMessage.WrongPassword -> Text(stringResource(R.string.sync_wrong_password), color = MaterialTheme.colorScheme.error)
                is SyncMessage.Done -> Text(stringResource(R.string.sync_done, m.pushed, m.pulled), color = MaterialTheme.colorScheme.primary)
                is SyncMessage.Failed -> Text(stringResource(R.string.sync_failed, m.reason), color = MaterialTheme.colorScheme.error)
                null -> Unit
            }
        }
    }
}
