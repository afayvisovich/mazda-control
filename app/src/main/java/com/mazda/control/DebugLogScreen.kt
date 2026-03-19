package com.mazda.control

import android.util.Log
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран отладки с логами в реальном времени
 */
@Composable
fun DebugLogScreen(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Автопрокрутка к последнему логy
    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
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
                text = "📋 System Logs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка автоскролла
                OutlinedButton(
                    onClick = { autoScroll = !autoScroll },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(if (autoScroll) "⬇️ Auto" else "⏸️ Paused")
                }
                
                OutlinedButton(
                    onClick = onClear,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🗑 Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Статистика
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LogStatItem("Total", logs.size.toString())
                LogStatItem("Errors", logs.count { it.level == Log.ERROR }.toString())
                LogStatItem("Warnings", logs.count { it.level == Log.WARN }.toString())
                LogStatItem("Info", logs.count { it.level == Log.INFO }.toString())
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Список логов с кнопкой "вверх"
        Box(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
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
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = Color.DarkGray.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            
            // Кнопка "вверх" (показывается когда не в конце списка)
            if (!autoScroll && listState.firstVisibleItemIndex > 10) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                            autoScroll = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("⬆️")
                }
            }
        }
    }
}

@Composable
private fun LogStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogItem(logEntry: LogEntry) {
    val bgColor = when (logEntry.level) {
        android.util.Log.ERROR -> Color(0x40FF0000)  // Красный с прозрачностью
        android.util.Log.WARN -> Color(0x40FFA500)   // Оранжевый
        android.util.Log.INFO -> Color(0x4000FF00)   // Зелёный
        android.util.Log.DEBUG -> Color(0x400000FF)  // Синий
        else -> Color.Transparent
    }
    
    val textColor = when (logEntry.level) {
        android.util.Log.ERROR -> Color(0xFFFF6B6B)
        android.util.Log.WARN -> Color(0xFFFFD93D)
        android.util.Log.INFO -> Color(0xFF6BCB77)
        android.util.Log.DEBUG -> Color(0xFF4D96FF)
        else -> Color.White
    }
    
    val levelPrefix = when (logEntry.level) {
        android.util.Log.ERROR -> "❌"
        android.util.Log.WARN -> "⚠️"
        android.util.Log.INFO -> "ℹ️"
        android.util.Log.DEBUG -> "🔍"
        else -> "•"
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = logEntry.timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.width(70.dp)
        )
        
        Text(
            text = levelPrefix,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(24.dp)
        )
        
        Text(
            text = logEntry.tag,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Cyan,
            fontSize = 10.sp,
            modifier = Modifier.width(120.dp)
        )
        
        Text(
            text = logEntry.message,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Модель записи лога
 */
data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: Int,
    val tag: String,
    val message: String
) {
    companion object {
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        fun fromLogLine(logLine: String, id: Long): LogEntry? {
            // Формат: 03-19 00:41:53.331 D/TestMode( 2946): 🚀 TestMode initialized
            // Или: 03-19 01:02:26.937  3703  3751 D LogcatManager: 📋 Logcat reading started
            // Пробуем два формата
            
            // Формат 1: с PID в скобках
            val regex1 = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWE])/([\w.]+)\(\s*\d+\):\s+(.*)""")
            val match1 = regex1.find(logLine)
            
            if (match1 != null) {
                val (time, levelChar, tag, msg) = match1.destructured
                val level = parseLevel(levelChar)
                return LogEntry(
                    id = id,
                    timestamp = time.substring(11),
                    level = level,
                    tag = tag,
                    message = msg
                )
            }
            
            // Формат 2: без PID
            val regex2 = Regex("""(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWE])\s+([\w.]+):\s+(.*)""")
            val match2 = regex2.find(logLine)
            
            if (match2 != null) {
                val (time, levelChar, tag, msg) = match2.destructured
                val level = parseLevel(levelChar)
                return LogEntry(
                    id = id,
                    timestamp = time.substring(11),
                    level = level,
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
