package com.wdtt.client.ui

import com.wdtt.client.ui.dialogs.ImportantInfoDialog
import com.wdtt.client.ui.components.verticalScrollEdgeFade
import androidx.compose.runtime.MutableState

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.BuildConfig
import com.wdtt.client.R
import com.wdtt.client.SettingsStore
import com.wdtt.client.UPDATE_DIALOG_ACTION_POSTPONED
import com.wdtt.client.UPDATE_DIALOG_ACTION_UPDATE
import com.wdtt.client.fetchLatestReleaseInfo
import com.wdtt.client.isNewerVersion
import kotlinx.coroutines.launch

private const val ReleasesUrl = "https://github.com/Yan4ik000/ProxyTurn-Android/releases"
private const val IssuesUrl = "https://github.com/Yan4ik000/ProxyTurn-Android/issues/new"
private const val DeveloperProfileUrl = "https://github.com/amurcanov"
private const val RepositoryUrl = "https://github.com/Yan4ik000/ProxyTurn-Android"

private val browserPackages = listOf(
    "com.android.chrome",
    "com.google.android.googlequicksearchbox",
    "org.mozilla.firefox",
    "com.yandex.browser",
    "ru.yandex.searchplugin",
    "com.yandex.browser.lite",
    "com.opera.browser",
    "com.opera.mini.native",
    "com.microsoft.emmx",
    "com.brave.browser",
    "com.duckduckgo.mobile.android",
    "com.sec.android.app.sbrowser",
    "com.vivaldi.browser",
    "com.kiwibrowser.browser",
)

private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val pm = context.packageManager
        val uri = Uri.parse(url)
        for (pkg in browserPackages) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(pkg)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        if (intent.resolveActivity(pm) != null) context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
private fun infoTileColor(): Color {
    val palette = MaterialTheme.colorScheme
    val isDark = palette.background.luminance() < 0.22f
    return if (isDark) {
        lerp(palette.surface, palette.surfaceVariant, 0.22f)
    } else {
        lerp(palette.surface, palette.surfaceVariant, 0.42f)
    }
}

