package com.wdtt.client.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.wdtt.client.SettingsStore
import com.wdtt.client.ui.components.verticalScrollEdgeFade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploySecretsDialog(
    settingsStore: SettingsStore,
    initialMainPass: String,
    initialAdminId: String,
    initialBotToken: String,
    initialSshPort: String,
    manualPortsEnabled: Boolean,
    initialServerDtlsPort: String,
    initialServerWgPort: String,
    sshKeyAuth: Boolean,
    initialSshPublicKey: String,
    initialSshPrivateKey: String,
    initialSshKeyPassphrase: String,
    onSaved: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var passInput by rememberSaveable { mutableStateOf(initialMainPass) }
    var adminIdInput by rememberSaveable { mutableStateOf(initialAdminId) }
    var botTokenInput by rememberSaveable { mutableStateOf(initialBotToken) }
    var sshPortInput by rememberSaveable { mutableStateOf(if (initialSshPort.isBlank()) "22" else initialSshPort) }
    var dtlsPortInput by rememberSaveable { mutableStateOf(initialServerDtlsPort.ifBlank { "56000" }) }
    var wgPortInput by rememberSaveable { mutableStateOf(initialServerWgPort.ifBlank { "56001" }) }
    var sshPublicKeyInput by remember { mutableStateOf(initialSshPublicKey) }
    var sshPrivateKeyInput by remember { mutableStateOf(initialSshPrivateKey) }
    var sshKeyPassphraseInput by remember { mutableStateOf(initialSshKeyPassphrase) }
    var sshKeyError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun normalizePort(value: String, fallback: String): String {
        return value.toIntOrNull()?.takeIf { it in 1..65535 }?.toString() ?: fallback
    }

    val isPasswordValid = passInput.isNotEmpty() && passInput.matches(Regex("^[a-zA-Z0-9_.!?:#/-]+$"))

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                    .verticalScrollEdgeFade(
                        canScrollBackward = scrollState.canScrollBackward,
                        canScrollForward = scrollState.maxValue > 0,
                        innerEdgeOffset = 6.dp
                    )
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Секреты Деплоя", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it.filter { c -> !c.isWhitespace() } },
                    label = { Text("Задайте пароль туннеля (любой)") },
                    placeholder = { Text("Придумайте надежный пароль") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving,
                    isError = passInput.isNotEmpty() && !isPasswordValid,
                    supportingText = if (passInput.isNotEmpty() && !isPasswordValid) {
                        { Text("Разрешены только буквы, цифры и символы: _ . ! ? : # - /", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                if (sshKeyAuth) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("SSH ключ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sshPublicKeyInput,
                        onValueChange = { sshPublicKeyInput = it },
                        label = { Text("Публичный ключ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sshPrivateKeyInput,
                        onValueChange = {
                            sshPrivateKeyInput = it
                            sshKeyError = null
                        },
                        label = { Text("Приватный ключ") },
                        minLines = 3,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving,
                        isError = sshKeyError != null
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sshKeyPassphraseInput,
                        onValueChange = {
                            sshKeyPassphraseInput = it
                            sshKeyError = null
                        },
                        label = { Text("Passphrase ключа (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving,
                        isError = sshKeyError != null,
                        supportingText = sshKeyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Телеграм бот для управления", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = adminIdInput,
                    onValueChange = { adminIdInput = it },
                    label = { Text("ID Админа (Опционально)") },
                    placeholder = { Text("ID из @getmyid_bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = { botTokenInput = it },
                    label = { Text("Токен Бота (Опционально)") },
                    placeholder = { Text("Токен от BotFather") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("SSH Порт", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sshPortInput,
                    onValueChange = { sshPortInput = it.filter(Char::isDigit).take(5) },
                    label = { Text("Порт для деплоя SSH") },
                    placeholder = { Text("22") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (manualPortsEnabled) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Порты сервера", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dtlsPortInput,
                        onValueChange = { dtlsPortInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт DTLS сервера") },
                        placeholder = { Text("56000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wgPortInput,
                        onValueChange = { wgPortInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Порт WireGuard сервера") },
                        placeholder = { Text("56001") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(22.dp))

                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val finalPort = normalizePort(sshPortInput, "22")
                        val finalDtls = normalizePort(dtlsPortInput, "56000")
                        val finalWg = normalizePort(wgPortInput, "56001")
                        val keyChanged = sshPrivateKeyInput != initialSshPrivateKey || sshKeyPassphraseInput != initialSshKeyPassphrase
                        scope.launch {
                            isSaving = true
                            sshKeyError = null
                            val keyError = if (sshKeyAuth && (sshPrivateKeyInput.isBlank() || keyChanged)) {
                                withContext(Dispatchers.Default) {
                                    validateSshPrivateKey(sshPrivateKeyInput, sshKeyPassphraseInput)
                                }
                            } else null
                            if (keyError != null) {
                                sshKeyError = keyError
                                isSaving = false
                                return@launch
                            }
                            settingsStore.saveDeploySecrets(passInput, adminIdInput, botTokenInput, finalPort)
                            if (sshKeyAuth) {
                                settingsStore.saveDeploySshKey(sshPublicKeyInput, sshPrivateKeyInput, sshKeyPassphraseInput)
                            }
                            settingsStore.savePorts(finalDtls.toInt(), finalWg.toInt(), settingsStore.listenPort.first())
                            isSaving = false
                            onSaved(finalDtls, finalWg)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isPasswordValid && !isSaving,
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Сохранить", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun validateSshPrivateKey(privateKey: String, passphrase: String): String? {
    if (privateKey.isBlank()) return "Добавьте приватный SSH-ключ."
    return try {
        val keyPair = KeyPair.load(JSch(), privateKey.toByteArray(), null)
        try {
            if (keyPair.isEncrypted) {
                if (passphrase.isBlank()) return "Этот ключ защищён passphrase. Укажите её для продолжения."
                if (!keyPair.decrypt(passphrase.toByteArray())) return "Не удалось открыть SSH-ключ: проверьте passphrase."
            }
        } finally {
            keyPair.dispose()
        }
        null
    } catch (_: Exception) {
        "Не удалось прочитать SSH-ключ: проверьте его формат."
    }
}
