package com.mazda.control

import android.util.Log
import com.mazda.control.IMegaController
import rikka.shizuku.Shizuku

/**
 * Реальный контроллер для работы со спойлером Mazda через Shizuku
 *
 * Работает ТОЛЬКО на Head Unit с запущенным Shizuku
 * На эмуляторе будет возвращать ошибки (Shizuku не запущен)
 */
object RealMegaController : IMegaController {

    private const val TAG = "RealMegaController"
    private var callCount = 0
    private const val SERVICE_NAME = "mega.controller"
    private const val TRANSACTION_CALL_SERVICE = 1

    /**
     * Property ID для спойлера (3 варианта из разных источников)
     */
    private val spoilerPropertyId: Int
        get() = SpoilerPropertyMode.getPropertyId(TestMode.getSpoilerPropertyMode())

    override fun setSpoiler(position: Int): Boolean {
        Log.d(TAG, "🚗 setSpoiler: position=$position, mode=${TestMode.getSpoilerPropertyMode().propName}, propId=0x${spoilerPropertyId.toString(16)}")
        return callService(TRANSACTION_CALL_SERVICE, spoilerPropertyId, position)
    }

    /**
     * Проверка доступности сервиса
     */
    fun checkServiceAvailability(): Boolean {
        Log.d(TAG, "🔍 Checking service availability...")
        
        // Проверяем Shizuku с повторными попытками
        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            val pingResult = Shizuku.pingBinder()
            Log.d(TAG, "🏓 Shizuku pingBinder (attempt ${attempts + 1}): $pingResult")
            
            if (pingResult) {
                Log.d(TAG, "✅ Shizuku binder available (attempt ${attempts + 1})")
                break
            }

            attempts++
            if (attempts < maxAttempts) {
                Log.w(TAG, "⏳ Shizuku not ready, retrying... ($attempts/$maxAttempts)")
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "❌ Shizuku not running!")
            Log.e(TAG, "💡 Please ensure Shizuku is installed and running on the device")
            return false
        }
        
        // Дополнительная диагностика
        try {
            val version = Shizuku.getVersion()
            Log.d(TAG, "📦 Shizuku version: $version")
            
            val uid = Shizuku.getUid()
            Log.d(TAG, "🔐 Shizuku UID: $uid")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting Shizuku info: ${e.message}", e)
        }

        // Проверяем сервис
        val available = ShizukuBinderCaller.isServiceAvailable(SERVICE_NAME)

        if (available) {
            Log.i(TAG, "✅ Service $SERVICE_NAME is available")
        } else {
            Log.e(TAG, "❌ Service $SERVICE_NAME not found")
        }

        return available
    }

    override fun callService(code: Int, propId: Int, value: Int): Boolean {
        callCount++
        
        Log.d(TAG, "📞 callService #$callCount: code=0x${code.toString(16)}, propId=0x${propId.toString(16)}, value=$value")
        
        // Проверяем Shizuku
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "❌ Shizuku not running!")
            return false
        }
        
        // Проверяем сервис
        if (!checkServiceAvailability()) {
            return false
        }
        
        // Формируем данные для транзакции
        val data = ((code and 0xFFFF) shl 16) or ((propId and 0xFFFF) shl 8) or (value and 0xFF)
        
        // Вызываем через реальный ShizukuBinderCaller
        val success = ShizukuBinderCaller.callService(SERVICE_NAME, TRANSACTION_CALL_SERVICE, data)
        
        if (success) {
            Log.d(TAG, "✅ Service call successful")
        } else {
            Log.e(TAG, "❌ Service call failed")
        }
        
        return success
    }

    override fun getProperty(propId: Int): Int {
        Log.d(TAG, "📖 getProperty: propId=0x${propId.toString(16)}")
        
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "❌ Shizuku not running!")
            return 0
        }
        
        if (!checkServiceAvailability()) {
            return 0
        }
        
        val value = ShizukuBinderCaller.getProperty(SERVICE_NAME, propId)
        
        Log.d(TAG, "✅ Got property: $value")
        return value
    }

    override fun setProperty(propId: Int, value: Int): Boolean {
        Log.d(TAG, "✏️ setProperty: propId=0x${propId.toString(16)}, value=$value")
        
        if (!Shizuku.pingBinder()) {
            return false
        }
        
        if (!checkServiceAvailability()) {
            return false
        }
        
        val success = ShizukuBinderCaller.setProperty(SERVICE_NAME, propId, value)

        if (success) {
            Log.d(TAG, "✅ Property set")
        } else {
            Log.e(TAG, "❌ Property set failed")
        }

        return success
    }

    override fun getStats(): Map<String, Any> {
        return mapOf(
            "callCount" to callCount,
            "shizukuRunning" to Shizuku.pingBinder(),
            "serviceAvailable" to checkServiceAvailability(),
            "serviceName" to SERVICE_NAME,
            "mode" to "REAL",
            "spoilerPropertyMode" to TestMode.getSpoilerPropertyMode().propName,
            "spoilerPropertyId" to "0x${spoilerPropertyId.toString(16)}"
        )
    }

    fun reset() {
        callCount = 0
        Log.d(TAG, "🔄 Reset")
    }
}
