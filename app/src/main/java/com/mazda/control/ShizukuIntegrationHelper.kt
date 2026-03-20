package com.mazda.control

import android.content.Context
import android.content.Intent
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Helper для интеграции Shizuku в приложение
 * 
 * Централизованное управление статусом Shizuku и проверка доступности
 */
object ShizukuIntegrationHelper {

    private const val TAG = "ShizukuHelper"

    /**
     * Статус Shizuku
     */
    sealed class ShizukuStatus {
        object Available : ShizukuStatus()
        object NotInstalled : ShizukuStatus()
        object NotAuthorized : ShizukuStatus()
        object Dead : ShizukuStatus()
    }

    /**
     * Проверка доступности Shizuku
     *
     * @return true если Shizuku доступен и готов к работе
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            val result = Shizuku.pingBinder()
            Log.d(TAG, "🏓 Shizuku pingBinder: $result")
            
            // Проверяем версию Shizuku
            val version = Shizuku.getVersion()
            Log.d(TAG, "📦 Shizuku version: $version")
            
            // Проверяем UID
            val uid = Shizuku.getUid()
            Log.d(TAG, "🔐 Shizuku UID: $uid")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Shizuku ping failed: ${e.message}", e)
            false
        }
    }

    /**
     * Получение текущего статуса Shizuku
     */
    fun getShizukuStatus(context: Context): ShizukuStatus {
        return try {
            // Сначала проверяем, доступен ли binder
            val binderAvailable = Shizuku.pingBinder()
            
            Log.d(TAG, "🔍 Shizuku pingBinder: $binderAvailable")
            
            if (!binderAvailable) {
                // Проверяем, установлено ли приложение Shizuku
                val shizukuInstalled = isShizukuInstalled(context)
                Log.d(TAG, "📦 Shizuku installed: $shizukuInstalled")
                
                if (!shizukuInstalled) {
                    ShizukuStatus.NotInstalled
                } else {
                    ShizukuStatus.Dead
                }
            } else {
                // Shizuku доступен, проверяем авторизацию
                val permission = Shizuku.checkSelfPermission()
                Log.d(TAG, "🔐 Shizuku checkSelfPermission: $permission")
                
                if (permission == 0) {
                    ShizukuStatus.Available
                } else {
                    ShizukuStatus.NotAuthorized
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Status check failed", e)
            ShizukuStatus.Dead
        }
    }

    /**
     * Проверка установленного Shizuku
     *
     * Ищем несколько возможных package name'ов
     */
    private fun isShizukuInstalled(context: Context): Boolean {
        // Стандартные package name'ы Shizuku
        val possiblePackageNames = listOf(
            "moe.shizuku.privileged.api",      // Стандартный Shizuku
            "moe.shizuku.manager",             // Shizuku Manager
            "rikka.shizuku",                   // Rikka Shizuku
            "com.android.shell",               // Встроенный Android Shell (иногда имеет Shizuku)
            "android"                          // Системный Android
        )
        
        for (packageName in possiblePackageNames) {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "📦 Found package: $packageName")
                return true
            } catch (e: Exception) {
                // Package not found, try next
            }
        }
        
        // Если не нашли ни один, пробуем получить все пакеты и ищем по ключевым словам
        try {
            val installedPackages = context.packageManager.getInstalledPackages(0)
            val shizukuPackages = installedPackages.filter { 
                it.packageName.contains("shizuku", ignoreCase = true) ||
                it.packageName.contains("privileged", ignoreCase = true)
            }
            
            if (shizukuPackages.isNotEmpty()) {
                Log.d(TAG, "🔍 Found Shizuku-like packages: ${shizukuPackages.map { it.packageName }}")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get installed packages", e)
        }
        
        return false
    }

    /**
     * Проверка и активация Shizuku
     * 
     * Если Shizuku не доступен, открывает Activity активации
     * 
     * @param context Context для запуска Activity
     * @return true если Shizuku доступен, false если требуется активация
     */
    fun checkAndActivate(context: Context): Boolean {
        val status = getShizukuStatus(context)

        return when (status) {
            is ShizukuStatus.Available -> {
                Log.d(TAG, "✅ Shizuku available and authorized")
                true
            }
            is ShizukuStatus.NotInstalled -> {
                Log.w(TAG, "⚠️ Shizuku not installed")
                showActivationActivity(context)
                false
            }
            is ShizukuStatus.NotAuthorized -> {
                Log.w(TAG, "⚠️ Shizuku not authorized")
                showActivationActivity(context)
                false
            }
            is ShizukuStatus.Dead -> {
                Log.w(TAG, "⚠️ Shizuku dead or not running")
                showActivationActivity(context)
                false
            }
        }
    }

    /**
     * Показ Activity активации Shizuku
     */
    private fun showActivationActivity(context: Context) {
        Log.d(TAG, "Opening ShizukuActivationActivity")
        val intent = Intent(context, ShizukuActivationActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Listener для мониторинга статуса Shizuku
     */
    private val shizukuStatusListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "✅ Shizuku binder received")
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "⚠️ Shizuku binder dead - attempting reconnection...")
        // Shizuku был убит, пробуем переподключиться через ShizukuInitializer
        // Это может понадобиться на Head Unit где Shizuku может быть убит системой
        startReconnectionThread()
    }

