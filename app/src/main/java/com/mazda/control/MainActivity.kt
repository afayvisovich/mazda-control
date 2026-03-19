package com.mazda.control

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mazda.control.mock.MockMegaController
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SHIZUKU_REQUEST_CODE = 999
    }

    private var showLogs by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем TestMode
        TestMode.init(this)

        // Запускаем Mock-сервис если в Mock Mode
        if (TestMode.isMockMode()) {
            startMockService()
            // MockMegaController инициализируется в TestMode.init()
        } else {
            // В Real Mode проверяем права Shizuku
            Log.d(TAG, "🔍 Real Mode: Checking Shizuku...")
            ShizukuBinderChecker.diagnose()
            checkShizukuPermission()
        }

        // Запускаем чтение логов
        LogcatManager.start()

        Log.d(TAG, "🚀 MainActivity started")

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        showLogs = showLogs,
                        onToggleLogs = { showLogs = !showLogs },
                        onRequestShizukuPermission = { checkShizukuPermission() }
                    )
                }
            }
        }
    }

    /**
     * Запустить MockMegaService
     */
    private fun startMockService() {
        try {
            val intent = Intent(this, MockMegaService::class.java)
            startService(intent)
            Log.i(TAG, "🚀 MockMegaService started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start MockMegaService", e)
        }
    }

    /**
     * Проверка и запрос прав Shizuku
     */
    private fun checkShizukuPermission() {
        // Проверяем с задержкой - Shizuku может быть еще не готов
        mainHandler.postDelayed({
            if (!ShizukuIntegrationHelper.hasShizukuPermission()) {
                Log.w(TAG, "⚠️ Shizuku permission not granted, requesting...")
                requestShizukuPermission()
            } else {
                Log.d(TAG, "✅ Shizuku permission already granted")
            }
        }, 1000) // Задержка 1 секунду для инициализации Shizuku
    }

    /**
     * Запрос прав Shizuku
     */
    private fun requestShizukuPermission() {
        try {
            // Проверяем с повторными попытками
            var attempts = 0
            val maxAttempts = 5
            
            while (attempts < maxAttempts) {
                if (Shizuku.pingBinder()) {
                    val permissionGranted = ShizukuIntegrationHelper.hasShizukuPermission()
                    Log.d(TAG, "🔐 Shizuku permission granted: $permissionGranted (attempt ${attempts + 1})")
                    
                    if (!permissionGranted) {
                        // Показываем диалог активации
                        Log.w(TAG, "⚠️ App not authorized in Shizuku. Opening activation screen...")
                        val intent = Intent(this, ShizukuActivationActivity::class.java)
                        startActivity(intent)
                    } else {
                        Log.d(TAG, "✅ Shizuku authorized and ready!")
                    }
                    return
                } else {
                    attempts++
                    if (attempts < maxAttempts) {
                        Log.w(TAG, "⏳ Shizuku not ready, retrying... ($attempts/$maxAttempts)")
                        Thread.sleep(500)
                    }
                }
            }
            
            Log.e(TAG, "❌ Shizuku not running after $maxAttempts attempts")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error requesting Shizuku permission", e)
        }
    }

    /**
     * Остановить MockMegaService
     */
    private fun stopMockService() {
        try {
            val intent = Intent(this, MockMegaService::class.java)
            stopService(intent)
            Log.i(TAG, "🛑 MockMegaService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop MockMegaService", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Останавливаем чтение логов
        LogcatManager.stop()

        // Останавливаем Mock-сервис при выходе
        if (TestMode.isMockMode()) {
            stopMockService()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    showLogs: Boolean,
    onToggleLogs: () -> Unit,
    onRequestShizukuPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    var showShizukuDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mazda Control") },
                actions = {
                    TextButton(onClick = onToggleLogs) {
                        Text(
                            text = if (showLogs) "🚗 Test" else "📋 Logs",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    IconButton(onClick = { 
                        val status = ShizukuIntegrationHelper.getShizukuStatus(context)
                        if (status is ShizukuIntegrationHelper.ShizukuStatus.NotAuthorized) {
                            showShizukuDialog = true
                        } else {
                            ShizukuIntegrationHelper.checkAndActivate(context)
                        }
                    }) {
                        Text(
                            text = "🔧",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showLogs) {
                LogScreenWrapper()
            } else {
                SpoilerTestScreen(
                    onRequestShizukuPermission = onRequestShizukuPermission
                )
            }
        }
        
        // Диалог для неавторизованного Shizuku
        if (showShizukuDialog) {
            AlertDialog(
                onDismissRequest = { showShizukuDialog = false },
                title = { Text("⚠️ Shizuku не авторизован") },
                text = { 
                    Text("Ваше приложение не имеет разрешения Shizuku. Откройте настройки Shizuku и разрешите приложению доступ в разделе 'Authorized apps'.") 
                },
                confirmButton = {
                    TextButton(onClick = {
                        ShizukuIntegrationHelper.checkAndActivate(context)
                        showShizukuDialog = false
                    }) {
                        Text("Открыть настройки")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShizukuDialog = false }) {
                        Text("Позже")
                    }
                }
            )
        }
    }
}

@Composable
fun LogScreenWrapper() {
    val logs by LogcatManager.logs.collectAsState()
    
    DebugLogScreen(
        logs = logs,
        onClear = { LogcatManager.clear() },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun SpoilerTestScreen(
    onRequestShizukuPermission: () -> Unit = {}
) {
    var modeText by remember { mutableStateOf("🎭 MOCK") }
    var spoilerStatus by remember { mutableStateOf("CLOSED") }
    var currentSpoilerMode by remember { mutableStateOf(TestMode.getSpoilerPropertyMode()) }
    val logTag = "MainActivity"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Mazda Control - Spoiler Test",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Одна кнопка переключения режима
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (modeText == "🎭 MOCK")
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Mode: $modeText",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val newMock = TestMode.toggle()
                        modeText = if (newMock) "🎭 MOCK" else "🚗 REAL"
                        Log.d(logTag, "🔄 Mode toggled: $modeText")
                        spoilerStatus = "CHANGED MODE"

                        // Если переключились в Real Mode, проверяем Shizuku
                        if (!newMock) {
                            onRequestShizukuPermission()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Switch Mode")
                }
            }
        }

        // Переключатель Spoiler Property Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🔧 Spoiler Property Mode",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Статус Shizuku в Real Mode
                if (!TestMode.isMockMode()) {
                    val shizukuAuthorized = ShizukuIntegrationHelper.hasShizukuPermission()
                    val shizukuRunning = Shizuku.pingBinder()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (shizukuRunning) "✅ Shizuku running" else "❌ Shizuku not running",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuRunning) Color.Green else Color.Red
                        )
                        
                        Text(
                            text = if (shizukuAuthorized) "✅ Authorized" else "⚠️ Not authorized",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuAuthorized) Color.Green else Color(0xFFFFA000)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Current: ${currentSpoilerMode.propName}\n${currentSpoilerMode.description}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            TestMode.setSpoilerPropertyMode(SpoilerPropertyMode.VARIANT_1)
                            currentSpoilerMode = SpoilerPropertyMode.VARIANT_1
                            Log.d(logTag, "🔄 Spoiler Mode: VARIANT_1 (0x660000c3)")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !TestMode.isMockMode()
                    ) {
                        Text("1️⃣")
                    }

                    Button(
                        onClick = {
                            TestMode.setSpoilerPropertyMode(SpoilerPropertyMode.VARIANT_2)
                            currentSpoilerMode = SpoilerPropertyMode.VARIANT_2
                            Log.d(logTag, "🔄 Spoiler Mode: VARIANT_2 (0x38)")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !TestMode.isMockMode()
                    ) {
                        Text("2️⃣")
                    }

                    Button(
                        onClick = {
                            TestMode.setSpoilerPropertyMode(SpoilerPropertyMode.VARIANT_3)
                            currentSpoilerMode = SpoilerPropertyMode.VARIANT_3
                            Log.d(logTag, "🔄 Spoiler Mode: VARIANT_3 (0x6600022C)")
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !TestMode.isMockMode()
                    ) {
                        Text("3️⃣")
                    }
                }

                if (TestMode.isMockMode()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ В Mock Mode переключение недоступно",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Контроль спойлера
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🚗 Spoiler Control",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Status: $spoilerStatus",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            Log.d(logTag, "🔓 Opening spoiler...")
                            val controller = TestMode.getController()
                            val success = controller.setSpoiler(1)
                            spoilerStatus = if (success) "OPEN" else "FAILED"
                            Log.d(logTag, "🏁 Spoiler OPEN: $success")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔓 OPEN")
                    }

                    Button(
                        onClick = {
                            Log.d(logTag, "🔒 Closing spoiler...")
                            val controller = TestMode.getController()
                            val success = controller.setSpoiler(0)
                            spoilerStatus = if (success) "CLOSED" else "FAILED"
                            Log.d(logTag, "🏁 Spoiler CLOSE: $success")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔒 CLOSE")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        Log.d(logTag, "✋ Stopping spoiler...")
                        val controller = TestMode.getController()
                        val success = controller.setSpoiler(2)
                        spoilerStatus = if (success) "STOPPED" else "FAILED"
                        Log.d(logTag, "🏁 Spoiler STOP: $success")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✋ STOP")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📊 Statistics:",
                    style = MaterialTheme.typography.bodyMedium
                )

                val controller = TestMode.getController()
                val stats = controller.getStats()

                stats.forEach { (key, value) ->
                    Text(
                        text = "  $key: $value",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
