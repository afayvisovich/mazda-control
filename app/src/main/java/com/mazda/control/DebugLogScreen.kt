package com.mazda.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Упрощённый экран отладки с логами
 */
@Composable
fun DebugLogScreen(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Автопрокрутка к последнему логу
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Logs (${logs.size})",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedButton(onClick = onClear) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Список логов
        Card(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 200.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logs) { logEntry ->
                    LogItem(logEntry)
                }
            }
        }
    }
}

@Composable
private fun LogItem(logEntry: LogEntry) {
    val textColor = when (logEntry.level) {
        android.util.Log.ERROR -> Color(0xFFFF6B6B)
        android.util.Log.WARN -> Color(0xFFFFD93D)
        android.util.Log.INFO -> Color(0xFFAAAAAA)
        android.util.Log.DEBUG -> Color(0xFF6B9FFF)
        else -> Color.White
    }

    val levelPrefix = when (logEntry.level) {
        android.util.Log.ERROR -> "E"
        android.util.Log.WARN -> "W"
        android.util.Log.INFO -> "I"
        android.util.Log.DEBUG -> "D"
        else -> "V"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = logEntry.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF666666),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(70.dp)
        )

        Text(
            text = levelPrefix,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )

        Text(
            text = logEntry.tag,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4EC9B0),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(100.dp)
        )

        Text(
            text = logEntry.message,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Модель записи лога
 */
data class LogEntry(
    val timestamp: String,
    val level: Int,
    val tag: String,
    val message: String
) {
    companion object {
        /**
         * Parse log line from `logcat -v time` output
         */
        fun fromLogLine(logLine: String, index: Long): LogEntry? {
            // Формат 1: 03-19 00:41:53.331 D/TestMode( 2946): message
            val regex1 = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWE])/([\w.$]+)(?:\(\s*\d+\))?:\s*(.*)""")
            val match = regex1.find(logLine)

            if (match != null) {
                val (time, levelChar, tag, msg) = match.destructured
                return LogEntry(
                    timestamp = time.substring(11), // Убираем дату, оставляем время
                    level = parseLevel(levelChar),
                    tag = tag,
                    message = msg
                )
            }

            return null
        }

        private fun parseLevel(levelChar: String): Int {
            return when (levelChar) {
                "V" -> android.util.Log.VERBOSE
                "D" -> android.util.Log.DEBUG
                "I" -> android.util.Log.INFO
                "W" -> android.util.Log.WARN
                "E" -> android.util.Log.ERROR
                else -> android.util.Log.DEBUG
            }
        }
    }
}
