package com.mazda.control

import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Утилита для проверки готовности Shizuku Binder
 *
 * Можно вызвать из MainActivity или любого другого места
 */
object ShizukuBinderChecker {

    private const val TAG = "ShizukuChecker"

    /**
     * Проверка с повторными попытками
     *
     * @param maxAttempts Максимальное количество попыток
     * @param delayMs Задержка между попытками в мс
     * @return true если binder готов
     */
    fun waitForBinder(maxAttempts: Int = 10, delayMs: Long = 500): Boolean {
        for (attempt in 1..maxAttempts) {
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "✅ Shizuku binder ready (attempt $attempt)")
                return true
            }
            Log.d(TAG, "⏳ Waiting for Shizuku binder... ($attempt/$maxAttempts)")
            if (attempt < maxAttempts) {
                Thread.sleep(delayMs)
            }
        }
        Log.e(TAG, "❌ Shizuku binder NOT ready after $maxAttempts attempts")
        return false
    }

    /**
     * Полная диагностика Shizuku
     */
    fun diagnose() {
        Log.d(TAG, "🔍 Shizuku Diagnosis:")
        Log.d(TAG, "====================")

        try {
            // 1. Проверка ping
            val pingResult = Shizuku.pingBinder()
            Log.d(TAG, "🏓 Ping Binder: $pingResult")

            if (pingResult) {
                // 2. Проверка версии
                val version = Shizuku.getVersion()
                Log.d(TAG, "📦 Version: $version")

                // 3. Проверка UID
                val uid = Shizuku.getUid()
                Log.d(TAG, "🔐 UID: $uid")

                // 4. Проверка прав
                val permission = Shizuku.checkSelfPermission()
                Log.d(TAG, "🔑 Permission: $permission (0=granted, -1=not authorized)")

                Log.d(TAG, "✅ Shizuku fully operational")
            } else {
                Log.w(TAG, "⚠️ Binder not ready - Shizuku may not be running")
                Log.w(TAG, "💡 Possible reasons:")
                Log.w(TAG, "   - Shizuku not started (run start.sh)")
                Log.w(TAG, "   - Shizuku not installed")
                Log.w(TAG, "   - Using built-in Shizuku (different package)")
                Log.w(TAG, "   - Binder not initialized yet (wait + retry)")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "❌ Binder not ready: ${e.message}", e)
            Log.w(TAG, "💡 Wait for Shizuku initialization and retry")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }
}
