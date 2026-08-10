package com.wdtt.client.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wdtt.client.ui.components.verticalScrollEdgeFade

@Composable
fun ImportantInfoDialog(onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScrollEdgeFade(scrollState.canScrollBackward, scrollState.canScrollForward)
                    .verticalScroll(scrollState)
            ) {
                AppDialogHeader(
                    title = "Важная информация",
                    onClose = onDismiss,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(Modifier.height(16.dp))

                InfoSection("Капча ВК",
                    "По умолчанию в приложении установлен ручной режим (WBV + РУЧ), но его можно заменить на RJS-АВТ. Это продвинутый автоматический метод решения капчи без всплывающих окон и участия человека, основанный на реверс-инжиниринге JS-кода капчи. Он имитирует действия пользователя в фоновом режиме, обеспечивая бесперебойную работу.\n\nВАЖНО: Если в вашем случае RJS не проходит капчу или выдает ошибки (проблемы со связью или изменения на стороне ВК) — переключитесь обратно в ручной режим."
                )
                InfoSection("Как решать капчу",
                    "Она не сложная: нужно просто потянуть слайдер вправо так, чтобы все элементы (обычно это 3 слова) идеально сошлись в пазле."
                )
                InfoSection("Сетевое окружение",
                    "Отключите другие VPN/Прокси и «Приватный DNS» перед использованием."
                )
                InfoSection("Связь потоков и капч",
                    "Рекомендую выбирать 12-36 потока для меньшего количества капч. Если вам всё равно на частоту ввода капчи в фоне — ставьте 48 и более ради скорости."
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Понятно")
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
}
