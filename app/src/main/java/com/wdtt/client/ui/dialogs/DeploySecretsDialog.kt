package com.wdtt.client.ui.dialogs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.wdtt.client.SettingsStore
import com.wdtt.client.ui.AppSectionCard
import com.wdtt.client.ui.components.verticalScrollEdgeFade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploySecretsPage(
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
    onBack: () -> Unit
) {
    SecureScreenEffect()

    val scope = rememberCoroutineScope()
    var passInput by rememberSaveable { mutableStateOf(initialMainPass) }
    var adminIdInput by rememberSaveable { mutableStateOf(initialAdminId) }
    var botTokenInput by rememberSaveable { mutableStateOf(initialBotToken) }
    var sshPortInput by rememberSaveable { mutableStateOf(initialSshPort.ifBlank { "22" }) }
    var dtlsPortInput by rememberSaveable { mutableStateOf(initialServerDtlsPort.ifBlank { "56000" }) }
    var wgPortInput by rememberSaveable { mutableStateOf(initialServerWgPort.ifBlank { "56001" }) }
    var sshPublicKeyInput by remember { mutableStateOf(initialSshPublicKey) }
    var sshPrivateKeyInput by remember { mutableStateOf(initialSshPrivateKey) }
    var sshKeyPassphraseInput by remember { mutableStateOf(initialSshKeyPassphrase) }
    var sshKeyError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    fun normalizePort(value: String, fallback: String): String =
        value.toIntOrNull()?.takeIf { it in 1..65535 }?.toString() ?: fallback

    val isPasswordValid = passInput.isNotEmpty() &&
        passInput.matches(Regex("^[a-zA-Z0-9_.!?:#/-]+$"))

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppDialogBackButton(onClick = { if (!isSaving) onBack() })
            Text(
                "Секреты деплоя",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollEdgeFade(
                    canScrollBackward = scrollState.canScrollBackward,
                    canScrollForward = scrollState.canScrollForward,
                    innerEdgeOffset = 6.dp
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppSectionCard(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Пароль туннеля", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it.filter { c -> !c.isWhitespace() } },
                    label = { Text("Пароль туннеля") },
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
            }

            if (sshKeyAuth) {
                AppSectionCard(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("SSH ключ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
                    OutlinedTextField(
                        value = sshPublicKeyInput,
                        onValueChange = { sshPublicKeyInput = it },
                        label = { Text("Публичный ключ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isSaving
                    )
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
            }

            AppSectionCard(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Телеграм бот для управления", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                OutlinedTextField(
                    value = adminIdInput,
                    onValueChange = { adminIdInput = it },
                    label = { Text("ID администратора (опционально)") },
                    placeholder = { Text("ID из @getmyid_bot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = { botTokenInput = it },
                    label = { Text("Токен бота (опционально)") },
                    placeholder = { Text("Токен от BotFather") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving
                )
            }

            AppSectionCard(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("SSH порт", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
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
            }

            if (manualPortsEnabled) {
                AppSectionCard(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Порты сервера", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
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
            }

            Spacer(Modifier.height(4.dp))
        }

        Button(
            onClick = {
                val finalPort = normalizePort(sshPortInput, "22")
                val finalDtls = normalizePort(dtlsPortInput, "56000")
                val finalWg = normalizePort(wgPortInput, "56001")
                val keyChanged = sshPrivateKeyInput != initialSshPrivateKey ||
                    sshKeyPassphraseInput != initialSshKeyPassphrase
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
                    onBack()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = isPasswordValid && !isSaving,
            colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Сохранить", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SecureScreenEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