    // Ссылка на поток переподключения для возможности отмены
    @Volatile
    private var reconnectionThread: Thread? = null

    /**
     * Запуск потока переподключения к Shizuku
     */
    private fun startReconnectionThread() {
        // Отменяем предыдущий поток если есть
        cancelReconnectionThread()

        reconnectionThread = Thread {
            retryShizukuReconnection()
        }.apply {
            name = "ShizukuReconnect"
            isDaemon = true  //Daemon-темы не блокируют выход из приложения
            start()
        }
    }

    /**
     * Отмена потока переподключения
     */
    fun cancelReconnectionThread() {
        reconnectionThread?.let { thread ->
            if (thread.isAlive) {
                thread.interrupt()
                Log.d(TAG, "Reconnection thread cancelled")
            }
        }
        reconnectionThread = null
    }

    /**
     * Попытка переподключения к Shizuku после смерти binder
     */
    private fun retryShizukuReconnection() {
        val maxAttempts = 3
        val retryDelayMs = 2000L

        val appInstance = try {
            MazdaControlApp.instance
        } catch (e: UninitializedPropertyAccessException) {
            Log.w(TAG, "MazdaControlApp not initialized yet")
            return
        }

        for (attempt in 1..maxAttempts) {
            // Проверяем флаг прерывания перед каждой попыткой
            if (Thread.currentThread().isInterrupted) {
                Log.d(TAG, "Reconnection cancelled")
                return
            }

            Log.d(TAG, "Shizuku reconnection attempt $attempt/$maxAttempts")

            // Пробуем инициализировать через ShizukuInitializer
            val initialized = ShizukuInitializer.initialize(appInstance)
            if (initialized) {
                Log.d(TAG, "Shizuku reconnected successfully")
                return
            }

            if (attempt < maxAttempts) {
                Log.w(TAG, "Waiting ${retryDelayMs}ms before next attempt...")
                try {
                    // Используем Thread.sleep вместо InterruptedException для отмены
                    @Suppress("DEPRECATION")
                    Thread.sleep(retryDelayMs)
                } catch (e: InterruptedException) {
                    Log.d(TAG, "Reconnection sleep interrupted")
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }

        Log.e(TAG, "All Shizuku reconnection attempts failed")
    }

    /**
     * Регистрация слушателей статуса Shizuku
     * 
     * Вызывать в onCreate Activity/Application
     */
    fun registerStatusListeners() {
        try {
            Shizuku.addBinderReceivedListener(shizukuStatusListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
            Log.d(TAG, "Status listeners registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register listeners", e)
        }
    }

    /**
     * Отписка слушателей статуса Shizuku
     * 
     * Вызывать в onDestroy Activity/Application
     */
    fun unregisterStatusListeners() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuStatusListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
            Log.d(TAG, "Status listeners unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister listeners", e)
        }
    }

    /**
     * Проверка прав Shizuku
     *
     * @return true если приложение имеет права Shizuku
     */
    fun hasShizukuPermission(): Boolean {
        return try {
            // Сначала проверяем что binder готов
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "⚠️ Shizuku binder not ready yet")
                return false
            }
            
            val result = Shizuku.checkSelfPermission() == 0
            Log.d(TAG, "🔐 Shizuku permission check: $result")
            result
        } catch (e: IllegalStateException) {
            // binder haven't been received
            Log.e(TAG, "❌ Shizuku binder not ready: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Permission check failed: ${e.message}", e)
            false
        }
    }

    /**
     * Запрос прав Shizuku (если требуется)
     */
    fun requestPermission() {
        try {
            // Shizuku не требует явного запроса прав, как root
            // Права выдаются автоматически при активации
            Log.d(TAG, "Shizuku permission check complete")
        } catch (e: Exception) {
            Log.e(TAG, "Permission request failed", e)
        }
    }
}
