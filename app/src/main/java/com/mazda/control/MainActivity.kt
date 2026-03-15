package com.mazda.control

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mazda.control.ui.theme.MazdaControlTheme
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    // Новый контроллер для AG35TspClient (172.16.2.30:50001)
    private lateinit var tBoxController: TBoxSpoilerController

    // Диагностика сети без root
    private lateinit var networkDiagnostics: NetworkDiagnostics

    // Mock-контроллер для тестирования
    private val mockController = MockSpoilerController()

    private val logMessages = mutableStateListOf<String>()
    private val responseMessages = mutableStateListOf<String>()
    private lateinit var logFile: File

    // По умолчанию используем TBox (реальный сервер)
    private var isMockMode = false
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализируем контроллер с контекстом приложения
        tBoxController = TBoxSpoilerController(applicationContext)

        // Инициализируем диагностику сети
        networkDiagnostics = NetworkDiagnostics(applicationContext)

        // Initialize log file - сохраняем во внутреннюю директорию приложения
        // Это работает на Android 11+ без root прав
        logFile = File(applicationContext.filesDir, "mazda_log_${getCurrentTimestamp()}.txt")
        log("=== Сессия началась ===")
        log("Файл лога: ${logFile.absolutePath}")
        log("Режим: ${if (isMockMode) "TEST (MOCK)" else "REAL AG35TspClient"}")
        log("📁 Для извлечения лога: adb pull ${logFile.absolutePath}")
        log("📱 Или через приложение: кнопка '💾 Сохранить лог'")

        // Подписка на ответы от сервера AG35TspClient
        tBoxController.onServerResponse = { response ->
            val uiMessage = response.toUiString()
            log("📥 ОТВЕТ AG35: $uiMessage")
            responseMessages.add(uiMessage)
        }

        // Подписка на изменение подключения
        tBoxController.onConnectionStateChanged = { connected ->
            mainHandler.post {
                isConnected = connected
                if (connected) {
                    log("✅ AG35TspClient: Подключение установлено")
                } else {
                    log("❌ AG35TspClient: Подключение разорвано")
                }
            }
        }

        // Подключаем контроллеры
        connectControllers()

        setContent {
            MazdaControlTheme {
                SpoilerScreen(
                    logMessages = logMessages,
                    responseMessages = responseMessages,
                    logFilePath = logFile.absolutePath,
                    isSpoilerOpen = if (isMockMode) mockController.isSpoilerOpen() else tBoxController.isSpoilerOpen(),
                    isMoving = if (isMockMode) mockController.isMoving() else tBoxController.isMoving(),
                    isMockMode = isMockMode,
                    isConnected = isConnected,
                    onOpenClick = {
                        log("📤 Команда: Спойлер ОТКРЫТЬ")
                        log("   Режим: ${if (isMockMode) "MOCK" else "AG35TspClient"}")
                        if (isMockMode) {
                            mockController.open()
                            log("   Статус: Отправлено в MockController")
                        } else {
                            tBoxController.open()
                            log("   Статус: Отправлено в TBoxSpoilerController (AG35 протокол)")
                        }
                    },
                    onCloseClick = {
                        log("📤 Команда: Спойлер ЗАКРЫТЬ")
                        log("   Режим: ${if (isMockMode) "MOCK" else "AG35TspClient"}")
                        if (isMockMode) {
                            mockController.close()
                            log("   Статус: Отправлено в MockController")
                        } else {
                            tBoxController.close()
                            log("   Статус: Отправлено в TBoxSpoilerController (AG35 протокол)")
                        }
                    },
                    onShareLogClick = {
                        log("💾 Лог сохранён в: ${logFile.absolutePath}")
                        shareLogFile()
                    },
                    onToggleModeClick = {
                        isMockMode = !isMockMode
                        log("🔄 Режим переключен: ${if (isMockMode) "TEST (MOCK)" else "REAL AG35TspClient"}")
                        connectControllers()
                    },
                    onNetworkDiagnosticsClick = {
                        onNetworkDiagnosticsClick()
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
                    log("🚗 REAL MODE: Подключение к AG35TspClient...")
                    log("🔌 Server: 172.16.2.30:50001 (TCP)")
                    log("📋 Protocol: AG35TspClient (14-byte header + body + CRC16)")
                    val result = tBoxController.connect()
                    mainHandler.post {
                        if (result) {
                            log("✅ AG35TspClient: Успешное подключение к 172.16.2.30:50001")
                            isConnected = true
                        } else {
                            log("❌ AG35TspClient: Не удалось подключиться")
                            log("⚠️ Проверьте:")
                            log("  1. Доступность сервера 172.16.2.30:50001")
                            log("  2. Настройки сети (WiFi/сеть автомобиля)")
                            log("  3. Брандмауэр/безопасность")
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

    /**
     * Диагностика сети без root прав
     * Проверка доступности сервера 172.16.2.30:50001
     */
    private fun onNetworkDiagnosticsClick() {
        log("🔍 ЗАПУСК ДИАГНОСТИКИ СЕТИ...")
        
        Executors.newSingleThreadExecutor().execute {
            try {
                // Проверяем сервер 172.16.2.30:50001
                val report = networkDiagnostics.diagnoseServer("172.16.2.30", 50001)
                
                mainHandler.post {
                    log("📊 ${report.toUiString()}")
                    responseMessages.add(report.toUiString())
                }
            } catch (e: Exception) {
                mainHandler.post {
                    log("❌ Ошибка диагностики: ${e.message}")
                }
            }
        }
    }

    /**
     * Поделиться файлом лога через Intent
     * Работает без root-прав через FileProvider
     */
    private fun shareLogFile() {
        try {
            // Создаём URI для файла через FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                logFile
            )

            // Создаём Intent для шеринга
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MazdaControl Log")
                putExtra(Intent.EXTRA_TEXT, "Лог управления спойлером\nФайл: ${logFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Запускаем chooser
            val chooserIntent = Intent.createChooser(shareIntent, "Поделиться логом")
            startActivity(chooserIntent)

            log("✅ Лог отправлен через шеринг")
        } catch (e: Exception) {
            log("❌ Ошибка шеринга: ${e.message}")
            Log.e("MainActivity", "Share log error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tBoxController.disconnect()
        mockController.disconnect()
        log("🔌 Отключено")
        log("=== Сессия завершена ===")
    }
}

@Composable
fun SpoilerScreen(
    logMessages: List<String>,
    responseMessages: List<String>,
    logFilePath: String,
    isSpoilerOpen: Boolean,
    isMoving: Boolean,
    isMockMode: Boolean,
    isConnected: Boolean,
    onOpenClick: () -> Unit,
    onCloseClick: () -> Unit,
    onShareLogClick: () -> Unit,
    onToggleModeClick: () -> Unit,
    onNetworkDiagnosticsClick: () -> Unit,
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
                    text = if (isMockMode) "🔧 TEST MODE" else "🚗 AG35TspClient",
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
                    text = "⚠️ НЕТ ПОДКЛЮЧЕНИЯ К AG35TspClient",
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

        // Кнопка диагностики сети
        OutlinedButton(
            onClick = onNetworkDiagnosticsClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "🔍 Диагностика сети (172.16.2.30:50001)",
                fontSize = 14.sp
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

        // Ответы от сервера (только для REAL MODE)
        if (!isMockMode && responseMessages.isNotEmpty()) {
            Text(
                text = "Ответы от AG35TspClient:",
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .background(Color(0xFFE3F2FD).copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    responseMessages.takeLast(10).forEach { message ->
                        Text(
                            text = message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Log header
        Text(
            text = "Лог событий:",
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 8.dp)
        )

        // Log view - уменьшенная высота на 100px снизу для удобства на автомобиле
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)  // Фиксированная высота вместо fillMaxSize()
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
