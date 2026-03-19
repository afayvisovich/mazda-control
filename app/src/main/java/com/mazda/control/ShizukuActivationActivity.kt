package com.mazda.control

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import rikka.shizuku.Shizuku

/**
 * Activity для активации Shizuku
 * 
 * Показывает пользователю инструкцию по активации Shizuku через ADB
 * и предоставляет возможность скопировать команду активации
 */
class ShizukuActivationActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var shizukuStatus by mutableStateOf<ShizukuIntegrationHelper.ShizukuStatus>(
        ShizukuIntegrationHelper.ShizukuStatus.Dead
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Регистрируем слушателей
        ShizukuIntegrationHelper.registerStatusListeners()

        setContent {
            MaterialTheme {
                ShizukuActivationScreen(
                    status = shizukuStatus,
                    onCopyCommandClick = { copyActivationCommand() },
                    onCheckStatusClick = { checkShizukuStatus() },
                    onOpenShizukuAppClick = { openShizukuApp() },
                    onBackClick = { finish() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Первоначальная проверка статуса
        checkShizukuStatus()
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статус при возврате к Activity
        checkShizukuStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuIntegrationHelper.unregisterStatusListeners()
    }

    /**
     * Проверка текущего статуса Shizuku
     */
    private fun checkShizukuStatus() {
        val status = ShizukuIntegrationHelper.getShizukuStatus(this)
        mainHandler.post {
            shizukuStatus = status
        }
    }

    /**
     * Копирование команды активации в буфер обмена
     */
    private fun copyActivationCommand() {
        val command = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Shizuku Activation Command", command)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "✅ Команда скопирована в буфер обмена", Toast.LENGTH_SHORT).show()
        Log.d("ShizukuActivation", "Command copied: $command")
    }

    /**
     * Открытие приложения Shizuku
     */
    private fun openShizukuApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                startActivity(intent)
                Log.d("ShizukuActivation", "Opened Shizuku app")
            } else {
                Toast.makeText(this, "❌ Shizuku не установлен", Toast.LENGTH_SHORT).show()
                Log.w("ShizukuActivation", "Shizuku app not installed")
            }
        } catch (e: Exception) {
            Log.e("ShizukuActivation", "Failed to open Shizuku app", e)
            Toast.makeText(this, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ShizukuActivationScreen(
    status: ShizukuIntegrationHelper.ShizukuStatus,
    onCopyCommandClick: () -> Unit,
    onCheckStatusClick: () -> Unit,
    onOpenShizukuAppClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "🔧 Активация Shizuku",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Status indicator
        val statusText = when (status) {
            is ShizukuIntegrationHelper.ShizukuStatus.Available -> "✅ Shizuku активен и готов к работе"
            is ShizukuIntegrationHelper.ShizukuStatus.NotInstalled -> "❌ Shizuku не установлен"
            is ShizukuIntegrationHelper.ShizukuStatus.NotAuthorized -> "⚠️ Shizuku требует активации"
            is ShizukuIntegrationHelper.ShizukuStatus.Dead -> "⚠️ Shizuku не запущен"
        }

        val statusColor = when (status) {
            is ShizukuIntegrationHelper.ShizukuStatus.Available -> Color.Green
            else -> Color(0xFFFFA000)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = statusColor.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = statusText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                fontSize = 16.sp,
                color = statusColor
            )
        }

        // Instructions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "📋 Инструкция по активации:",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                StepItem(
                    number = 1,
                    text = "Убедитесь, что на устройстве включена отладка по USB"
                )
                StepItem(
                    number = 2,
                    text = "Подключите устройство к компьютеру через USB"
                )
                StepItem(
                    number = 3,
                    text = "Выполните команду в терминале (ADB):"
                )

                // Command box
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StepItem(
                    number = 4,
                    text = "Проверьте статус: adb shell shizuku status"
                )
            }
        }

        // Buttons
        Button(
            onClick = onCopyCommandClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "📋 Скопировать команду",
                fontSize = 16.sp
            )
        }

        Button(
            onClick = onCheckStatusClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = "🔄 Проверить статус",
                fontSize = 16.sp
            )
        }

        Button(
            onClick = onOpenShizukuAppClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text(
                text = "📱 Открыть приложение Shizuku",
                fontSize = 16.sp
            )
        }

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "← Назад",
                fontSize = 16.sp
            )
        }

        // Additional info
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "💡 Информация:",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                InfoItem(
                    text = "• Shizuku работает без root-прав"
                )
                InfoItem(
                    text = "• Активация требуется после каждой перезагрузки"
                )
                InfoItem(
                    text = "• Можно настроить автозапуск через ShizukuAutoStarter"
                )
                InfoItem(
                    text = "• Документация: https://shizuku.rikka.app/"
                )
            }
        }
    }
}

@Composable
private fun StepItem(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$number. ",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoItem(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer
    )
}