@Composable
private fun infoTileBorder(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

@Composable
private fun InfoIconTile(
    accent: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(size * 0.4f),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun InfoTab(
    actionsExpandedState: MutableState<Boolean> = rememberSaveable { mutableStateOf(true) },
    projectExpandedState: MutableState<Boolean> = rememberSaveable { mutableStateOf(true) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { SettingsStore(context) }
    val currentVersion = remember { "v${BuildConfig.VERSION_NAME.removePrefix("v")}" }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var pendingManualRelease by remember { mutableStateOf<com.wdtt.client.AppReleaseInfo?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var actionsExpanded by actionsExpandedState
    var projectExpanded by projectExpandedState
    val updateLatestVersion by settingsStore.updateLatestVersion.collectAsStateWithLifecycle(initialValue = "")
    val updateLastError by settingsStore.updateLastError.collectAsStateWithLifecycle(initialValue = "")
    val scrollState = rememberScrollState()
    val hasUpdate = updateLatestVersion.isNotBlank() && isNewerVersion(currentVersion, updateLatestVersion)
    val updateStatus = remember(isCheckingUpdates, updateLatestVersion, updateLastError, currentVersion) {
        when {
            isCheckingUpdates -> "Проверяем GitHub releases..."
            updateLatestVersion.isNotBlank() && isNewerVersion(currentVersion, updateLatestVersion) ->
                "На GitHub доступна версия $updateLatestVersion"
            updateLatestVersion.isNotBlank() -> "Последняя версия: $updateLatestVersion"
            updateLastError.isNotBlank() -> "Последняя проверка завершилась ошибкой"
            else -> "Проверить GitHub вручную"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp)
            .verticalScrollEdgeFade(scrollState.canScrollBackward, scrollState.canScrollForward)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Информация",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasUpdate) "Доступно обновление $updateLatestVersion" else "Версия $currentVersion",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        InfoHeroCard(
            currentVersion = currentVersion,
            hasUpdate = hasUpdate,
            onSupportClick = { openUrlInBrowser(context, RepositoryUrl) }
        )

        ExpandableSectionCard(
            title = "Действия",
            itemCount = "3 пункта",
            expanded = actionsExpanded,
            onToggle = { actionsExpanded = !actionsExpanded },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoActionTile(
                    title = "Поднять вопрос",
                    subtitle = "Открыть GitHub issue",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = { openUrlInBrowser(context, IssuesUrl) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                InfoActionTile(
                    title = "Собрать отчёт",
                    subtitle = "Android, ABI, версия, устройство",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("WDTT Report", buildSupportReport()))
                        Toast.makeText(context, "Отчёт сформирован и скопирован", Toast.LENGTH_SHORT).show()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            WideActionTile(
                title = "Проверить обновления",
                subtitle = updateStatus,
                accent = MaterialTheme.colorScheme.primary,
                onClick = {
                    if (isCheckingUpdates) return@WideActionTile
                    isCheckingUpdates = true
                    scope.launch {
                        val checkedAt = System.currentTimeMillis()
                        val release = fetchLatestReleaseInfo(currentVersion)
                        val latest = release?.versionTag
                        settingsStore.saveUpdateState(
                            lastCheckAt = checkedAt,
                            latestVersion = latest ?: "",
                            error = if (release == null) "Не удалось проверить" else ""
                        )
                        isCheckingUpdates = false

                        if (release == null) {
                            val message = if (updateLatestVersion.isNotBlank()) {
                                "Не удалось проверить. Последняя известная версия: $updateLatestVersion"
                            } else {
                                "Не удалось проверить обновления"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        if (isNewerVersion(currentVersion, release.versionTag)) {
                            settingsStore.saveUpdateDialogShown(release.versionTag, checkedAt)
                            pendingManualRelease = release
                        } else if (isNewerVersion(release.versionTag, currentVersion)) {
                            Toast.makeText(
                                context,
                                "У вас версия $currentVersion, хотя последняя - ${release.versionTag}. Вы путешественник во времени?",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "У вас уже последняя версия: ${release.versionTag}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        pendingManualRelease?.let { release ->
            AppUpdateDialog(
                release = release,
                onPostpone = {
                    pendingManualRelease = null
                    Toast.makeText(context, "Обновление отложено на 24 часа.", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        val now = System.currentTimeMillis()
                        settingsStore.saveUpdatePostpone(
                            version = release.versionTag,
                            until = now + 24L * 60L * 60L * 1000L
                        )
                        settingsStore.saveUpdateDialogAction(
                            version = release.versionTag,
                            action = UPDATE_DIALOG_ACTION_POSTPONED,
                            actedAt = now
                        )
                    }
                },
                onUpdate = {
                    pendingManualRelease = null
                    scope.launch {
                        settingsStore.saveUpdateDialogAction(
                            version = release.versionTag,
                            action = UPDATE_DIALOG_ACTION_UPDATE,
                            actedAt = System.currentTimeMillis()
                        )
                        openUrlInBrowser(context, release.releaseUrl)
                    }
                }
            )
        }

        ExpandableSectionCard(
            title = "О проекте",
            itemCount = "2 ссылки",
            expanded = projectExpanded,
            onToggle = { projectExpanded = !projectExpanded },
            icon = {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        ) {
            ProjectLinkRow(
                title = "Репозиторий WDTT",
                subtitle = "Исходники и релизы приложения",
                onClick = { openUrlInBrowser(context, RepositoryUrl) },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            ProjectLinkRow(
                title = "Актуальные релизы",
                subtitle = "Страница загрузки APK",
                onClick = { openUrlInBrowser(context, ReleasesUrl) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Автор оригинального WDTT",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.clickable { openUrlInBrowser(context, DeveloperProfileUrl) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showHelpDialog) ImportantInfoDialog(onDismiss = { showHelpDialog = false })
}

@Composable
private fun InfoHeroCard(
    currentVersion: String,
    hasUpdate: Boolean,
    onSupportClick: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val versionColor by animateColorAsState(
        targetValue = if (hasUpdate) palette.primary else palette.onSurfaceVariant,
        animationSpec = tween(400),
        label = "hero_version_color"
    )

    AppSectionCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = palette.primary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.20f)),
                modifier = Modifier.size(62.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tile_logo_w),
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "WDTT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface
                )
                Text(
                    text = "WireGuard · DTLS · TURN",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = versionColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, versionColor.copy(alpha = 0.22f))
            ) {
                Text(
                    text = currentVersion,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = versionColor
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(versionColor)
            )
            Text(
                text = if (hasUpdate) "Есть новая версия — проверьте обновления" else "Приложение актуально",
                style = MaterialTheme.typography.labelMedium,
                color = versionColor
            )
        }

        Button(
            onClick = onSupportClick,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.primary,
                contentColor = palette.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Поддержать проект звездой", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ExpandableSectionCard(
    title: String,
    itemCount: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "section_arrow_rotation"
    )

    AppSectionCard(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoIconTile { icon() }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            MetaChip(text = itemCount)

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(arrowRotation)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(modifier = Modifier.height(0.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                content()
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoActionTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = infoTileColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, infoTileBorder()),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoIconTile { icon() }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WideActionTile(
    title: String,
    subtitle: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = infoTileColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, infoTileBorder()),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoIconTile(accent = accent) { icon() }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProjectLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = infoTileColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, infoTileBorder()),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoIconTile(accent = MaterialTheme.colorScheme.primary) { icon() }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun buildSupportReport(): String {
    val androidVersion = Build.VERSION.RELEASE ?: "?"
    val sdkInt = Build.VERSION.SDK_INT
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
    val supportedAbis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "unknown" }
    val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }
    val brand = Build.BRAND.orEmpty().ifBlank { "unknown" }
    val model = Build.MODEL.orEmpty().ifBlank { "unknown" }
    val device = Build.DEVICE.orEmpty().ifBlank { "unknown" }
    val product = Build.PRODUCT.orEmpty().ifBlank { "unknown" }
    val hardware = Build.HARDWARE.orEmpty().ifBlank { "unknown" }
    val board = Build.BOARD.orEmpty().ifBlank { "unknown" }
    val romDisplay = Build.DISPLAY.orEmpty().ifBlank { "unknown" }
    val buildId = Build.ID.orEmpty().ifBlank { "unknown" }
    val buildFingerprint = Build.FINGERPRINT.orEmpty().ifBlank { "unknown" }
    val buildType = Build.TYPE.orEmpty().ifBlank { "unknown" }
    val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MANUFACTURER.orEmpty().ifBlank { "unknown" }
    } else {
        "n/a"
    }
    val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.orEmpty().ifBlank { "unknown" }
    } else {
        "n/a"
    }

    return buildString {
        appendLine("Версия приложения: ${BuildConfig.VERSION_NAME}")
        appendLine("Андроид: $androidVersion (SDK $sdkInt)")
        appendLine("Устройство: $manufacturer / $brand / $model")
        appendLine("Код устройства: $device")
        appendLine("Продукт: $product")
        appendLine("ABI: $primaryAbi")
        appendLine("Все ABI: $supportedAbis")
        appendLine("SoC: $socManufacturer / $socModel")
        appendLine("Hardware: $hardware")
        appendLine("Board: $board")
        appendLine("ROM: $romDisplay")
        appendLine("Build ID: $buildId")
        appendLine("Build type: $buildType")
        appendLine("Fingerprint: $buildFingerprint")
    }.trim()
}
