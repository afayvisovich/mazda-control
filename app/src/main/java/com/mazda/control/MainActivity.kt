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

    // Новый контроллер для Fake32960Server (127.0.0.1:32960)
    private lateinit var tBoxController: TBoxSpoilerController

    // Диагностика сети без root
    private lateinit var networkDiagnostics: NetworkDiagnostics

    // Mock-контроллер для тестирования
    private val mockController = MockSpoilerController()

    private val logMessages = mutableStateListOf<String>()
    private val responseMessages = mutableStateListOf<String>()
    private lateinit var logFile: File

    // По умолчанию используем TBox (реальный сервер 127.0.0.1:32960)
    private var isMockMode by mutableStateOf(false)
    private var isConnected by mutableStateOf(false)
    private var isConnecting by mutableStateOf(false)  // Флаг процесса подключения
    private var isServicesListExpanded by mutableStateOf(true)  // Состояние сворачивания списка сервисов
    
    // Найденные TBox сервисы
    private val tBoxServices = mutableStateListOf<TBoxServiceInfo>()
    private var selectedService: TBoxServiceInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Инициализируем контроллер с контекстом приложения
        tBoxController = TBoxSpoilerController(applicationContext)

        // Инициализируем диагностику сети с логгером
        networkDiagnostics = NetworkDiagnostics(applicationContext) { message ->
            log("[Network] $message")
        }

        // Initialize log file - один файл на день
        // Для удобного извлечения без ADB
        val logDir = File("/sdcard/Download/MazdaControl")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "mazda_log_${getCurrentDate()}.txt")
        log("=== Сессия началась ===")
        log("Файл лога: ${logFile.absolutePath}")
        log("Режим: ${if (isMockMode) "TEST (MOCK)" else "REAL (127.0.0.1:32960)"}")
        log("📁 Лог доступен в: /sdcard/Download/MazdaControl/")
        log("📱 Или через приложение: кнопка '💾 Сохранить лог'")

        // Подписка на ответы от сервера Fake32960Server
        tBoxController.onServerResponse = { response ->
            val uiMessage = response.toUiString()
            log("📥 ОТВЕТ: $uiMessage")
            responseMessages.add(uiMessage)
        }

        // Подписка на изменение подключения
        tBoxController.onConnectionStateChanged = { connected ->
            mainHandler.post {
                isConnecting = false  // Завершаем процесс подключения
                isConnected = connected
                if (connected) {
                    log("✅ Fake32960Server: Подключение установлено")
                } else {
                    log("❌ Fake32960Server: Подключение разорвано или не удалось")
                    log("⚠️ Проверьте:")
                    log("  1. Доступность сервера 127.0.0.1:32960")
                    log("  2. Запущен ли Fake32960Server")
                    log("  3. Логи в /sdcard/Download/MazdaControl/")
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
                    isConnecting = isConnecting,
                    isServicesListExpanded = isServicesListExpanded,
                    tBoxServices = tBoxServices,
                    onOpenClick = {
                        log("📤 Команда: Спойлер ОТКРЫТЬ")
                        log("   Режим: ${if (isMockMode) "MOCK" else "Fake32960Server"}")
                        if (isMockMode) {
                            mockController.open()
                            log("   Статус: Отправлено в MockController")
                        } else {
                            tBoxController.open()
                            log("   Статус: Отправлено в TBoxSpoilerController (протокол)")
                        }
                    },
                    onCloseClick = {
                        log("📤 Команда: Спойлер ЗАКРЫТЬ")
                        log("   Режим: ${if (isMockMode) "MOCK" else "Fake32960Server"}")
                        if (isMockMode) {
                            mockController.close()
                            log("   Статус: Отправлено в MockController")
                        } else {
                            tBoxController.close()
                            log("   Статус: Отправлено в TBoxSpoilerController (протокол)")
                        }
                    },
                    onShareLogClick = {
                        log("💾 Лог сохранён в: ${logFile.absolutePath}")
                        shareLogFile()
                    },
                    onToggleModeClick = {
                        isMockMode = !isMockMode
                        log("🔄 Режим переключен: ${if (isMockMode) "TEST (MOCK)" else "REAL Fake32960Server"}")
                        connectControllers()
                    },
                    onNetworkDiagnosticsClick = {
                        onNetworkDiagnosticsClick()
                    },
                    onScanNetworkClick = {
                        onScanNetworkClick()
                    },
                    onServiceClick = { service ->
                        log("🔌 Выбор сервиса: ${service.host}:${service.port}")
                        selectedService = service
                        testServiceProtocol(service)
                    },
                    onToggleServicesListClick = {
                        isServicesListExpanded = !isServicesListExpanded
                        log("🔄 Список сервисов: ${if (isServicesListExpanded) "развернут" else "свернут"}")
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
                if (isMockMode) {
                    log("🔧 MOCK MODE: Подключение к эмулятору...")
                    val result = mockController.connect()
                    mainHandler.post {
                        isConnecting = false
                        if (result) {
                            log("✅ MOCK: Тестовое подключение к 127.0.0.1:32960")
                        } else {
                            log("ℹ️ MOCK: Режим эмуляции (сервер недоступен)")
                        }
                        isConnected = true
                    }
                } else {
                    log("🚗 REAL MODE: Подключение к Fake32960Server...")
                    log("🔌 Server: 127.0.0.1:32960 (TCP)")
                    log("📋 Protocol: Fake32960Server (30-byte header + body + CRC16)")
                    
                    // Устанавливаем флаг подключения
                    mainHandler.post {
                        isConnecting = true
                        isConnected = false
                    }
                    
                    // Подключение через контроллер
                    // onConnectionStateChanged callback установит isConnected
                    val connectStarted = System.currentTimeMillis()
                    log("⏱️ Начало подключения: ${connectStarted}")
                    tBoxController.connect()
                    
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isConnecting = false
                    isConnected = false
                    log("❌ REAL: Критическая ошибка подключения: ${e.message}")
                    log("📋 Exception: ${e.javaClass.simpleName}")
                    log("📋 StackTrace: ${e.stackTraceToString().take(500)}")
                }
            }
        }
    }

    /**
     * Получить текущую дату для имени файла лога
     * Формат: YYYYMMDD (один файл на день)
     */
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }

    /**
     * Получить текущее время для записи в лог
     * Формат: ЧЧ:ММ:СС.мс
     */
    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    private fun log(message: String) {
        val timestamp = getCurrentTimestamp()
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
     * Проверка доступности сервера 127.0.0.1:32960 (Fake32960Server)
     */
    private fun onNetworkDiagnosticsClick() {
        log("🔍 ЗАПУСК ДИАГНОСТИКИ СЕТИ...")

        Executors.newSingleThreadExecutor().execute {
            try {
                // Проверяем сервер 127.0.0.1:32960
                val report = networkDiagnostics.diagnoseServer("127.0.0.1", 32960)

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
     * Сканирование сети для поиска реальных TBox сервисов
     * Сканирует подсети и порты, ищет открытые сервисы
     */
    private fun onScanNetworkClick() {
        log("🌐 ЗАПУСК СКАНИРОВАНИЯ СЕТИ (поиск TBox)...")
        log("⏱️ Это может занять до 30 секунд...")

        Executors.newSingleThreadExecutor().execute {
            try {
                mainHandler.post {
                    responseMessages.clear()
                    responseMessages.add("🔍 Сканирование сети...")
                    tBoxServices.clear()
                    selectedService = null
                }

                // Запуск сканирования
                val services = networkDiagnostics.scanForTBoxServices()

                mainHandler.post {
                    if (services.isEmpty()) {
                        log("❌ TBox сервисы не найдены")
                        responseMessages.add("❌ TBox сервисы не найдены")
                        responseMessages.add("💡 Попробуйте запустить оригинальное приложение и повторить сканирование")
                    } else {
                        log("✅ Найдено сервисов: ${services.size}")
                        responseMessages.add("✅ Найдено TBox сервисов: ${services.size}")
                        
                        // Добавляем в список для UI
                        tBoxServices.addAll(services)
                        
                        services.forEach { service ->
                            val serviceInfo = service.toUiString()
                            log("  $serviceInfo")
                            responseMessages.add(serviceInfo)
                        }
                        
                        responseMessages.add("")
                        responseMessages.add("💡 Нажмите на сервис в списке для тестирования протокола")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    log("❌ Ошибка сканирования: ${e.message}")
                    responseMessages.add("❌ Ошибка сканирования: ${e.message}")
                }
            }
        }
    }

    /**
     * Тестирование протокола на выбранном сервисе
     */
    private fun testServiceProtocol(service: TBoxServiceInfo) {
        log("🔌 Тестирование протокола: ${service.host}:${service.port}")
        
        Executors.newSingleThreadExecutor().execute {
            try {
                mainHandler.post {
                    responseMessages.add("🔌 Подключение к ${service.host}:${service.port}...")
                }
                
                // Тестовое подключение
                val connectResult = TBoxProtocolTester.testService(service.host, service.port)
                
                mainHandler.post {
                    val resultText = buildString {
                        appendLine("📊 Результаты тестирования:")
                        appendLine("  Подключение: ${if (connectResult.connected) "✅" else "❌"}")
                        appendLine("  Ответ сервера: ${if (connectResult.responded) "✅" else "❌"}")
                        if (connectResult.responseBytes != null) {
                            appendLine("  Ответ: ${connectResult.responseBytes.toHex()}")
                            
                            // Проверка формата
                            if (connectResult.responseBytes.size >= 2) {
                                val magic0 = connectResult.responseBytes[0]
                                val magic1 = connectResult.responseBytes[1]
                                appendLine("  Magic bytes: 0x${String.format("%02X", magic0)} 0x${String.format("%02X", magic1)}")
                                
                                if (magic0 == 0x23.toByte() && magic1 == 0x23.toByte()) {
                                    appendLine("  ✅ ФОРМАТ Fake32960Server (0x23 0x23)!")
                                } else if (magic0 == 0x5A.toByte()) {
                                    appendLine("  ⚠️ Старый формат (0x5A)")
                                } else {
                                    appendLine("  ❌ Неизвестный формат")
                                }
                            }
                        }
                        if (connectResult.errorMessage != null) {
                            appendLine("  Ошибка: ${connectResult.errorMessage}")
                        }
                    }
                    
                    log(resultText)
                    responseMessages.add(resultText)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    log("❌ Ошибка тестирования: ${e.message}")
                    responseMessages.add("❌ Ошибка тестирования: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Отправить тестовую команду (спойлер open) на выбранный сервис
     */
    private fun sendTestCommandToService(service: TBoxServiceInfo) {
        log("📤 Отправка тестовой команды на ${service.host}:${service.port}")
        
        Executors.newSingleThreadExecutor().execute {
            try {
                // Создаём тестовый пакет через PacketGenerator
                val testPacket = PacketGenerator.createSpoilerOpenPacket()
                
                mainHandler.post {
                    responseMessages.add("📤 Отправка команды OPEN (${testPacket.size} байт)...")
                }
                
                val result = TBoxProtocolTester.sendTestCommand(
                    host = service.host,
                    port = service.port,
                    command = testPacket
                )
                
                mainHandler.post {
                    val resultText = result.toUiString()
                    log(resultText)
                    responseMessages.add(resultText)
                    
                    if (result.success && result.hasExpectedFormat) {
                        log("✅ СЕРВИС РАБОТАЕТ! Можно использовать для управления.")
                        responseMessages.add("✅ СЕРВИС РАБОТАЕТ! Можно использовать для управления.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    log("❌ Ошибка отправки команды: ${e.message}")
                    responseMessages.add("❌ Ошибка отправки команды: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Extension function для ByteArray -> Hex
     */
    private fun ByteArray.toHex(): String {
        return joinToString(" ") { String.format("%02X", it) }
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
    isConnecting: Boolean,  // Флаг процесса подключения
    isServicesListExpanded: Boolean,  // Состояние сворачивания списка сервисов
    tBoxServices: List<TBoxServiceInfo>,
    onOpenClick: () -> Unit,
    onCloseClick: () -> Unit,
    onShareLogClick: () -> Unit,
    onToggleModeClick: () -> Unit,
    onNetworkDiagnosticsClick: () -> Unit,
    onScanNetworkClick: () -> Unit,
    onServiceClick: (TBoxServiceInfo) -> Unit,
    onToggleServicesListClick: () -> Unit,  // Callback для сворачивания списка
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
                    text = if (isMockMode) "🔧 TEST MODE" else "🚗 Fake32960Server",
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
        if (!isMockMode) {
            val connectionStatusText = when {
                isConnecting -> "⏳ ПОДКЛЮЧЕНИЕ К Fake32960Server..."
                isConnected -> "✅ Fake32960Server: ПОДКЛЮЧЕНО"
                else -> "⚠️ НЕТ ПОДКЛЮЧЕНИЯ К Fake32960Server"
            }
            
            val connectionStatusColor = when {
                isConnecting -> MaterialTheme.colorScheme.primaryContainer
                isConnected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.errorContainer
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                color = connectionStatusColor,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = connectionStatusText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    fontSize = 14.sp,
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
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

        // Buttons row - always enabled for POC (no service dependency)
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
                enabled = !isSpoilerOpen && !isMoving
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
                enabled = isSpoilerOpen && !isMoving
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
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "🔍 Диагностика сети (127.0.0.1:32960)",
                fontSize = 14.sp
            )
        }

        // Кнопка сканирования сети (поиск TBox сервисов)
        OutlinedButton(
            onClick = onScanNetworkClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = "🌐 Сканирование сети (поиск TBox)",
                fontSize = 14.sp
            )
        }

        // Список найденных TBox сервисов (сворачиваемый, scrollable)
        if (tBoxServices.isNotEmpty()) {
            val toggleIcon = if (isServicesListExpanded) "📂" else "📁"
            val toggleText = if (isServicesListExpanded) "Свернуть" else "Развернуть"
            val listCount = tBoxServices.size
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Заголовок с кнопкой сворачивания
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = toggleIcon,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Найдено TBox сервисов: $listCount",
                                fontSize = 16.sp,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        
                        TextButton(
                            onClick = onToggleServicesListClick,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = toggleText,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    // Свертываемый список
                    if (isServicesListExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            // Scrollable список с ограничением высоты
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),  // Максимальная высота
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())  // ← Прокрутка
                                        .padding(8.dp)
                                ) {
                                    tBoxServices.forEach { service ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                            onClick = { onServiceClick(service) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "${service.host}:${service.port}",
                                                        fontSize = 16.sp,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = "Подсеть: ${service.subnet}.x",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Text(
                                                    text = "🔌 Тест",
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                text = "💡 Нажмите на сервис для тестирования протокола",
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
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
                text = "Ответы от Fake32960Server:",
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
