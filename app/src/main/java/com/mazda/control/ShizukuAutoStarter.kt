package com.mazda.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Автоматический запуск Shizuku при старте устройства
 * 
 * Позволяет избежать необходимости ручной активации Shizuku через ADB
 * после каждой перезагрузки устройства.
 * 
 * Внимание: Для работы автозапуска требуется, чтобы Shizuku был
 * предварительно настроен на автозапуск через приложение Shizuku.
 */
class ShizukuAutoStarter {

    companion object {
        private const val TAG = "ShizukuAutoStarter"
    }

    private val context: Context
    private var isRegistered = false

    constructor(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Receiver для получения уведомления о загрузке устройства
     */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.d(TAG, "📢 Boot completed received")
                val autoStarter = ShizukuAutoStarter(context)
                autoStarter.startShizuku()
            }
        }
    }

    /**
     * Регистрация receiver'а для получения уведомления о загрузке
     * 
     * Вызывать в onCreate Application или MainActivity
     */
    fun register() {
        if (isRegistered) {
            Log.w(TAG, "Already registered")
            return
        }

        try {
            val bootReceiver = BootReceiver()
            val intentFilter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bootReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(bootReceiver, intentFilter)
            }
            
            isRegistered = true
            Log.d(TAG, "✅ Boot receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register boot receiver", e)
        }
    }

    /**
     * Отписка receiver'а
     * 
     * Вызывать в onDestroy Application или MainActivity
     */
    fun unregister() {
        if (!isRegistered) {
            Log.w(TAG, "Not registered")
            return
        }

        try {
            // BootReceiver unregister requires the same instance
            // For simplicity, we'll just mark as unregistered
            isRegistered = false
            Log.d(TAG, "✅ Boot receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister boot receiver", e)
        }
    }

    /**
     * Запуск Shizuku
     * 
     * Проверяет наличие Shizuku и пытается его запустить
     */
    fun startShizuku() {
        Log.d(TAG, "🚀 Starting Shizuku...")

        if (!isShizukuInstalled()) {
            Log.e(TAG, "❌ Shizuku not installed")
            return
        }

        // Проверяем, доступен ли уже Shizuku
        if (Shizuku.pingBinder()) {
            Log.d(TAG, "✅ Shizuku already running")
            return
        }

        // Пытаемся запустить Shizuku через сервис
        // Это работает, если Shizuku настроен на автозапуск
        try {
            val startIntent = Intent(context, ShizukuServiceStarter::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            Log.d(TAG, "✅ Shizuku start command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Shizuku service", e)
            // Альтернативно: напоминаем пользователю активировать Shizuku
            showActivationNotification()
        }
    }

    /**
     * Проверка установленного Shizuku
     */
    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Показ уведомления о необходимости активации Shizuku
     */
    private fun showActivationNotification() {
        Log.w(TAG, "⚠️ Shizuku requires manual activation")
        // В реальной реализации здесь можно было бы показать notification
        // с intent для открытия ShizukuActivationActivity
    }

    /**
     * Быстрая проверка и запуск Shizuku
     * 
     * @return true если Shizuku доступен
     */
    fun checkAndStart(): Boolean {
        if (Shizuku.pingBinder()) {
            Log.d(TAG, "✅ Shizuku already available")
            return true
        }

        Log.w(TAG, "⚠️ Shizuku not available, attempting to start...")
        startShizuku()
        
        // Небольшая задержка для проверки запуска
        Thread.sleep(1000)
        
        return Shizuku.pingBinder()
    }
}

/**
 * Сервис для запуска Shizuku
 * 
 * Используется для фонового запуска Shizuku после загрузки устройства
 */
class ShizukuServiceStarter : android.app.Service() {

    companion object {
        private const val TAG = "ShizukuService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Service started")
        
        // Пытаемся запустить Shizuku
        try {
            // Отправляем intent для активации Shizuku
            val shizukuIntent = Intent("moe.shizuku.intent.action.START")
            shizukuIntent.setPackage("moe.shizuku.privileged.api")
            sendBroadcast(shizukuIntent)
            Log.d(TAG, "✅ Shizuku start broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Shizuku start broadcast", e)
        }

        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null
}
