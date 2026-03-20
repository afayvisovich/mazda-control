package com.mazda.control

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Менеджер логов - читает logcat через Shizuku UserService
 */
object LogcatManager {

    private const val TAG = "LogcatManager"
    private const val REFRESH_INTERVAL = 1000L

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var job: Job? = null

    // Фильтры для логов
    private val filterTags = setOf(
        // Наши контроллеры
        "MockMegaController",
        "MockMegaService",
        "MockServiceRegistrar",
        "RealMegaController",
        "TBoxProtocol",
        // Shizuku
        "ShizukuBinderCaller",
        "ShizukuShellExecutor",
        "ShellExecutorService",
        "ShizukuIntegrationHelper",
        "ShizukuBinderChecker",
        "ShizukuActivation",
        "ShizukuHelper",
        // Приложение
        "TestMode",
        "MainActivity",
        "MazdaControlApp",
        "LogcatManager",
        "DebugLogScreen",
        // Car сервисы
        "mega.controller",
        "CarService",
        "car_property",
        "tbox",
        // OTA
        "OTA",
        "Updater",
        "Download"
    )

    private val maxLogs = 500
    private var lastReadCount = 0

    /**
     * Начать чтение logcat
     */
    fun start() {
        if (job?.isActive == true) {
            Log.w(TAG, "Logcat reading already started")
            return
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "Logcat reading started")

            while (isActive) {
                try {
                    // Используем logcat -v time с -T 1 для чтения только новых логов
                    val command = "logcat -v time -t 100 *:V"
                    val result = if (ShizukuShellExecutor.isServiceBound()) {
                        ShizukuShellExecutor.execute(command)
                    } else {
                        // Fallback для эмулятора
                        val process = Runtime.getRuntime().exec(command.split(" ").toTypedArray())
                        val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                        ShellCommandResult(
                            exitCode = process.waitFor(),
                            output = output,
                            errorOutput = "",
                            success = process.exitValue() == 0
                        )
                    }

                    if (result.success) {
                        val newLogs = result.output.lines()
                            .filter { line -> filterTags.any { tag -> line.contains(tag) } }
                            .mapNotNull { LogEntry.fromLogLine(it, lastReadCount++.toLong()) }

                        if (newLogs.isNotEmpty()) {
                            addLogs(newLogs.takeLast(maxLogs / 2))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading logcat", e)
                }

                delay(REFRESH_INTERVAL)
            }
        }
    }

    /**
     * Остановить чтение logcat
     */
    fun stop() {
        job?.cancel()
        job = null
        Log.i(TAG, "Logcat reading stopped")
    }

    /**
     * Добавить логи
     */
    private fun addLogs(entries: List<LogEntry>) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.addAll(entries)

        while (currentLogs.size > maxLogs) {
            currentLogs.removeAt(0)
        }

        _logs.value = currentLogs
    }

    /**
     * Очистить логи
     */
    fun clear() {
        _logs.value = emptyList()
        lastReadCount = 0
        Log.i(TAG, "Logs cleared")
    }
}
