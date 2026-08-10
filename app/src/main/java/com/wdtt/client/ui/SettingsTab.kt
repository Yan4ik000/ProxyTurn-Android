package com.wdtt.client.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.SettingsStore
import com.wdtt.client.R
import com.wdtt.client.TunnelManager
import com.wdtt.client.TunnelService
import com.wdtt.client.WDTTColors
import com.wdtt.client.ui.dialogs.AppDialogHeader
import com.wdtt.client.ui.dialogs.AppDialogSurface
import com.wdtt.client.ui.dialogs.HashesDialog
import com.wdtt.client.ui.dialogs.SecretsDialog
import com.wdtt.client.ui.components.verticalScrollEdgeFade
import com.wdtt.client.ui.utils.stripVkUrlStatic
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt

private const val WORKERS_PER_GROUP = 9

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    mainPageBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNestedPageChanged: (Int) -> Unit = {},
    mainPageOverlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(currentDensity.density, fontScale = 1f)
    ) {
        SettingsTabContent(
            context,
            scope,
            settingsStore,
            mainPageBottomPadding,
            onNestedPageChanged,
            mainPageOverlay
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabContent(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    settingsStore: SettingsStore,
    mainPageBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNestedPageChanged: (Int) -> Unit = {},
    mainPageOverlay: @Composable BoxScope.() -> Unit = {}
) {
    val savedConnectionPassword by settingsStore.connectionPassword.collectAsStateWithLifecycle(initialValue = "")
    val savedManualPortsEnabled by settingsStore.manualPortsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val savedServerDtlsPort by settingsStore.serverDtlsPort.collectAsStateWithLifecycle(initialValue = 56000)
    val savedServerWgPort by settingsStore.serverWgPort.collectAsStateWithLifecycle(initialValue = 56001)
    val savedListenPort by settingsStore.listenPort.collectAsStateWithLifecycle(initialValue = 9000)

    val activeProfile by settingsStore.activeProfile.collectAsStateWithLifecycle(initialValue = 0)
    val wdttLinkMode by settingsStore.wdttLinkMode.collectAsStateWithLifecycle(initialValue = false)
    val wdttLink by settingsStore.wdttLink.collectAsStateWithLifecycle(initialValue = "")

    val activeClientIds by settingsStore.activeClientIds.collectAsStateWithLifecycle(initialValue = "8202606,6287487")
    val clientIdCheckResultsJson by settingsStore.clientIdCheckResults.collectAsStateWithLifecycle(initialValue = "{}")
    val savedObfsMode by settingsStore.obfsMode.collectAsStateWithLifecycle(initialValue = "audio")

    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val tunnelConfig by TunnelManager.config.collectAsStateWithLifecycle()

    val cooldownActive by TunnelManager.cooldownActive.collectAsStateWithLifecycle()
    var wasRunning by remember { mutableStateOf(false) }

    LaunchedEffect(tunnelRunning) {
        if (wasRunning && !tunnelRunning) {
            TunnelManager.startCooldown(1500L)
        }
        wasRunning = tunnelRunning
    }

    var peerInput by rememberSaveable { mutableStateOf("") }
    var vkHash1 by rememberSaveable { mutableStateOf("") }
    var vkHash2 by rememberSaveable { mutableStateOf("") }
    var vkHash3 by rememberSaveable { mutableStateOf("") }
    var vkHash4 by rememberSaveable { mutableStateOf("") }
    var workersInput by rememberSaveable { mutableFloatStateOf(18f) }
    var powerDynamic by rememberSaveable { mutableStateOf(false) }
    var showHashesDialog by rememberSaveable { mutableStateOf(false) }
    var useVKCallsAuth by rememberSaveable { mutableStateOf(true) }
    var obfsMode by rememberSaveable { mutableStateOf("audio") }
    var autoCaptchaEnabled by rememberSaveable { mutableStateOf(true) }
    var useWVCaptcha by rememberSaveable { mutableStateOf(false) }
    var isManualMode by rememberSaveable { mutableStateOf(true) }
    var wbvManualMode by rememberSaveable { mutableStateOf(true) }
    var manualPortsEnabled by rememberSaveable { mutableStateOf(false) }
    var serverDtlsPortInput by rememberSaveable { mutableStateOf("56000") }
    var serverWgPortInput by rememberSaveable { mutableStateOf("56001") }

    val allHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4) { listOf(vkHash1, vkHash2, vkHash3, vkHash4) }
    val uniqueHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4) { allHashes.filter { it.isNotBlank() && it.length >= 16 }.distinct() }
    val parsedLinkHashes = remember(wdttLink) {
        if (wdttLink.trim().startsWith("wdtt://")) {
            val clean = wdttLink.trim().removePrefix("wdtt://")
            val parts = clean.split(":")
            if (parts.size >= 6) {
                parts[5].split(",").filter { stripVkUrlStatic(it).isNotBlank() }
            } else emptyList()
        } else emptyList()
    }
    val filledHashCount = remember(vkHash1, vkHash2, vkHash3, vkHash4, wdttLinkMode, parsedLinkHashes) { 
        if (wdttLinkMode) parsedLinkHashes.size else uniqueHashes.size 
    }
    val combinedHashes = remember(vkHash1, vkHash2, vkHash3, vkHash4) { uniqueHashes.joinToString(",") }
    val dynamicMaxWorkers = remember(filledHashCount) { (filledHashCount.coerceAtLeast(1) * 27).toFloat() }
    var portInput by rememberSaveable { mutableStateOf("9000") }
    var sniInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(dynamicMaxWorkers) {
        if (workersInput > dynamicMaxWorkers) {
            workersInput = dynamicMaxWorkers
        }
    }

    val currentWorkers = workersInput.coerceIn(WORKERS_PER_GROUP.toFloat(), dynamicMaxWorkers)

    val hashErrors = remember(vkHash1, vkHash2, vkHash3, vkHash4) {
        buildList {
            allHashes.forEachIndexed { i, h ->
                if (h.isNotBlank() && h.length < 16) add("Хеш ${i + 1} — короткий")
            }
            val filled = allHashes.filter { it.isNotBlank() && it.length >= 16 }
            if (filled.size != filled.distinct().size) add("Есть дубликаты хешей")
        }
    }
    val hasInputHashErrors = remember(vkHash1, vkHash2, vkHash3, vkHash4) { hashErrors.isNotEmpty() }

    var showSecretsDialog by rememberSaveable { mutableStateOf(false) }
    var exceptionsOpen by rememberSaveable { mutableStateOf(false) }
    var showClearProfileDialog by rememberSaveable { mutableStateOf(false) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var isCheckingClientIds by remember { mutableStateOf(false) }
    val clientIdCheckResults = remember(clientIdCheckResultsJson) {
        try {
            val json = org.json.JSONObject(clientIdCheckResultsJson)
            buildMap<String, Boolean> {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optBoolean(key, false))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
    var initialized by remember { mutableStateOf(false) }

    fun parseHashes(raw: String) {
        val parts = raw.split(Regex("[,\\s\\n]+")).map { stripVkUrlStatic(it) }.filter { it.isNotEmpty() }
        vkHash1 = parts.getOrElse(0) { "" }
        vkHash2 = parts.getOrElse(1) { "" }
        vkHash3 = parts.getOrElse(2) { "" }
        vkHash4 = parts.getOrElse(3) { "" }
    }

    fun normalizeHashes(vararg hashes: String): String {
        return hashes
            .map { stripVkUrlStatic(it) }
            .filter { it.isNotBlank() && it.length >= 16 }
            .distinct()
            .joinToString(",")
    }

    LaunchedEffect(activeProfile, profileRevision) {
        val peer = settingsStore.peer.first()
        val hashes = settingsStore.vkHashes.first()
        val workers = settingsStore.totalWorkers.first()
        val port = settingsStore.listenPort.first()
        val manualPorts = settingsStore.manualPortsEnabled.first()
        val serverDtlsPort = settingsStore.serverDtlsPort.first()
        val serverWgPort = settingsStore.serverWgPort.first()
        val sni = settingsStore.sni.first()
        val vkAuthMode = settingsStore.vkAuthMode.first()
        val captchaMode = settingsStore.captchaMode.first()
        val captchaMethod = settingsStore.captchaSolveMethod.first()
        val wbvCaptchaMethod = settingsStore.captchaWbvSolveMethod.first()
        powerDynamic = settingsStore.powerDynamic.first()
        
        peerInput = peer
        parseHashes(hashes)
        val maxWorkers = (listOf(vkHash1, vkHash2, vkHash3, vkHash4).count { it.isNotBlank() }.coerceAtLeast(1) * 27).toFloat()
        workersInput = roundToGroup(workers.toFloat(), WORKERS_PER_GROUP.toFloat()).coerceIn(WORKERS_PER_GROUP.toFloat(), maxWorkers)
        portInput = port.toString()
        manualPortsEnabled = manualPorts
        serverDtlsPortInput = serverDtlsPort.toString()
        serverWgPortInput = serverWgPort.toString()
        sniInput = sni
        useVKCallsAuth = vkAuthMode != "legacy"
        obfsMode = savedObfsMode
        autoCaptchaEnabled = captchaMode == "auto"
        useWVCaptcha = captchaMode != "rjs"
        wbvManualMode = wbvCaptchaMethod != "auto"
        isManualMode = if (captchaMode == "wv") wbvManualMode else captchaMethod != "auto"
        
        initialized = true
    }

    LaunchedEffect(savedManualPortsEnabled) {
        manualPortsEnabled = savedManualPortsEnabled
    }

    LaunchedEffect(savedServerDtlsPort) {
        serverDtlsPortInput = savedServerDtlsPort.toString()
    }

    LaunchedEffect(savedServerWgPort) {
        serverWgPortInput = savedServerWgPort.toString()
    }

    LaunchedEffect(savedListenPort) {
        portInput = savedListenPort.toString()
    }

    if (!initialized) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun saveTunnelSettingsNow(hashes: String = combinedHashes, onSaved: (() -> Unit)? = null) {
        saveJob?.cancel()
        scope.launch {
            val savedLocalPort = if (manualPortsEnabled) portInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000 else 9000
            settingsStore.save(
                peerInput, hashes, "",
                workersInput.toInt(), "udp", savedLocalPort, sniInput, false
            )
            onSaved?.invoke()
        }
    }

    fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            val savedLocalPort = if (manualPortsEnabled) portInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000 else 9000
            settingsStore.save(
                peerInput, combinedHashes, "",
                workersInput.toInt(), "udp", savedLocalPort, sniInput, false
            )
        }
    }

    val scrollState = rememberScrollState()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var deployOpen by rememberSaveable { mutableStateOf(false) }

    val currentNestedPageCallback by rememberUpdatedState(onNestedPageChanged)
    LaunchedEffect(settingsOpen, deployOpen) {
        currentNestedPageCallback(
            when {
                deployOpen -> 2
                settingsOpen -> 1
                else -> 0
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { currentNestedPageCallback(0) }
    }

    val isPeerValid = peerInput.isNotBlank() && !peerInput.contains(":")
    val isHashesValid = combinedHashes.isNotBlank()
    val isLinkValid = wdttLink.trim().startsWith("wdtt://") && wdttLink.trim().split(":").size >= 6 && wdttLink.trim().split(":")[5].isNotBlank()
    val isManualValid = isPeerValid && isHashesValid && savedConnectionPassword.isNotBlank() && !hasInputHashErrors
    val isValid = if (wdttLinkMode) isLinkValid else isManualValid
    val effectiveServerDtlsPort = if (manualPortsEnabled) serverDtlsPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 56000 else 56000
    val effectiveLocalPort = if (manualPortsEnabled) portInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000 else 9000
    var pendingStartAfterVpnPermission by remember { mutableStateOf(false) }

    fun startTunnelService() {
        val effectiveVkAuthMode = if (useVKCallsAuth) "vkcalls" else "legacy"
        val effectiveCaptchaMode = if (autoCaptchaEnabled) "auto" else if (useWVCaptcha) "wv" else "rjs"
        val effectiveCaptchaSolveMethod = if (!autoCaptchaEnabled && effectiveCaptchaMode == "wv" && isManualMode) "manual" else "auto"
        saveJob?.cancel()
        scope.launch {
            settingsStore.save(
                peerInput, combinedHashes, "",
                workersInput.toInt(), "udp", effectiveLocalPort, sniInput, false
            )
            settingsStore.saveVkAuthMode(effectiveVkAuthMode)
            settingsStore.saveCaptchaMode(effectiveCaptchaMode)
            settingsStore.saveCaptchaSolveMethod(effectiveCaptchaSolveMethod)
        }

        var finalPeer = "$peerInput:$effectiveServerDtlsPort"
        var finalHashes = combinedHashes
        var finalLocalPort = effectiveLocalPort
        var finalPassword = savedConnectionPassword

        if (wdttLinkMode && wdttLink.trim().startsWith("wdtt://")) {
            val clean = wdttLink.trim().removePrefix("wdtt://")
            val parts = clean.split(":")
            if (parts.size >= 5) {
                val ip = parts[0]
                val dtls = parts[1].toIntOrNull() ?: 56000
                finalLocalPort = parts[3].toIntOrNull() ?: 9000
                finalPassword = parts[4]
                val hash = if (parts.size >= 6) parts[5] else ""
                
                finalPeer = "$ip:$dtls"
                val rawHash = stripVkUrlStatic(hash)
                finalHashes = if (rawHash.isNotBlank()) rawHash else normalizeHashes(hash)
            }
        }

        val intent = Intent(context, TunnelService::class.java).apply {
            action = "START"
            putExtra("peer", finalPeer)
            putExtra("vk_hashes", finalHashes)
            putExtra("secondary_vk_hash", "")
            putExtra("total_workers", workersInput.toInt())
            putExtra("port", finalLocalPort)
            putExtra("sni", sniInput)
            putExtra("connection_password", finalPassword)
            putExtra("vk_auth_mode", effectiveVkAuthMode)
            putExtra("captcha_mode", effectiveCaptchaMode)
            putExtra("captcha_solve_method", effectiveCaptchaSolveMethod)
            putExtra("client_ids", activeClientIds)
            putExtra("obfs_mode", obfsMode)
            putExtra("power_dynamic", powerDynamic)
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
        else context.startService(intent)
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (pendingStartAfterVpnPermission) {
            pendingStartAfterVpnPermission = false
            if (VpnService.prepare(context) == null) {
                startTunnelService()
            } else {
                Toast.makeText(context, "VPN-разрешение не выдано", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestVpnAndStart() {
        val vpnIntent = VpnService.prepare(context)
        if (vpnIntent != null) {
            pendingStartAfterVpnPermission = true
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startTunnelService()
        }
    }

    
    if (showSecretsDialog) {
        SecretsDialog(
            settingsStore = settingsStore,
            initialPassword = savedConnectionPassword,
            manualPortsEnabled = manualPortsEnabled,
            initialServerDtlsPort = serverDtlsPortInput,
            initialServerWgPort = serverWgPortInput,
            initialLocalPort = portInput,
            onSaved = { dtls, wg, local ->
                serverDtlsPortInput = dtls
                serverWgPortInput = wg
                portInput = local
            },
            onDismiss = { showSecretsDialog = false }
        )
    }

    if (showHashesDialog) {
        HashesDialog(
            hash1 = vkHash1,
            hash2 = vkHash2,
            hash3 = vkHash3,
            hash4 = vkHash4,
            onSave = { h1, h2, h3, h4 ->
                val cleaned1 = stripVkUrlStatic(h1)
                val cleaned2 = stripVkUrlStatic(h2)
                val cleaned3 = stripVkUrlStatic(h3)
                val cleaned4 = stripVkUrlStatic(h4)
                vkHash1 = cleaned1
                vkHash2 = cleaned2
                vkHash3 = cleaned3
                vkHash4 = cleaned4
                saveTunnelSettingsNow(normalizeHashes(cleaned1, cleaned2, cleaned3, cleaned4)) {
                    showHashesDialog = false
                }
            },
            onDismiss = { showHashesDialog = false }
        )
    }

    if (showClearProfileDialog) {
        Dialog(onDismissRequest = { showClearProfileDialog = false }) {
            AppDialogSurface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppDialogHeader(
                        title = "Очистка профиля",
                        subtitle = "Текущие поля будут сброшены",
                        accent = MaterialTheme.colorScheme.error,
                        onClose = { showClearProfileDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    Text(
                        "Вы точно хотите очистить текущий профиль?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearProfileDialog = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Нет", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                showClearProfileDialog = false
                                scope.launch {
                                    settingsStore.clearActiveProfile()
                                    profileRevision++
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Да", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = deployOpen) { deployOpen = false }
    BackHandler(enabled = exceptionsOpen && settingsOpen && !deployOpen) { exceptionsOpen = false }
    BackHandler(enabled = settingsOpen && !deployOpen && !exceptionsOpen) { settingsOpen = false }

    AnimatedContent(
        targetState = deployOpen,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(180)))
            }
        },
        label = "deploy_nested_page"
    ) { showDeploy ->
        if (showDeploy) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { deployOpen = false }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        "Установка на сервер",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    DeployTab()
                }
            }
        } else {
    AnimatedContent(
        targetState = settingsOpen,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(320)) { it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(180)))
            }
        },
        label = "tunnel_settings_page"
    ) { showSettings ->
        if (showSettings) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = {
                        if (exceptionsOpen) exceptionsOpen = false else settingsOpen = false
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        if (exceptionsOpen) "Исключения для приложений" else "Настройки туннеля",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedContent(
                    targetState = exceptionsOpen,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(225))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "exceptions_settings_page"
                ) { showExceptions ->
                    if (showExceptions) {
                        ExceptionsTab(showTitle = false)
                    } else Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollEdgeFade(
                            scrollState.canScrollBackward,
                            scrollState.canScrollForward,
                            fadeHeight = 14.dp
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
            AnimatedVisibility(
                visible = !wdttLinkMode,
                enter = fadeIn(tween(190)) + expandVertically(tween(300), expandFrom = Alignment.Top),
                exit = fadeOut(tween(190)) + shrinkVertically(tween(300), shrinkTowards = Alignment.Top)
            ) {
                Column {
                    AppSectionCard(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = peerInput,
                            onValueChange = {
                                peerInput = it.filter { c -> !c.isWhitespace() }
                                scheduleSave()
                            },
                            label = { Text("IP сервера или домен (без порта)") },
                            placeholder = { Text("1.2.3.4 (или test.com)") },
                            singleLine = true,
                            isError = !isPeerValid && peerInput.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                        )

                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            
                AppSectionCard(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Мощность",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (powerDynamic) "Авто" else "${currentWorkers.toInt()}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PowerModeTile(
                            title = "Фиксированная",
                            selected = !powerDynamic,
                            enabled = !tunnelRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            powerDynamic = false
                            scope.launch { settingsStore.savePowerDynamic(false) }
                        }
                        PowerModeTile(
                            title = "Динамическая",
                            selected = powerDynamic,
                            enabled = !tunnelRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            powerDynamic = true
                            scope.launch { settingsStore.savePowerDynamic(true) }
                        }
                    }

                    AnimatedVisibility(
                        visible = !powerDynamic,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))

                            val maxWorkers = dynamicMaxWorkers
                            val minWorkers = WORKERS_PER_GROUP.toFloat()
                            val currentWorkersVal = roundToGroup(currentWorkers.coerceIn(minWorkers, maxWorkers), WORKERS_PER_GROUP.toFloat())

                            CompactSteppedSlider(
                                value = currentWorkersVal,
                                onValueChange = { raw ->
                                    workersInput = roundToGroup(raw, WORKERS_PER_GROUP.toFloat())
                                    scheduleSave()
                                },
                                valueRange = minWorkers..maxWorkers,
                                stepSize = WORKERS_PER_GROUP.toFloat(),
                                enabled = !tunnelRunning,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = { showHashesDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasInputHashErrors) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Tag, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Настройка VK Хешей ($filledHashCount/4)", fontWeight = FontWeight.SemiBold)
                    }

                    val errorTexts = hashErrors.filter { !it.contains("короткий") }
                    if (errorTexts.isNotEmpty()) {
                        Text(
                            text = errorTexts.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Режим", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProtocolChip("Вызов", useVKCallsAuth, enabled = !tunnelRunning) {
                                    useVKCallsAuth = true
                                    scope.launch { settingsStore.saveVkAuthMode("vkcalls") }
                                }
                                ProtocolChip("Капча", !useVKCallsAuth, enabled = !tunnelRunning) {
                                    useVKCallsAuth = false
                                    scope.launch { settingsStore.saveVkAuthMode("legacy") }
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(72.dp).padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Маскировка", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ProtocolChip("Аудио", obfsMode == "audio", enabled = !tunnelRunning) {
                                    obfsMode = "audio"
                                    scope.launch { settingsStore.saveObfsMode("audio") }
                                }
                                ProtocolChip("Видео", obfsMode == "video", enabled = !tunnelRunning) {
                                    obfsMode = "video"
                                    scope.launch { settingsStore.saveObfsMode("video") }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !useVKCallsAuth,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (autoCaptchaEnabled) "Авто капча" else "Ручная капча",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = autoCaptchaEnabled,
                                    enabled = !tunnelRunning,
                                    onCheckedChange = { enabled ->
                                        autoCaptchaEnabled = enabled
                                        scope.launch {
                                            if (enabled) {
                                                settingsStore.saveCaptchaMode("auto")
                                                settingsStore.saveCaptchaSolveMethod("auto")
                                            } else {
                                                val mode = if (useWVCaptcha) "wv" else "rjs"
                                                settingsStore.saveCaptchaMode(mode)
                                                settingsStore.saveCaptchaSolveMethod(if (mode == "wv" && isManualMode) "manual" else "auto")
                                            }
                                        }
                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = !autoCaptchaEnabled,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Метод обхода капчи",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ProtocolChip("WBV", useWVCaptcha, enabled = !tunnelRunning) {
                                                useWVCaptcha = true
                                                isManualMode = wbvManualMode
                                                scope.launch {
                                                    settingsStore.saveCaptchaMode("wv")
                                                    settingsStore.saveCaptchaSolveMethod(if (wbvManualMode) "manual" else "auto")
                                                }
                                            }
                                            ProtocolChip("RJS", !useWVCaptcha, enabled = !tunnelRunning, isError = false) {
                                                useWVCaptcha = false
                                                isManualMode = false
                                                scope.launch {
                                                    settingsStore.saveCaptchaMode("rjs")
                                                    settingsStore.saveCaptchaSolveMethod("auto")
                                                }
                                            }
                                        }
                                    }

                                    
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )

                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Режим обхода",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (useWVCaptcha) {
                                                ProtocolChip(
                                                    "РУЧ",
                                                    isManualMode,
                                                    enabled = !tunnelRunning,
                                                    isError = false
                                                ) {
                                                    isManualMode = true
                                                    wbvManualMode = true
                                                    scope.launch { settingsStore.saveWbvCaptchaSolveMethod("manual") }
                                                }
                                                ProtocolChip(
                                                    "АВТ",
                                                    !isManualMode,
                                                    enabled = !tunnelRunning,
                                                    isError = false
                                                ) {
                                                    isManualMode = false
                                                    wbvManualMode = false
                                                    scope.launch { settingsStore.saveWbvCaptchaSolveMethod("auto") }
                                                }
                                            } else {
                                                ProtocolChip(
                                                    "АВТ",
                                                    selected = true,
                                                    enabled = false,
                                                    isError = false
                                                ) {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Режим ссылки",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = wdttLinkMode,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsStore.saveWdttLinkMode(enabled)
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = wdttLinkMode,
                        enter = fadeIn(tween(190)) + expandVertically(tween(300), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(190)) + shrinkVertically(tween(300), shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            var linkText by remember(wdttLink) { mutableStateOf(wdttLink) }
                            OutlinedTextField(
                                value = linkText,
                                onValueChange = {
                                    val cleaned = it.filter { c -> !c.isWhitespace() }
                                    linkText = cleaned
                                    scope.launch { settingsStore.saveWdttLink(cleaned) }
                                },
                                label = { Text("Ссылка wdtt://") },
                                placeholder = { Text("Ссылка wdtt://") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                )
                            )
                        }
                    }
                }

        
        val tunnelSecretsMissing = savedConnectionPassword.isBlank()

        AnimatedVisibility(
            visible = !wdttLinkMode,
            enter = fadeIn(tween(190)) + expandVertically(tween(300), expandFrom = Alignment.Top),
            exit = fadeOut(tween(190)) + shrinkVertically(tween(300), shrinkTowards = Alignment.Top)
        ) {
            Column {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showSecretsDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (tunnelSecretsMissing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (tunnelSecretsMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    1.dp,
                    if (tunnelSecretsMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Секреты", fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { exceptionsOpen = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Исключения для приложений", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        Spacer(Modifier.height(12.dp))
        AppSectionCard(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Профиль", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2).forEach { profile ->
                    TechnicalChoice(
                        label = "Пр. $profile",
                        selected = activeProfile == profile,
                        enabled = !tunnelRunning,
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch { settingsStore.saveActiveProfile(profile) }
                    }
                }
                IconButton(
                    onClick = { showClearProfileDialog = true },
                    enabled = !tunnelRunning,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Очистить текущий профиль",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text("Резервные VK Client IDs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val selectedClientIds = activeClientIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            listOf("8202606", "6287487").forEach { id ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = id in selectedClientIds,
                            enabled = !tunnelRunning,
                            onCheckedChange = { checked ->
                                val updated = if (checked) {
                                    (selectedClientIds + id).distinct()
                                } else {
                                    selectedClientIds - id
                                }
                                if (updated.isNotEmpty()) {
                                    scope.launch { settingsStore.saveActiveClientIds(updated.joinToString(",")) }
                                }
                            }
                        )
                        Text(id, style = MaterialTheme.typography.bodyMedium)
                    }
                    clientIdCheckResults[id]?.let { valid ->
                        Icon(
                            imageVector = if (valid) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (valid) "Доступен" else "Недоступен",
                            tint = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isCheckingClientIds = true
                        val results = withContext(Dispatchers.IO) {
                            listOf("8202606", "6287487").associateWith(::checkVkClientId)
                        }
                        val json = org.json.JSONObject()
                        results.forEach { (id, valid) -> json.put(id, valid) }
                        settingsStore.saveClientIdCheckResults(json.toString())
                        isCheckingClientIds = false
                    }
                },
                enabled = !isCheckingClientIds,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isCheckingClientIds) "Проверка…" else "Проверить Client IDs")
            }
        }
        
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Для мобильных сетей.\nЕсли не работает режим \"Вызов\", попробуйте \"Капча\". Если автокапча не работает, попробуйте ручную. Маскировка сильно роли не играет.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
            }
        } else Box(modifier = Modifier.fillMaxSize()) {
            TunnelDashboard(
                peer = if (wdttLinkMode) wdttLink.removePrefix("wdtt://").substringBefore(":").ifBlank { "Не указан" }
                    else peerInput.ifBlank { "Не указан" },
                mode = if (useVKCallsAuth) "Вызов" else "Капча",
                obfuscation = if (obfsMode == "video") "Видео" else "Аудио",
                workers = if (powerDynamic) "Авто" else currentWorkers.toInt().toString(),
                tunnelRunning = tunnelRunning,
                tunnelVerified = tunnelRunning && !tunnelConfig.isNullOrBlank(),
                cooldownActive = cooldownActive,
                enabled = (isValid && !cooldownActive) || tunnelRunning,
                modifier = Modifier.padding(bottom = mainPageBottomPadding),
                onOpenSettings = { settingsOpen = true },
                onOpenDeploy = { deployOpen = true },
                onToggleTunnel = {
                    if (tunnelRunning) {
                        context.startService(
                            Intent(context, TunnelService::class.java).apply { action = "STOP" }
                        )
                    } else {
                        requestVpnAndStart()
                    }
                }
            )
            mainPageOverlay()
        }
    }
        }
    }
}

@Composable
private fun TunnelDashboard(
    peer: String,
    mode: String,
    obfuscation: String,
    workers: String,
    tunnelRunning: Boolean,
    tunnelVerified: Boolean,
    cooldownActive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenDeploy: () -> Unit,
    onToggleTunnel: () -> Unit
) {
    val dashboardScale = 1.12f
    val density = LocalDensity.current
    val view = LocalView.current
    var wasVerified by remember { mutableStateOf(false) }

    LaunchedEffect(tunnelVerified) {
        if (tunnelVerified && !wasVerified) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        wasVerified = tunnelVerified
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Настройки туннеля",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onOpenDeploy, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = "Установка на сервер",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * dashboardScale)
        ) {
            AuroraPowerButton(
                tunnelRunning = tunnelRunning,
                tunnelVerified = tunnelVerified,
                enabled = enabled,
                dashboardScale = dashboardScale,
                onToggle = onToggleTunnel
            )

            Spacer(Modifier.height(18.dp * dashboardScale))

            Text(
                text = when {
                    tunnelVerified -> "Туннель подключён"
                    tunnelRunning -> "Подключение…"
                    cooldownActive -> "Подождите…"
                    enabled -> "Готов к подключению"
                    else -> "Заполните настройки туннеля"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (tunnelVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp * dashboardScale))

            AuroraStatusCard(
                peer = peer,
                mode = mode,
                workers = workers,
                obfuscation = obfuscation,
                tunnelRunning = tunnelRunning,
                tunnelVerified = tunnelVerified,
                dashboardScale = dashboardScale
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AuroraStatusCard(
    peer: String,
    mode: String,
    workers: String,
    obfuscation: String,
    tunnelRunning: Boolean,
    tunnelVerified: Boolean,
    dashboardScale: Float
) {
    val palette = MaterialTheme.colorScheme
    val ds = dashboardScale

    val reveal by animateFloatAsState(
        targetValue = if (tunnelVerified) 1f else 0f,
        animationSpec = tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "status_reveal"
    )
    val infinite = rememberInfiniteTransition(label = "status_infinite")
    val breath by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "status_breath"
    )

    val borderColor by animateColorAsState(
        targetValue = if (tunnelVerified) lerp(palette.outlineVariant, palette.primary, 0.5f) else palette.outlineVariant,
        animationSpec = tween(650), label = "status_border"
    )
    val valueColor by animateColorAsState(
        targetValue = if (tunnelVerified) palette.primary else palette.onSurface,
        animationSpec = tween(650), label = "status_value"
    )
    val dotColor by animateColorAsState(
        targetValue = when {
            tunnelVerified -> palette.primary
            tunnelRunning -> lerp(palette.primary, palette.tertiary, 0.5f)
            else -> palette.onSurfaceVariant
        },
        animationSpec = tween(650), label = "status_dot"
    )
    val cardBg = lerp(palette.surface, palette.primary, 0.05f * reveal + 0.02f)
    val dotRingProgress by animateFloatAsState(
        targetValue = if (tunnelRunning) 1f else 0f,
        animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "status_dot_ring"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(0.78f * ds),
        shape = RoundedCornerShape(24.dp * ds),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.35f + 0.35f * reveal * breath)),
        shadowElevation = (4.dp + 6.dp * reveal)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp * ds, vertical = 13.dp * ds),
            verticalArrangement = Arrangement.spacedBy(10.dp * ds)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp * ds)) {
                        val r = size.minDimension / 2f
                        if (dotRingProgress > 0.01f) {
                            drawCircle(
                                color = dotColor,
                                radius = r * (0.9f + 0.35f * breath * dotRingProgress),
                                center = center,
                                alpha = (0.30f - 0.10f * breath) * dotRingProgress
                            )
                        }
                        drawCircle(color = dotColor, radius = r * 0.75f, center = center)
                    }
                    Spacer(Modifier.width(6.dp * ds))
                    Text(
                        "IP сервера",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onSurfaceVariant
                    )
                }
                Text(
                    peer,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                borderColor.copy(alpha = 0.45f + 0.25f * reveal),
                                Color.Transparent
                            )
                        )
                    )
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AuroraMetric("Режим", mode, valueColor, Modifier.weight(1f))
                AuroraDivider(ds, borderColor, reveal)
                AuroraMetric("Мощность", workers, valueColor, Modifier.weight(1f))
                AuroraDivider(ds, borderColor, reveal)
                AuroraMetric("Скрытие", obfuscation, valueColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AuroraMetric(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun AuroraDivider(
    dashboardScale: Float,
    color: Color,
    reveal: Float
) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(38.dp * dashboardScale)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.40f + 0.20f * reveal),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun AuroraPowerButton(
    tunnelRunning: Boolean,
    tunnelVerified: Boolean,
    enabled: Boolean,
    dashboardScale: Float,
    onToggle: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val connecting = tunnelRunning && !tunnelVerified
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (tunnelRunning) 1.10f else 0.96f,
        animationSpec = tween(650, easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "aurora_scale"
    )
    val reveal by animateFloatAsState(
        targetValue = if (tunnelVerified) 1f else 0f,
        animationSpec = tween(620, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "aurora_reveal"
    )
    val connectAlpha by animateFloatAsState(
        targetValue = if (connecting) 1f else 0f,
        animationSpec = tween(300),
        label = "aurora_connect"
    )

    val infinite = rememberInfiniteTransition(label = "aurora_infinite")
    val rippleT by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "aurora_ripple"
    )
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "aurora_spin"
    )
    val slowSpin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "aurora_slowspin"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aurora_breath"
    )

    val offColor by animateColorAsState(
        targetValue = palette.surfaceVariant,
        animationSpec = tween(650), label = "aurora_off"
    )
    val onColor by animateColorAsState(
        targetValue = palette.primary,
        animationSpec = tween(650), label = "aurora_on"
    )
    val iconTint by animateColorAsState(
        targetValue = if (tunnelVerified) palette.onPrimary else palette.onSurfaceVariant,
        animationSpec = tween(650), label = "aurora_icon"
    )
    val offHi = lerp(offColor, palette.surface, 0.5f)
    val onHi = lerp(onColor, Color.White, 0.30f)
    val onLo = lerp(onColor, Color.Black, 0.14f)

    Box(
        modifier = Modifier.size(232.dp * dashboardScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2f
            val buttonR = maxR * 0.72f
            val ringR = buttonR + 9.dp.toPx()
            val ringSize = Size(ringR * 2, ringR * 2)
            val ringTopLeft = Offset(center.x - ringR, center.y - ringR)

            if (reveal > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(onColor.copy(alpha = 0.38f * reveal * breath), Color.Transparent),
                        center = center,
                        radius = maxR
                    ),
                    radius = maxR,
                    center = center
                )
                var i = 0
                while (i < 2) {
                    val f = (rippleT + i * 0.5f) % 1f
                    drawCircle(
                        color = onColor,
                        radius = buttonR + (maxR - buttonR) * f,
                        center = center,
                        alpha = (1f - f) * 0.30f * reveal,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    i++
                }
                drawCircle(
                    color = onColor,
                    radius = ringR,
                    center = center,
                    alpha = 0.55f * reveal,
                    style = Stroke(width = 3.dp.toPx())
                )
                rotate(slowSpin) {
                    drawArc(
                        color = onColor,
                        startAngle = 0f,
                        sweepAngle = 55f,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringSize,
                        alpha = 0.7f * reveal,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            if (connectAlpha > 0f) {
                drawCircle(
                    color = onColor,
                    radius = ringR,
                    center = center,
                    alpha = 0.15f * connectAlpha,
                    style = Stroke(width = 3.dp.toPx())
                )
                rotate(spin) {
                    drawArc(
                        color = onColor,
                        startAngle = 0f,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringSize,
                        alpha = connectAlpha,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            val offRingAlpha = (1f - reveal) * (1f - connectAlpha)
            if (offRingAlpha > 0.01f) {
                drawCircle(
                    color = palette.onSurfaceVariant,
                    radius = ringR,
                    center = center,
                    alpha = (0.14f + 0.08f * breath) * offRingAlpha,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(164.dp * dashboardScale)
                .scale(scale)
                .shadow(elevation = 14.dp, shape = CircleShape)
                .clip(CircleShape)
                .clickable(enabled = enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                if (reveal < 1f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(offHi, offColor),
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center,
                        alpha = 1f - reveal
                    )
                }
                if (reveal > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(onHi, onLo),
                            center = Offset(center.x, center.y - r * 0.25f),
                            radius = r * 1.1f
                        ),
                        radius = r,
                        center = center,
                        alpha = reveal
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(center.x, center.y - r * 0.6f),
                        radius = r * 0.9f
                    ),
                    radius = r,
                    center = center
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_vpn_key_gradient),
                contentDescription = if (tunnelRunning) "Остановить туннель" else "Подключить туннель",
                modifier = Modifier.size(104.dp * dashboardScale),
                colorFilter = ColorFilter.tint(iconTint)
            )
        }
    }
}

@Composable
private fun TechnicalChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private fun checkVkClientId(appId: String): Boolean {
    repeat(2) {
        try {
            val url = java.net.URL("https://oauth.vk.com/authorize?client_id=$appId&display=mobile&response_type=token")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return !(response.contains("\"error\"") &&
                (response.contains("invalid_client") || response.contains("invalid_request")))
        } catch (_: Exception) {
            // Повторяем один раз при временной сетевой ошибке.
        }
    }
    return false
}

@Composable
private fun PowerModeTile(
    title: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(250),
        label = "power_tile_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled && selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        },
        animationSpec = tween(250),
        label = "power_tile_border"
    )
    val titleColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(250),
        label = "power_tile_title"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProtocolChip(label: String, selected: Boolean, enabled: Boolean = true, isError: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
            disabledSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            disabledSelectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        )
    )
}

@Composable
private fun CompactSteppedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.55f)
    val thumbStrokeColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { 9.dp.toPx() }
    val trackWidthPx = with(density) { 5.dp.toPx() }

    fun snap(raw: Float): Float {
        val min = valueRange.start
        val max = valueRange.endInclusive
        val snapped = (((raw - min) / stepSize).roundToInt() * stepSize) + min
        return snapped.coerceIn(min, max)
    }

    fun positionToValue(x: Float, width: Float): Float {
        val left = thumbRadiusPx
        val right = (width - thumbRadiusPx).coerceAtLeast(left + 1f)
        val fraction = ((x.coerceIn(left, right) - left) / (right - left)).coerceIn(0f, 1f)
        return snap(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
    }

    Canvas(
        modifier = modifier
            .height(34.dp)
            .pointerInput(enabled, valueRange, stepSize) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange(positionToValue(offset.x, size.width.toFloat()))
                }
            }
            .pointerInput(enabled, valueRange, stepSize) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    onValueChange(positionToValue(change.position.x, size.width.toFloat()))
                }
            }
    ) {
        val centerY = size.height / 2f
        val left = thumbRadiusPx
        val right = size.width - thumbRadiusPx
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
        val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
        val thumbX = left + (right - left) * fraction

        drawLine(
            color = inactiveColor,
            start = Offset(left, centerY),
            end = Offset(right, centerY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(left, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round
        )

        val tickCount = (((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt()).coerceAtLeast(1)
        repeat(tickCount + 1) { index ->
            val tickFraction = index / tickCount.toFloat()
            val tickX = left + (right - left) * tickFraction
            drawCircle(
                color = if (tickX <= thumbX) activeColor else inactiveColor,
                radius = 2.dp.toPx(),
                center = Offset(tickX, centerY)
            )
        }

        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = thumbStrokeColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Округляет значение до ближайшего кратного группы.
 */
private fun roundToGroup(value: Float, groupSize: Float): Float {
    val groups = (value / groupSize).toInt()
    val remainder = value % groupSize
    return if (remainder >= groupSize / 2f) {
        (groups + 1) * groupSize
    } else {
        groups * groupSize
    }
}


