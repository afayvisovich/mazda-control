package com.mazda.control

import android.content.Context
import com.mazda.control.Logger
import rikka.shizuku.Shizuku

/**
 * Монитор здоровья системы для предотвращения сбоев
 * 
 * Проверяет:
 * - Доступность Shizuku
 * - Количество последовательных неудач
 * - Время с последнего успешного вызова
 * - Смерть Binder (критичная ошибка)
 */
class SystemHealthMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemHealthMonitor"
        
        // Лимиты
        private const val MAX_CONSECUTIVE_FAILURES = 5
        private const val HEALTH_CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 минут
        private const val BINDER_DEATH_THRESHOLD = 3
    }
    
    /**
     * Слушатель событий здоровья системы
     */
    interface HealthListener {
        fun onSystemHealthy()
        fun onSystemDegraded(reason: String)
        fun onSystemUnstable(reason: String)
    }
    
    private var listener: HealthListener? = null
    private var lastSuccessfulCallTime = 0L
    private var consecutiveFailures = 0
    private var binderDeathCount = 0
    private var isMonitoring = false
    
    // Статистика
    private var totalCalls = 0
    private var successfulCalls = 0
    private var failedCalls = 0
    
    /**
     * Проверка: система здорова?
     */
    fun isSystemHealthy(): Boolean {
        totalCalls++
        
        // 1. Проверка доступности Shizuku
        if (!isShizukuAvailable()) {
            Logger.e(TAG, "❌ Shizuku Binder dead")
            notifyUnstable("Shizuku Binder unavailable")
            return false
        }
        
        // 2. Проверка количества неудач
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Logger.w(TAG, "⚠️ Too many consecutive failures: $consecutiveFailures")
            notifyDegraded("Too many consecutive failures ($consecutiveFailures)")
            return false
        }
        
        // 3. Проверка времени с последнего успеха
        val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulCallTime
        if (timeSinceLastSuccess > HEALTH_CHECK_INTERVAL_MS && lastSuccessfulCallTime > 0) {
            Logger.w(TAG, "⚠️ No successful calls for ${timeSinceLastSuccess / 1000}s")
            
            // Делаем health check
            if (!performHealthCheck()) {
                notifyDegraded("Health check failed")
                return false
            }
        }
        
        // Система здорова
        notifyHealthy()
        return true
    }
    
    /**
     * Проверка доступности Shizuku
     */
    private fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Logger.e(TAG, "Shizuku ping failed", e)
            false
        }
    }
    
    /**
     * Тестовый вызов для проверки системы
     */
    private fun performHealthCheck(): Boolean {
        return try {
            // Простой вызов без побочных эффектов - проверяем список сервисов
            val result = ShizukuShellExecutor.execute("service list")
            val success = result.success && !result.output.isNullOrBlank()
            
            if (success) {
                Logger.d(TAG, "✅ Health check passed")
                onCallSuccess()
            } else {
                Logger.w(TAG, "⚠️ Health check failed: ${result.errorOutput}")
                onCallFailure()
            }
            
            success
        } catch (e: Exception) {
            Logger.e(TAG, "Health check exception", e)
            onCallFailure()
            false
        }
    }
    
    /**
     * Регистрация успешного вызова
     */
    fun onCallSuccess() {
        consecutiveFailures = 0
        lastSuccessfulCallTime = System.currentTimeMillis()
        successfulCalls++
        Logger.d(TAG, "✅ Call success (total: $successfulCalls/$totalCalls)")
    }
    
    /**
     * Регистрация неудачного вызова
     */
    fun onCallFailure(reason: String? = null) {
        consecutiveFailures++
        failedCalls++
        Logger.w(TAG, "❌ Call failure #$consecutiveFailures: $reason")
    }
    
    /**
     * Установить слушателя
     */
    fun setListener(listener: HealthListener) {
        this.listener = listener
    }
    
    /**
     * Начать мониторинг
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Logger.w(TAG, "Monitoring already started")
            return
        }
        
        isMonitoring = true
        Shizuku.addBinderDeadListener(binderDeathListener)
        Logger.i(TAG, "Health monitoring started")
    }
    
    /**
     * Остановить мониторинг
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            Logger.w(TAG, "Monitoring not started")
            return
        }
        
        isMonitoring = false
        try {
            Shizuku.removeBinderDeadListener(binderDeathListener)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove binder death listener", e)
        }
        Logger.i(TAG, "Health monitoring stopped")
    }
    
    /**
     * Обработчик смерти Binder
     */
    private val binderDeathListener = Shizuku.OnBinderDeadListener {
        binderDeathCount++
        Logger.e(TAG, "💀 Binder died (count: $binderDeathCount)")
        
        if (binderDeathCount >= BINDER_DEATH_THRESHOLD) {
            notifyUnstable("Binder died $binderDeathCount times")
        }
        
        // Перезапускаем мониторинг
        consecutiveFailures = MAX_CONSECUTIVE_FAILURES
    }
    
    /**
     * Уведомления слушателя
     */
    private fun notifyHealthy() {
        listener?.onSystemHealthy()
    }
    
    private fun notifyDegraded(reason: String) {
        listener?.onSystemDegraded(reason)
    }
    
    private fun notifyUnstable(reason: String) {
        listener?.onSystemUnstable(reason)
    }
    
    /**
     * Получить статистику
     */
    fun getStats(): HealthStats {
        return HealthStats(
            totalCalls = totalCalls,
            successfulCalls = successfulCalls,
            failedCalls = failedCalls,
            consecutiveFailures = consecutiveFailures,
            binderDeathCount = binderDeathCount,
            lastSuccessfulCallTime = lastSuccessfulCallTime,
            successRate = if (totalCalls > 0) (successfulCalls.toFloat() / totalCalls) * 100 else 0f
        )
    }
    
    data class HealthStats(
        val totalCalls: Int,
        val successfulCalls: Int,
        val failedCalls: Int,
        val consecutiveFailures: Int,
        val binderDeathCount: Int,
        val lastSuccessfulCallTime: Long,
        val successRate: Float
    ) {
        override fun toString(): String {
            return "Health: ${successfulCalls}/${totalCalls} (${successRate.toInt()}%), " +
                   "failures: $consecutiveFailures, binder deaths: $binderDeathCount"
        }
    }
}
