package com.mazda.control

import android.content.Context
import kotlinx.coroutines.runBlocking
import rikka.shizuku.Shizuku

/**
 * КОНФИГУРАЦИЯ МИТИГАЦИИ РИСКОВ
 *
 * Параметры для разных режимов работы:
 * - Нормальный режим: защита от сбоев
 * - Режим тестирования: минимум ограничений
 * - Режим отладки: полная статистика
 */
data class RiskMitigationConfig(
    val circuitBreakerEnabled: Boolean = true,
    val circuitBreakerFailureThreshold: Int = 5,
    val circuitBreakerRecoveryTimeoutMs: Long = 60_000,
    
    val healthMonitorEnabled: Boolean = true,
    val healthMonitorMaxFailures: Int = 5,
    
    val safeCallerTimeoutMs: Long = 5000,
    val safeCallerRetryCount: Int = 3,
    val safeCallerRetryDelayMs: Long = 1000,
    
    // Режим тестирования (ослабляет защиту)
    val testingMode: Boolean = false
) {
    companion object {
        /**
         * Конфигурация для нормального режима (продакшен)
         */
        fun production(): RiskMitigationConfig = RiskMitigationConfig()
        
        /**
         * Конфигурация для тестирования (минимум ограничений)
         */
        fun testing(): RiskMitigationConfig = RiskMitigationConfig(
            circuitBreakerEnabled = true,
            circuitBreakerFailureThreshold = 10,  // Разумный лимит для тестов
            circuitBreakerRecoveryTimeoutMs = 10_000,  // Быстрое восстановление
            
            healthMonitorEnabled = true,
            healthMonitorMaxFailures = 10,  // Разумный лимит для тестов
            
            safeCallerTimeoutMs = 3000,  // Быстрее
            safeCallerRetryCount = 1,    // Меньше попыток
            safeCallerRetryDelayMs = 500,  // Меньше задержка
            
            testingMode = true
        )
        
        /**
         * Конфигурация для отладки (полная статистика)
         */
        fun debugging(): RiskMitigationConfig = RiskMitigationConfig(
            testingMode = false  // Защита включена, но много логов
        )
    }
}

/**
 * Контроллер для работы с TBox через Shizuku service call
 *
 * Использует Shizuku для вызова системных команд без root-прав
 *
 * Архитектура:
 * - Выполняет service call команды через Shizuku
 * - Работает с HIDL интерфейсами автомобиля
 * - Обрабатывает ответы от системных сервисов
 *
 * Управление спойлером через Property ID:
 * - Property ID: 0x6600022C (1711276588)
 * - Сигнал: VEHICLEDOOR_DOOR_PTS_SPOILERPOSITION
 * - OPEN: RealBody<41>=0x06, Time<29>=0x02
 * - CLOSE: RealBody<41>=0x05, Time<29>=0x0C
 *
 * МИТИГАЦИЯ ТЕХНИЧЕСКИХ РИСКОВ:
 * - Circuit Breaker: защита от каскадных сбоев
 * - Health Monitor: мониторинг здоровья системы
 * - Safe Service Caller: таймауты и повторные попытки
 * 
 * @param context Контекст приложения
 * @param config Конфигурация митигации рисков (по умолчанию production)
 */
