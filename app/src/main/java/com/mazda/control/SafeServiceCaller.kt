package com.mazda.control

import com.mazda.control.Logger
import kotlinx.coroutines.*

/**
 * Безопасный вызов сервисов с защитой от технических рисков
 * 
 * Реализует:
 * - Таймаут вызова (< 5 секунд для защиты от Watchdog)
 * - Очистку ресурсов (try-finally)
 * - Повторные попытки при ошибках
 * - Логирование для отладки
 * 
 * @param timeoutMs Таймаут в миллисекундах (по умолчанию 5000)
 * @param retryCount Количество попыток (по умолчанию 3)
 * @param retryDelayMs Задержка между попытками в миллисекундах (по умолчанию 1000)
 */
class SafeServiceCaller(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val retryCount: Int = MAX_RETRY_COUNT,
    private val retryDelayMs: Long = RETRY_DELAY_MS
) {
    
    companion object {
        private const val TAG = "SafeServiceCaller"
        
        const val MAX_RETRY_COUNT = 3
        const val RETRY_DELAY_MS = 1000L
        const val DEFAULT_TIMEOUT_MS = 5000L // 5 секунд (<< 60 сек Watchdog)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Результат безопасного вызова
     */
    data class SafeCallResult(
        val success: Boolean,
        val output: String? = null,
        val errorMessage: String? = null,
        val retryCount: Int = 0,
        val executionTimeMs: Long = 0
    )
    
    /**
     * Безопасный вызов сервиса с таймаутом и повторами
     *
     * @param command Команда для выполнения (например, "service call mega.controller 1 ...")
     * @param timeoutMs Таймаут в миллисекундах (переопределяет значение конструктора)
     * @param retryCount Количество попыток (переопределяет значение конструктора)
     */
    suspend fun executeWithRetry(
        command: String,
        timeoutMs: Long = this.timeoutMs,
        retryCount: Int = this.retryCount
    ): SafeCallResult {

        var lastResult: SafeCallResult? = null
        var attempt = 0

        while (attempt < retryCount) {
            attempt++
            val startTime = System.currentTimeMillis()

            Logger.d(TAG, "📤 Attempt #$attempt: $command")

            try {
                // Выполняем с таймаутом
                val result = withTimeout(timeoutMs) {
                    executeServiceCall(command)
                }

                val executionTime = System.currentTimeMillis() - startTime

                if (result.success) {
                    Logger.i(TAG, "✅ Success on attempt #$attempt (${executionTime}ms)")
                    return SafeCallResult(
                        success = true,
                        output = result.output,
                        retryCount = attempt,
                        executionTimeMs = executionTime
                    )
                } else {
                    Logger.w(TAG, "⚠️ Failed on attempt #$attempt: ${result.errorOutput}")
                    lastResult = SafeCallResult(
                        success = false,
                        errorMessage = result.errorOutput,
                        retryCount = attempt,
                        executionTimeMs = executionTime
                    )

                    // Ждём перед следующей попыткой
                    if (attempt < retryCount) {
                        delay(retryDelayMs)
                    }
                }
                
            } catch (e: TimeoutCancellationException) {
                val executionTime = System.currentTimeMillis() - startTime
                Logger.e(TAG, "❌ Timeout after ${timeoutMs}ms (attempt #$attempt)")
                
                lastResult = SafeCallResult(
                    success = false,
                    errorMessage = "Timeout after ${timeoutMs}ms",
                    retryCount = attempt,
                    executionTimeMs = executionTime
                )
                
                if (attempt < retryCount) {
                    delay(RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                Logger.e(TAG, "❌ Exception on attempt #$attempt: ${e.message}", e)
                
                lastResult = SafeCallResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error",
                    retryCount = attempt,
                    executionTimeMs = executionTime
                )
                
                if (attempt < retryCount) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        // Все попытки исчерпаны
        Logger.e(TAG, "❌ All $retryCount attempts failed")
        return lastResult ?: SafeCallResult(
            success = false,
            errorMessage = "Unknown error",
            retryCount = attempt
        )
    }
    
    /**
     * Выполнение service call
     */
    private suspend fun executeServiceCall(command: String): ShellCommandResult {
        return withContext(Dispatchers.IO) {
            try {
                ShizukuShellExecutor.execute(command)
            } catch (e: Exception) {
                Logger.e(TAG, "Service call failed", e)
                ShellCommandResult(
                    exitCode = -1,
                    output = "",
                    errorOutput = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    /**
     * Асинхронный вызов (не блокирующий)
     */
    fun executeAsync(
        command: String,
        callback: (SafeCallResult) -> Unit
    ): Job {
        return scope.launch {
            val result = executeWithRetry(command)
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }
    
    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        Logger.d(TAG, "Cleaning up resources...")
        scope.cancel()
        Logger.i(TAG, "Cleanup complete")
    }
}
