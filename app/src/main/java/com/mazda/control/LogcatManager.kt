package com.mazda.control

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Менеджер логов - читает logcat периодически
 */
object LogcatManager {
    
    private const val TAG = "LogcatManager"
    private const val REFRESH_INTERVAL = 1000L // 1 секунда
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private var job: Job? = null
    
    // Фильтры для логов
    private val filterTags = setOf(
        "MockMegaController",
        "MockMegaService",
        "MockServiceRegistrar",
        "RealMegaController",
        "ShizukuBinderCaller",
        "TestMode",
        "MainActivity",
        "MazdaControlApp"
    )
    
    private val maxLogs = 500
    private var nextLogId = 0L
    private val seenLogIds = mutableSetOf<Long>()
    
    /**
     * Начать чтение logcat
     */
    fun start() {
        if (job?.isActive == true) {
            Log.w(TAG, "⚠️ Logcat reading already started")
            return
        }
        
        job = CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "📋 Logcat reading started")
            
            while (isActive) {
                try {
                    // Читаем логи командой logcat -d
                    val process = Runtime.getRuntime().exec("logcat -d -v time *:V")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    
                    val newLogs = mutableListOf<LogEntry>()
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            // Фильтруем по тегам
                            val shouldInclude = filterTags.any { tag -> logLine.contains(tag) }
                            
                            if (shouldInclude) {
                                LogEntry.fromLogLine(logLine, nextLogId++)?.let { entry ->
                                    newLogs.add(entry)
                                }
                            }
                        }
                    }
                    
                    reader.close()
                    process.destroy()
                    
                    // Добавляем только логи которые ещё не видели
                    val unseenLogs = newLogs.filter { it.id !in seenLogIds }
                    
                    if (unseenLogs.isNotEmpty()) {
                        addLogs(unseenLogs)
                        // Добавляем ID в множество просмотренных
                        seenLogIds.addAll(unseenLogs.map { it.id })
                        // Ограничиваем размер множества
                        if (seenLogIds.size > maxLogs * 2) {
                            val minId = seenLogIds.minOrNull() ?: 0L
                            seenLogIds.remove(minId)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reading logcat", e)
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
        Log.i(TAG, "🛑 Logcat reading stopped")
    }
    
    /**
     * Добавить логи
     */
    private fun addLogs(entries: List<LogEntry>) {
        val currentLogs = _logs.value.toMutableList()
        
        currentLogs.addAll(entries)
        
        // Ограничиваем размер
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
        seenLogIds.clear()  // Очищаем множество ID
        nextLogId = 0L  // Сбрасываем счётчик ID
        Log.i(TAG, "🗑 Logs cleared")
    }
    
    /**
     * Добавить тестовый лог
     */
    fun addTestLog(message: String, level: Int = Log.INFO) {
        addLogs(
            listOf(
                LogEntry(
                    id = nextLogId++,
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()),
                    level = level,
                    tag = "TestMode",
                    message = message
                )
            )
        )
    }
}
