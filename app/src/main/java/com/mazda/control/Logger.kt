package com.mazda.control

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter

/**
 * Централизованное логирование для MazdaControl
 * 
 * Пишет логи:
 * 1. В файл (надёжное хранилище)
 * 2. В Logcat (для отладки)
 * 3. В UI список (для отображения)
 * 
 * Использование:
 * - Logger.d(TAG, "Сообщение")
 * - Logger.e(TAG, "Ошибка")
 * - Logger.w(TAG, "Предупреждение")
 */
object Logger {
    
    private var logFile: File? = null
    private var uiLogMessages: MutableList<String>? = null
    
    /**
     * Инициализация логгера
     * 
     * @param context Контекст приложения
     * @param uiLogMessages Список для UI (опционально)
     */
    fun init(context: Context, uiLogMessages: MutableList<String>? = null) {
        // Используем правильное хранилище для Android 10+
        val logDir = File(context.getExternalFilesDir(null), "MazdaControl")
        
        if (!logDir.exists()) {
            val created = logDir.mkdirs()
            Log.d("Logger", "Log directory created: $created, path: ${logDir.absolutePath}")
        }
        
        val fileName = "mazda_log_${getCurrentDate()}.txt"
        logFile = File(logDir, fileName)
        this.uiLogMessages = uiLogMessages
        
        Log.i("Logger", "Initialized. Log file: ${logFile?.absolutePath}")

        // Запишем стартовое сообщение
        i("Logger", "=== Сессия началась ===")
        i("Logger", "📁 Файл лога: ${logFile?.absolutePath ?: "N/A"}")
    }
    
    /**
     * Debug сообщение
     */
    fun d(tag: String, message: String) {
        logToFile("D", tag, message)
        Log.d(tag, message)
    }
    
    /**
     * Error сообщение
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        logToFile("E", tag, "$message ${throwable?.message ?: ""}")
        Log.e(tag, message, throwable)
    }
    
    /**
     * Warning сообщение
     */
    fun w(tag: String, message: String) {
        logToFile("W", tag, message)
        Log.w(tag, message)
    }
    
    /**
     * Info сообщение
     */
    fun i(tag: String, message: String) {
        logToFile("I", tag, message)
        Log.i(tag, message)
    }
    
    /**
     * Запись в файл
     */
    private fun logToFile(level: String, tag: String, message: String) {
        val logFile = this.logFile ?: return
        
        try {
            val timestamp = getCurrentTimestamp()
            val logEntry = "[$timestamp] [$level] $tag: $message"
            
            // Добавляем в UI список
            uiLogMessages?.add(logEntry)
            
            // Пишем в файл
            FileWriter(logFile, true).use { writer ->
                writer.appendLine(logEntry)
            }
        } catch (e: Exception) {
            // Критическая ошибка логирования - пишем в Logcat
            Log.e("Logger", "❌ Failed to write to file: ${e.message}", e)
            
            // Добавляем в UI
            uiLogMessages?.add("❌ ОШИБКА ЛОГА: ${e.message}")
        }
    }
    
    /**
     * Получить путь к файлу лога
     */
    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }
    
    /**
     * Получить файл лога
     */
    fun getLogFile(): File? {
        return logFile
    }
    
    /**
     * Очистить старый лог файл (перед новой сессией)
     */
    fun clearLogFile() {
        logFile?.delete()
        Log.d("Logger", "Log file cleared")
    }
    
    /**
     * Получить статистику лога
     */
    fun getLogStats(): LogStats {
        val file = logFile ?: return LogStats(0, 0, 0, 0, 0, "Not initialized")

        if (!file.exists()) {
            return LogStats(0, 0, 0, 0, 0, "File does not exist")
        }
        
        var totalLines = 0
        var errorCount = 0
        var warningCount = 0
        var debugCount = 0
        var infoCount = 0
        
        try {
            file.forEachLine { line ->
                totalLines++
                when {
                    line.contains("[E]") -> errorCount++
                    line.contains("[W]") -> warningCount++
                    line.contains("[D]") -> debugCount++
                    line.contains("[I]") -> infoCount++
                }
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to read log file", e)
        }
        
        return LogStats(
            totalLines = totalLines,
            errorCount = errorCount,
            warningCount = warningCount,
            debugCount = debugCount,
            infoCount = infoCount,
            filePath = file.absolutePath
        )
    }

    /**
     * Статистика лога
     */
    data class LogStats(
        val totalLines: Int,
        val errorCount: Int,
        val warningCount: Int,
        val debugCount: Int,
        val infoCount: Int,
        val filePath: String
    ) {
        override fun toString(): String {
            return "Log Stats: $totalLines lines, E:$errorCount, W:$warningCount, D:$debugCount, I:$infoCount, Path: $filePath"
        }
    }
    
    // === ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ===
    
    private fun getCurrentDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
    
    private fun getCurrentTimestamp(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
}