class TBoxProtocolController(
    private val context: Context,
    private val config: RiskMitigationConfig = RiskMitigationConfig.production()
) {

    companion object {
        private const val TAG = "TBoxProtocol"

        // Property ID для спойлера из документации
        const val SPOILER_PROPERTY_ID = 0x6600022C // 1711276588
        const val SPOILER_OPEN_ACTION = 0x06
        const val SPOILER_CLOSE_ACTION = 0x05
        
        // Константы митигации рисков
        private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5
        private const val CIRCUIT_BREAKER_RECOVERY_TIMEOUT_MS = 60_000L
    }

    /**
     * Результат выполнения команды
     */
    data class CommandResult(
        val success: Boolean,
        val data: String?,
        val errorMessage: String? = null
    )

    // === КОМПОНЕНТЫ МИТИГАЦИИ РИСКОВ ===
    
    /**
     * Circuit Breaker для защиты от каскадных сбоев
     */
    private val circuitBreaker = CircuitBreaker(
        failureThreshold = if (config.testingMode) 100 else config.circuitBreakerFailureThreshold,
        recoveryTimeoutMs = if (config.testingMode) 10_000 else config.circuitBreakerRecoveryTimeoutMs
    )
    
    /**
     * Монитор здоровья системы
     */
    private val healthMonitor = SystemHealthMonitor(context).apply {
        if (config.healthMonitorEnabled) {
            setListener(object : SystemHealthMonitor.HealthListener {
                override fun onSystemHealthy() {
                    Logger.d(TAG, "✅ System healthy")
                }
                
                override fun onSystemDegraded(reason: String) {
                    Logger.w(TAG, "⚠️ System degraded: $reason")
                }
                
                override fun onSystemUnstable(reason: String) {
                    Logger.e(TAG, "❌ System unstable: $reason")
                }
            })
            startMonitoring()
        } else {
            Logger.w(TAG, "⚠️ Health Monitor disabled")
        }
    }
    
    /**
     * Безопасный вызов сервисов
     */
    private val safeServiceCaller = SafeServiceCaller(
        timeoutMs = config.safeCallerTimeoutMs,
        retryCount = config.safeCallerRetryCount,
        retryDelayMs = config.safeCallerRetryDelayMs
    )

    /**
     * Проверка доступности Shizuku
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Logger.e(TAG, "Shizuku ping failed", e)
            false
        }
    }

    /**
     * Выполнение service call команды
     *
     * @param serviceName Имя сервиса (например, "tbox", "car_service")
     * @param transactionCode Код транзакции
     * @param args Аргументы команды
     * @return Результат выполнения
     */
    fun serviceCall(
        serviceName: String,
        transactionCode: Int,
        vararg args: String
    ): CommandResult {
        if (!isShizukuAvailable()) {
            Logger.e(TAG, "❌ Shizuku not available")
            return CommandResult(
                success = false,
                data = null,
                errorMessage = "Shizuku not available"
            )
        }

        return try {
            val argsString = args.joinToString(" ")
            val command = "service call $serviceName $transactionCode $argsString"

            Logger.d(TAG, "📤 Executing: $command")
            val result = ShizukuShellExecutor.execute(command)

            if (result.success) {
                Logger.d(TAG, "✅ Success: ${result.output}")
                CommandResult(
                    success = true,
                    data = result.output
                )
            } else {
                Logger.e(TAG, "❌ Failed: ${result.errorOutput}")
                CommandResult(
                    success = false,
                    data = null,
                    errorMessage = result.errorOutput
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Command execution failed", e)
            CommandResult(
                success = false,
                data = null,
                errorMessage = e.message
            )
        }
    }

    /**
     * Чтение свойства TBox
     *
     * @param propertyName Имя свойства
     * @return Значение свойства или null
     */
    fun getProperty(propertyName: String): String? {
        Logger.d(TAG, "📋 Getting property: $propertyName")
        return ShizukuShellExecutor.getProp(propertyName)
    }

    /**
     * Запись свойства TBox
     *
     * @param propertyName Имя свойства
     * @param value Значение
     * @return true если успешно
     */
    fun setProperty(propertyName: String, value: String): Boolean {
        Logger.d(TAG, "📤 Setting property: $propertyName = $value")
        return ShizukuShellExecutor.setProp(propertyName, value)
    }

    /**
     * Отправка команды управления спойлером через Service Call
     *
     * Архитектура команды:
     * 1. Получаем HIDL интерфейс ITboxProtocol или ICarservice
     * 2. Вызываем setIntProp(propertyId, value) через service call
     * 3. Property ID: 0x6600022C (спойлер)
     * 4. Значение: 1=OPEN, 2=CLOSE
     *
     * @param action Действие (OPEN, CLOSE, STOP)
     * @return Результат выполнения
     *
     * МИТИГАЦИЯ РИСКОВ:
     * - Проверка здоровья системы перед вызовом
     * - Circuit Breaker для защиты от сбоев
     * - Safe Service Caller с таймаутом и повторами
     *
     * TODO: Заменить runBlocking на suspend functions (здесь и в строках 276, 344, 353, 370)
     *   - runBlocking может вызвать дедлоки при интеграции с корутинами
     *   - Рекомендуется: сделать sendSpoilerCommand suspend функцией и использовать корутины
     */
    fun sendSpoilerCommand(action: String): CommandResult {
        Logger.d(TAG, "📤 Spoiler command: $action")

        // === МИТИГАЦИЯ РИСКОВ: Шаг 1 - Проверка здоровья системы ===
        if (!healthMonitor.isSystemHealthy()) {
            Logger.e(TAG, "❌ System unhealthy - aborting command")
            return CommandResult(
                success = false,
                data = null,
                errorMessage = "System unhealthy"
            )
        }

        // === МИТИГАЦИЯ РИСКОВ: Шаг 2 - Circuit Breaker ===
        val canExecute = runBlocking { circuitBreaker.canExecute() }
        if (!canExecute) {
            Logger.w(TAG, "⚠️ Circuit Breaker OPEN - command blocked")
            return CommandResult(
                success = false,
                data = null,
                errorMessage = "Circuit breaker open"
            )
        }

        // Определяем значение команды
        val value = when (action.uppercase()) {
            "OPEN" -> 1
            "CLOSE" -> 2
            "STOP" -> 0
            else -> {
                Logger.e(TAG, "❌ Unknown action: $action")
                return CommandResult(
                    success = false,
                    data = null,
                    errorMessage = "Unknown action: $action"
                )
            }
        }

        // Пробуем несколько вариантов сервисов (от наиболее вероятного к наименее)
        val serviceCandidates = listOf(
            // Вариант 1: TBox сервис (приоритетный)
            SpoilerServiceConfig(
                serviceName = "tbox_protocol",
                transactionCode = 10, // SET_PROPERTY транзакция
                useInt32Args = true
            ),
            // Вариант 2: Car Property сервис
            SpoilerServiceConfig(
                serviceName = "car_property",
                transactionCode = 1,
                useInt32Args = true
            ),
            // Вариант 3: Vehicle сервис
            SpoilerServiceConfig(
                serviceName = "vehicle",
                transactionCode = 2,
                useInt32Args = true
            ),
            // Вариант 4: Общий car_service
            SpoilerServiceConfig(
                serviceName = "car_service",
                transactionCode = 1,
                useInt32Args = true
            )
        )

        // Пытаемся выполнить команду через каждый сервис с митигацией рисков
        for (config in serviceCandidates) {
            try {
                Logger.d(TAG, "🔍 Trying service: ${config.serviceName}")

                // === МИТИГАЦИЯ РИСКОВ: Шаг 3 - Safe Service Caller ===
                val command = if (config.useInt32Args) {
                    // Формат: service call <service> <code> i32 <propId> i32 <value>
                    "service call ${config.serviceName} ${config.transactionCode} i32 $SPOILER_PROPERTY_ID i32 $value"
                } else {
                    // Формат: service call <service> <code> <propId> <value>
                    "service call ${config.serviceName} ${config.transactionCode} $SPOILER_PROPERTY_ID $value"
                }

                // Выполняем с таймаутом и повторами
                val safeResult = runBlocking {
                    safeServiceCaller.executeWithRetry(command)
                }

                if (safeResult.success) {
                    Logger.d(TAG, "✅ Spoiler $action via ${config.serviceName} (${safeResult.retryCount} attempts, ${safeResult.executionTimeMs}ms)")
                    
                    // === МИТИГАЦИЯ РИСКОВ: Успех - обновляем счётчики ===
                    healthMonitor.onCallSuccess()
                    runBlocking { circuitBreaker.onSuccess() }
                    
                    return CommandResult(
                        success = true,
                        data = safeResult.output
                    )
                } else {
                    Logger.w(TAG, "⚠️ Failed via ${config.serviceName}: ${safeResult.errorMessage}")
                    // Продолжаем попытки с другими сервисами
                }
            } catch (e: Exception) {
                Logger.e(TAG, "❌ Exception via ${config.serviceName}: ${e.message}")
            }
        }

        // === МИТИГАЦИЯ РИСКОВ: Все попытки не удались ===
        healthMonitor.onCallFailure("All services failed")
        runBlocking { circuitBreaker.onFailure("All service candidates failed") }
        
        return CommandResult(
            success = false,
            data = null,
            errorMessage = "All service candidates failed for spoiler $action"
        )
    }

    /**
     * Конфигурация сервиса для управления спойлером
     */
    data class SpoilerServiceConfig(
        val serviceName: String,
        val transactionCode: Int,
        val useInt32Args: Boolean = true
    )

    /**
     * Получение статуса спойлера через Service Call
     *
     * @return Статус (OPEN, CLOSE, MOVING, UNKNOWN)
     */
    fun getSpoilerStatus(): String {
        Logger.d(TAG, "📋 Getting spoiler status")

        // Пробуем получить статус через getProperty
        val status = getProperty("persist.vehicle.spoiler.status")
        
        if (status != null) {
            return when (status.toIntOrNull()) {
                1 -> "OPEN"
                2 -> "CLOSE"
                0 -> "MOVING"
                else -> status
            }
        }

        // Если системное свойство не доступно, пробуем service call
        val result = serviceCall("car_property", 2, "i32", SPOILER_PROPERTY_ID.toString())

        return if (result.success && result.data != null) {
            parseSpoilerStatus(result.data)
        } else {
            "UNKNOWN"
        }
    }

    /**
     * Парсинг статуса спойлера из ответа сервиса
     */
    private fun parseSpoilerStatus(response: String): String {
        // Парсинг ответа от service call
        // Формат: Result{0x00000000} или Parcel{...}

        return when {
            // Проверяем числовые значения в ответе
            response.contains("0x1") || response.contains("101") -> "OPEN"
            response.contains("0x0") || response.contains("0") && !response.contains("0x") -> "CLOSE"
            response.contains("0x2") || response.contains("MOVING") -> "MOVING"
            
            // Проверяем позиционные значения (0-100%)
            response.contains("position=10") -> "OPEN"
            response.contains("position=0") -> "CLOSE"
            
            else -> {
                Logger.w(TAG, "Unknown status format: $response")
                "UNKNOWN"
            }
        }
    }

    /**
     * Тестирование подключения к сервису
     *
     * @param serviceName Имя сервиса для теста
     * @return true если сервис доступен
     */
    fun testService(serviceName: String): Boolean {
        Logger.d(TAG, "🔍 Testing service: $serviceName")

        val result = serviceCall(serviceName, 0)

        return result.success || result.errorMessage?.contains("not found") != true
    }

    /**
     * Получение списка доступных сервисов
     *
     * @return Список имен сервисов
     */
    fun listServices(): List<String> {
        Logger.d(TAG, "📋 Listing services...")

        val result = ShizukuShellExecutor.execute("service list")

        return if (result.success) {
            result.output
                .split("\n")
                .filter { it.contains(":") }
                .map { it.substringBefore(":").trim() }
        } else {
            emptyList()
        }
    }

    /**
     * Поиск сервиса управления автомобилем
     *
     * @return Имя сервиса или null
     */
    fun findCarService(): String? {
        Logger.d(TAG, "🔍 Searching for car property service...")

        val services = listServices()
        
        // Ищем сервисы по приоритету
        val priorityList = listOf(
            "tbox_protocol",
            "car_property",
            "vehicle",
            "car_service",
            "mega.car"
        )

        for (serviceName in priorityList) {
            if (services.any { it.contains(serviceName, ignoreCase = true) }) {
                Logger.d(TAG, "Found service: $serviceName")
                return serviceName
            }
        }

        // Если не найдено по имени, ищем по ключевым словам
        val carServices = services.filter { 
            it.contains("car", ignoreCase = true) || 
            it.contains("vehicle", ignoreCase = true) ||
            it.contains("tbox", ignoreCase = true)
        }

        return carServices.firstOrNull()
    }
    
    /**
     * Получить статистику митигации рисков (для отладки)
     * 
     * @return Строка со статистикой
     */
    fun getRiskMitigationStats(): String {
        val cbStats = circuitBreaker.getStats()
        val healthStats = healthMonitor.getStats()
        
        return buildString {
            appendLine("=== RISK MITIGATION STATS ===")
            appendLine("Circuit Breaker:")
            appendLine("  State: ${cbStats.state}")
            appendLine("  Failures: ${cbStats.failureCount}/${cbStats.failureThreshold}")
            appendLine("  Time to recovery: ${cbStats.timeUntilRecovery() / 1000}s")
            appendLine()
            appendLine("Health Monitor:")
            appendLine("  $healthStats")
            appendLine("==============================")
        }
    }
    
    /**
     * Сбросить Circuit Breaker (для ручного восстановления)
     */
    fun resetCircuitBreaker() {
        runBlocking { circuitBreaker.reset() }
        Logger.i(TAG, "Circuit Breaker manually reset")
    }
}
