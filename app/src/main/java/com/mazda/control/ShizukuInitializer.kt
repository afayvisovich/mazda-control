package com.mazda.control

import android.content.Context
import android.content.Intent
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Инициализация Shizuku из приложения
 * 
 * Поддерживает три режима:
 * 1. Если Shizuku уже запущен - просто проверяем
 * 2. Если есть root - запускаем через su
 * 3. Если нет root - пытаемся через exec (требует ADB)
 */
object ShizukuInitializer {
    
    private const val TAG = "ShizukuInitializer"
    
    /**
     * Попытка инициализировать Shizuku
     * 
     * @return true если Shizuku запущен и готов
     */
    fun initialize(context: Context): Boolean {
        Log.d(TAG, "🔍 Attempting to initialize Shizuku...")
        
        // 1. Проверяем, запущен ли уже
        if (Shizuku.pingBinder()) {
            Log.d(TAG, "✅ Shizuku already running")
            return true
        }
        
        Log.d(TAG, "⚠️ Shizuku not running, trying to start...")
        
        // 2. Пытаемся запустить
        val started = when {
            // Сначала пробуем через broadcast (легально)
            startViaBroadcast(context) -> {
                Log.d(TAG, "✅ Started via broadcast")
                true
            }
            // Затем пробуем exec (требует root или ADB)
            startViaExec() -> {
                Log.d(TAG, "✅ Started via exec")
                true
            }
            else -> {
                Log.e(TAG, "❌ All methods failed")
                false
            }
        }
        
        // 3. Проверяем результат
        if (started) {
            // Ждём запуска
            Thread.sleep(2000)
            return Shizuku.pingBinder()
        }
        
        return false
    }
    
    /**
     * Запуск через Broadcast Intent
     */
    private fun startViaBroadcast(context: Context): Boolean {
        return try {
            val intent = Intent("moe.shizuku.intent.action.START")
            intent.setPackage("moe.shizuku.privileged.api")
            
            // На Android 8+ нужно registerReceiver в Manifest
            context.sendBroadcast(intent)
            
            Log.d(TAG, "📻 Broadcast sent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Broadcast failed", e)
            false
        }
    }
    
    /**
     * Запуск через Runtime.exec()
     * Работает только с root или ADB
     */
    private fun startViaExec(): Boolean {
        return try {
            val command = arrayOf(
                "sh",
                "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
            )
            
            Log.d(TAG, "▶️ Executing: ${command.joinToString(" ")}")
            
            val process = Runtime.getRuntime().exec(command)
            
            // Читаем вывод (для отладки)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { 
                it.readText() 
            }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { 
                it.readText() 
            }
            
            Log.d(TAG, "📝 Output: $output")
            if (error.isNotEmpty()) {
                Log.e(TAG, "❌ Error: $error")
            }
            
            // Ждём завершения
            val exitCode = process.waitFor()
            
            Log.d(TAG, "🏁 Exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exec failed", e)
            false
        }
    }
    
    /**
     * Запуск через su (если есть root)
     */
    private fun startViaSu(): Boolean {
        return try {
            val command = arrayOf(
                "su",
                "-c",
                "sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
            )
            
            Log.d(TAG, "▶️ Executing with root: ${command.joinToString(" ")}")
            
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            
            Log.d(TAG, "🏁 Exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ su failed", e)
            false
        }
    }
    
    /**
     * Проверка наличия root
     */
    fun hasRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use {
                it.readText()
            }
            process.waitFor() == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            Log.d(TAG, "❌ No root access", e)
            false
        }
    }
    
    /**
     * Проверка, запущен ли Shizuku
     */
    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "❌ pingBinder failed", e)
            false
        }
    }
}
