package com.wdtt.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.LogEntry
import com.wdtt.client.TunnelManager
import com.wdtt.client.SettingsStore
import com.wdtt.client.ui.components.verticalScrollEdgeFade
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) {
    ALL("Все"),
    ERRORS("Ошибки"),
    NO_STATS("Без статистики")
}

private data class LogLineStyle(
    val tag: String?,
    val body: String,
    val accent: Color,
    val isSuccess: Boolean
)

private val tagRegex = Regex("^\\[([^\\]]+)\\]\\s*")

@Composable
private fun resolveLogStyle(entry: LogEntry): LogLineStyle {
    val palette = MaterialTheme.colorScheme
    val match = tagRegex.find(entry.message)
    val tag = match?.groupValues?.get(1)
    val body = if (match != null) entry.message.substring(match.value.length) else entry.message
    val isSuccess = body.contains("✓") || tag == "READY"

    val accent = when {
        entry.isError -> palette.error
        isSuccess -> palette.primary
        tag == null -> palette.onSurfaceVariant
        tag.startsWith("КАПЧА") -> palette.tertiary
        tag == "СТАТИСТИКА" -> palette.secondary
        tag == "СЕТЬ" || tag == "СТОП" -> palette.error
        else -> palette.primary
    }
    return LogLineStyle(tag, body, accent, isSuccess)
}

private fun formatLogTime(ts: Long): String {
    if (ts <= 0L) return ""
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
}

private fun buildLogsText(logs: List<LogEntry>): String = logs.joinToString("\n") { entry ->
    val time = formatLogTime(entry.lastTimestamp)
    val prefix = if (time.isNotEmpty()) "$time " else ""
    val repeats = if (entry.count > 1) " (x${entry.count})" else ""
    "$prefix${entry.message}$repeats"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val loggingEnabled by settingsStore.loggingEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    val currentLogs by TunnelManager.logs.collectAsStateWithLifecycle()
    val tunnelRunning by TunnelManager.running.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL.name) }
    val activeFilter = remember(filter) { LogFilter.valueOf(filter) }

    val visibleLogs = remember(currentLogs, activeFilter) {
        when (activeFilter) {
            LogFilter.ALL -> currentLogs
            LogFilter.ERRORS -> currentLogs.filter { it.isError }
            LogFilter.NO_STATS -> currentLogs.filter { !it.message.startsWith("[СТАТИСТИКА]") }
        }
    }
    val errorCount = remember(currentLogs) { currentLogs.count { it.isError } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Лог событий",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append("${currentLogs.size} событий")
                        if (errorCount > 0) append(" · ошибок: $errorCount")
                        if (tunnelRunning) append(" · туннель активен")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogsActionButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Очистить лог",
                    enabled = currentLogs.isNotEmpty()
                ) {
                    TunnelManager.clearLogs()
                }
                LogsActionButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Копировать лог",
                    enabled = currentLogs.isNotEmpty()
                ) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("WDTT Logs", buildLogsText(currentLogs)))
                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                }
                LogsActionButton(
                    icon = Icons.Default.Share,
                    contentDescription = "Поделиться логом",
                    enabled = currentLogs.isNotEmpty()
                ) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, buildLogsText(currentLogs))
                        putExtra(Intent.EXTRA_SUBJECT, "WDTT Logs")
                    }
                    context.startActivity(Intent.createChooser(intent, "Отправить лог"))
                }
            }
        }

        
        AppSectionCard(
            modifier = Modifier.padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Активное логирование",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = loggingEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsStore.saveLoggingEnabled(enabled)
                            if (!enabled) {
                                TunnelManager.clearLogs()
                            }
                        }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = activeFilter == option,
                        onClick = { filter = option.name },
                        label = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (activeFilter == option) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = activeFilter == option,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        
        LogsConsoleCard(
            modifier = Modifier.weight(1f),
            visibleLogs = visibleLogs,
            hasAnyLogs = currentLogs.isNotEmpty(),
            loggingEnabled = loggingEnabled,
            listState = listState
        )
    }
}

@Composable
private fun LogsActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val isDark = palette.background.luminance() < 0.22f
    val bg = if (isDark) {
        lerp(palette.surface, palette.surfaceVariant, 0.22f)
    } else {
        lerp(palette.surface, palette.surfaceVariant, 0.42f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 2.dp,
        modifier = Modifier.size(44.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) palette.primary else palette.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LogsConsoleCard(
    modifier: Modifier = Modifier,
    visibleLogs: List<LogEntry>,
    hasAnyLogs: Boolean,
    loggingEnabled: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val palette = MaterialTheme.colorScheme
    val isDark = palette.background.luminance() < 0.22f
    val container = if (isDark) {
        lerp(palette.surface, palette.surfaceVariant, 0.10f)
    } else {
        lerp(palette.surface, palette.surfaceVariant, 0.28f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = container,
        border = BorderStroke(1.dp, palette.outlineVariant.copy(alpha = 0.26f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        if (visibleLogs.isEmpty()) {
            LogsEmptyState(hasAnyLogs = hasAnyLogs, loggingEnabled = loggingEnabled)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollEdgeFade(
                        canScrollBackward = listState.canScrollBackward,
                        canScrollForward = listState.canScrollForward,
                        fadeHeight = 24.dp
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(visibleLogs, key = { it.key }) { entry ->
                    LogLine(entry)
                }
            }
        }
    }
}

@Composable
private fun LogsEmptyState(hasAnyLogs: Boolean, loggingEnabled: Boolean) {
    val palette = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.primary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.18f)),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = palette.primary.copy(alpha = 0.75f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = when {
                    !loggingEnabled -> "Логирование отключено"
                    hasAnyLogs -> "По текущему фильтру ничего нет"
                    else -> "Лог пуст"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !loggingEnabled -> "Включите переключатель выше, чтобы собирать события"
                    hasAnyLogs -> "Попробуйте другой фильтр"
                    else -> "События туннеля появятся здесь после подключения"
                },
                style = MaterialTheme.typography.labelMedium,
                color = palette.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LogLine(entry: LogEntry) {
    val palette = MaterialTheme.colorScheme
    val style = resolveLogStyle(entry)

    val rowBg by animateColorAsState(
        targetValue = if (entry.isError) palette.errorContainer.copy(alpha = 0.30f) else Color.Transparent,
        animationSpec = tween(400),
        label = "log_row_bg"
    )

    var trigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(entry.count) { trigger++ }

    val animatedScale by animateFloatAsState(
        targetValue = if (trigger > 0) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
        finishedListener = { trigger = 0 }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = style.accent.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, style.accent.copy(alpha = 0.25f)),
            modifier = Modifier
                .defaultMinSize(minWidth = 26.dp, minHeight = 22.dp)
                .graphicsLayer(scaleX = animatedScale, scaleY = animatedScale)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Text(
                    text = "${entry.count}",
                    color = style.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (style.tag != null) {
                    Surface(
                        color = style.accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = style.tag,
                            color = style.accent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                val time = formatLogTime(entry.lastTimestamp)
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        color = palette.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = style.body,
                color = when {
                    entry.isError -> palette.error
                    style.isSuccess -> palette.primary
                    else -> palette.onSurface
                },
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (entry.isError || style.isSuccess) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 17.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
