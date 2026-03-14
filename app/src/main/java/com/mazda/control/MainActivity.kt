package com.mazda.control

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazda.control.ui.theme.MazdaControlTheme
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val controller = SpoilerController()
    private val mockController = MockSpoilerController()
    private val logMessages = mutableStateListOf<String>()
    private lateinit var logFile: File
    private var isMockMode = true // По умолчанию mock-режим для тестирования
    private var isConnected = true // Статус подключения (для REAL MODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize log file - сохраняем в /data/local/tmp/
        logFile = File("/data/local/tmp/").apply {
            if (!exists()) mkdirs()
        }.let { dir ->
            File(dir, "log_${getCurrentTimestamp()}.txt")
        }
        log("=== Сессия началась ===")
        log("Файл лога: ${logFile.absolutePath}")
        log("Режим: ${if (isMockMode) "TEST (MOCK)" else "REAL CAR"}")
        log("📁 Для извлечения лога: adb pull /data/local/tmp/${logFile.name}")

        // Подключаем оба контроллера
        connectControllers()

        setContent {
            MazdaControlTheme {
                SpoilerScreen(
                    logMessages = logMessages,
                    logFilePath = logFile.absolutePath,
                    isSpoilerOpen = if (isMockMode) mockController.isSpoilerOpen() else controller.isSpoilerOpen(),
                    isMoving = if (isMockMode) mockController.isMoving() else controller.isMoving(),
                    isMockMode = isMockMode,
                    isConnected = isConnected,
                    onOpenClick = {
                        log("📤 Команда: Спойлер ОТКРЫТЬ")
                        if (isMockMode) {
                            mockController.open()
                            log("✅ MOCK: Команда эмулирована")
                        } else {
                            controller.open()
                            log("✅ Команда отправлена (512 байт)")
                        }
                    },
                    onCloseClick = {
                        log("📤 Команда: Спойлер ЗАКРЫТЬ")
                        if (isMockMode) {
                            mockController.close()
                            log("✅ MOCK: Команда эмулирована")
                        } else {
                            controller.close()
                            log("✅ Команда отправлена (512 байт)")
                        }
                    },
                    onShareLogClick = {
                        log("💾 Лог сохранён в: ${logFile.absolutePath}")
                        log("📁 Для извлечения: adb pull /data/local/tmp/${logFile.name}")
                        log("⚠️ Требуется root-доступ для записи в /data/local/tmp/")
                    },
                    onToggleModeClick = {
                        isMockMode = !isMockMode
                        log("🔄 Режим переключен: ${if (isMockMode) "TEST (MOCK)" else "REAL CAR"}")
                        connectControllers()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun connectControllers() {
        log("=== CONNECTING CONTROLLERS ===")
        
        executor.execute {
            try {
                val connected = if (isMockMode) {
                    log("🔧 MOCK MODE: Подключение к эмулятору...")
                    val result = mockController.connect()
                    mainHandler.post {
                        if (result) {
                            log("✅ MOCK: Тестовое подключение к 127.0.0.1:32960")
                        } else {
                            log("ℹ️ MOCK: Режим эмуляции (сервер недоступен)")
                        }
                        isConnected = true
                    }
                    result
                } else {
                    log("🚗 REAL MODE: Подключение к автомобилю...")
                    log("🔌 Server: 127.0.0.1:32960 (TCP)")
                    val result = controller.connect()
                    mainHandler.post {
                        if (result) {
                            log("✅ REAL: Успешное подключение к автомобилю")
                            log("📊 Local: ${getSocketInfo()?.get("local") ?: "unknown"}")
                            log("📊 Remote: ${getSocketInfo()?.get("remote") ?: "unknown"}")
                            isConnected = true
                        } else {
                            log("❌ REAL: Не удалось подключиться к автомобилю")
                            log("⚠️ Проверьте:")
                            log("  1. Запущен ли сервер на автомобиле")
                            log("  2. Правильность порта (32960)")
                            log("  3. Настройки ADB forward")
                            log("📋 См. логи в /data/local/tmp/")
                            isConnected = false
                        }
                    }
                    result
                }
            } catch (e: Exception) {
                mainHandler.post {
                    log("❌ REAL: Критическая ошибка подключения: ${e.message}")
                    log("📋 Exception: ${e.javaClass.simpleName}")
                    log("📋 StackTrace: ${e.stackTraceToString().take(500)}")
                    isConnected = false
                }
            }
        }
    }

    /**
     * Получить информацию о сокете из контроллера
     */
    private fun getSocketInfo(): Map<String, String>? {
        return try {
            val socketField = controller.javaClass.getDeclaredField("socket").apply {
                isAccessible = true
            }
            val socket = socketField.get(controller) as? java.net.Socket
            
            if (socket != null && socket.isConnected) {
                mapOf(
                    "local" to "${socket.localAddress.hostAddress}:${socket.localPort}",
                    "remote" to "${socket.inetAddress.hostAddress}:${socket.port}"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to get socket info: ${e.message}")
            null
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        
        // Add to UI log
        logMessages.add(logEntry)
        
        // Write to file
        try {
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(logEntry)
            }
            Log.d("MainActivity", logEntry)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to write to log file: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.disconnect()
        mockController.disconnect()
        log("🔌 Отключено")
        log("=== Сессия завершена ===")
    }
}

@Composable
fun SpoilerScreen(
    logMessages: List<String>,
    logFilePath: String,
    isSpoilerOpen: Boolean,
    isMoving: Boolean,
    isMockMode: Boolean,
    isConnected: Boolean,
    onOpenClick: () -> Unit,
    onCloseClick: () -> Unit,
    onShareLogClick: () -> Unit,
    onToggleModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Управление Спойлером",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Mode indicator
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = if (isMockMode)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMockMode) "🔧 TEST MODE" else "🚗 REAL MODE",
                    fontSize = 14.sp,
                    color = if (isMockMode)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                )

                // Mode toggle button
                FilledTonalButton(
                    onClick = onToggleModeClick,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Переключить",
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Connection status (для REAL MODE)
        if (!isMockMode && !isConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "⚠️ НЕТ ПОДКЛЮЧЕНИЯ К АВТОМОБИЛЮ",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Status indicator
        val statusText = if (isMoving) "ДВИЖЕНИЕ..." else if (isSpoilerOpen) "ОТКРЫТ" else "ЗАКРЫТ"
        val statusColor = when {
            isMoving -> Color(0xFFFFA000) // Amber for moving
            isSpoilerOpen -> Color.Green
            else -> Color.Red
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = statusColor.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Статус: $statusText",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                fontSize = 18.sp,
                color = statusColor
            )
        }

        // Buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onOpenClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = isConnected && !isSpoilerOpen && !isMoving
            ) {
                Text(
                    text = "Открыть",
                    fontSize = 18.sp
                )
            }

            Button(
                onClick = onCloseClick,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = isConnected && isSpoilerOpen && !isMoving
            ) {
                Text(
                    text = "Закрыть",
                    fontSize = 18.sp
                )
            }
        }

        // Export button
        Button(
            onClick = onShareLogClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = "💾 Сохранить лог",
                fontSize = 16.sp
            )
        }

        // Log file path
        Text(
            text = "Файл: ${logFilePath.substringAfterLast('/')}",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        // Log header
        Text(
            text = "Лог событий:",
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        // Log view
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray.copy(alpha = 0.2f)),
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                if (logMessages.isEmpty()) {
                    Text(
                        text = "Нет событий",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                } else {
                    logMessages.forEach { message ->
                        Text(
                            text = message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
