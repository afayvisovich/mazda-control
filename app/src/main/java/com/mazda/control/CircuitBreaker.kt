package com.mazda.control

import com.mazda.control.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Circuit Breaker для защиты от каскадных сбоев
 * 
 * Архитектура:
 * - CLOSED: Нормальная работа (вызовы разрешены)
 * - OPEN: Цепь разомкнута (вызовы запрещены, система восстанавливается)
 * - HALF_OPEN: Проверка восстановления (один тестовый вызов)
 * 
 * @param failureThreshold Количество ошибок для размыкания цепи
 * @param recoveryTimeoutMs Время до попытки восстановления
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeoutMs: Long = 60_000 // 1 минута
) {
    
    enum class State {
        CLOSED,      // ✅ Нормальная работа
        OPEN,        // ❌ Цепь разомкнута (защита)
        HALF_OPEN    // ⚠️ Проверка восстановления
    }
    
    private val mutex = Mutex()
    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var recoveryTime = 0L
    private var lastSuccessTime = 0L
    
    companion object {
        private const val TAG = "CircuitBreaker"
    }
    
    /**
     * Проверка: можно ли выполнить вызов?
     */
    suspend fun canExecute(): Boolean = mutex.withLock {
        return when (state) {
            State.CLOSED -> {
                Logger.d(TAG, "State: CLOSED - execution allowed")
                true
            }
            State.OPEN -> {
                val now = System.currentTimeMillis()
                if (now >= recoveryTime) {
                    state = State.HALF_OPEN
                    Logger.w(TAG, "State: OPEN → HALF_OPEN - testing recovery")
                    true
                } else {
                    val waitTime = (recoveryTime - now) / 1000
                    Logger.w(TAG, "State: OPEN - waiting ${waitTime}s more")
                    false
                }
            }
            State.HALF_OPEN -> {
                Logger.d(TAG, "State: HALF_OPEN - test execution allowed")
                true
            }
        }
    }
    
    /**
     * Регистрация успешного вызова
     */
    suspend fun onSuccess() = mutex.withLock {
        failureCount = 0
        lastSuccessTime = System.currentTimeMillis()
        
        if (state == State.HALF_OPEN) {
            state = State.CLOSED
            Logger.i(TAG, "State: HALF_OPEN → CLOSED - system recovered")
        }
        
        Logger.d(TAG, "Success recorded (count: $failureCount)")
    }
    
    /**
     * Регистрация неудачного вызова
     */
    suspend fun onFailure(reason: String? = null) = mutex.withLock {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        Logger.w(TAG, "Failure recorded (count: $failureCount/$failureThreshold): $reason")
        
        if (failureCount >= failureThreshold) {
            state = State.OPEN
            recoveryTime = System.currentTimeMillis() + recoveryTimeoutMs
            Logger.e(TAG, "⚠️ CIRCUIT OPEN - too many failures ($failureCount). Recovery in ${recoveryTimeoutMs / 1000}s")
        } else if (state == State.HALF_OPEN) {
            // Тестовый вызов не удался - снова открываем цепь
            state = State.OPEN
            recoveryTime = System.currentTimeMillis() + recoveryTimeoutMs
            Logger.w(TAG, "State: HALF_OPEN → OPEN - recovery test failed")
        }
    }
    
    /**
     * Получить текущее состояние
     */
    suspend fun getState(): State = mutex.withLock { state }
    
    /**
     * Сбросить состояние (для ручного восстановления)
     */
    suspend fun reset() = mutex.withLock {
        state = State.CLOSED
        failureCount = 0
        recoveryTime = 0
        Logger.i(TAG, "Circuit Breaker manually reset")
    }
    
    /**
     * Статистика для отладки
     */
    fun getStats(): CircuitBreakerStats {
        return CircuitBreakerStats(
            state = state,
            failureCount = failureCount,
            failureThreshold = failureThreshold,
            lastFailureTime = lastFailureTime,
            lastSuccessTime = lastSuccessTime,
            recoveryTime = recoveryTime
        )
    }
}

data class CircuitBreakerStats(
    val state: CircuitBreaker.State,
    val failureCount: Int,
    val failureThreshold: Int,
    val lastFailureTime: Long,
    val lastSuccessTime: Long,
    val recoveryTime: Long
) {
    fun timeUntilRecovery(): Long {
        val now = System.currentTimeMillis()
        return maxOf(0, recoveryTime - now)
    }
}
