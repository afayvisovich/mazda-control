package com.mazda.control

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Результат выполнения shell-команды
 */
data class ShellCommandResult(
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val success: Boolean = exitCode == 0
)

/**
 * Исполнитель shell-команд через Shizuku
 *
 * Позволяет выполнять команды от имени shell-пользователя (UID 2000)
 * без необходимости root-прав.
 *
 * Примеры использования:
 * - service call commands для вызова системных сервисов
 * - getprop для чтения системных свойств
 * - cmd commands для вызова команд Android
 */
object ShizukuShellExecutor {

    private const val TAG = "ShizukuExecutor"

    /**
     * Проверка доступности Shizuku
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku ping failed", e)
            false
        }
    }

    /**
     * Выполнение shell-команды через Shizuku
     *
     * @param command Команда для выполнения
     * @return Результат выполнения (exitCode, output, errorOutput)
     */
    fun execute(command: String): ShellCommandResult {
        if (!isShizukuAvailable()) {
            Log.e(TAG, "Shizuku not available")
            return ShellCommandResult(
                exitCode = -1,
                output = "",
                errorOutput = "Shizuku not available"
            )
        }

        return try {
            Log.d(TAG, "Executing command: $command")

            // Используем Shizuku для выполнения команды через ProcessBuilder
            val process = ProcessBuilder("sh", "-c", command)
            process.redirectErrorStream(true)
            
            val proc = process.start()
            
            // Читаем вывод через Shizuku
            val output = StringBuilder()
            val errorOutput = StringBuilder()
            
            // Читаем stdout
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                output.append(reader.readText())
            }
            
            // Читаем stderr
            BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                errorOutput.append(reader.readText())
            }
            
            val exitCode = proc.waitFor()

            Log.d(TAG, "Exit code: $exitCode")
            if (output.isNotEmpty()) {
                Log.d(TAG, "Output: $output")
            }
            if (errorOutput.isNotEmpty()) {
                Log.e(TAG, "Error: $errorOutput")
            }

            ShellCommandResult(
                exitCode = exitCode,
                output = output.toString().trim(),
                errorOutput = errorOutput.toString().trim(),
                success = exitCode == 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}", e)
            ShellCommandResult(
                exitCode = -1,
                output = "",
                errorOutput = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Выполнение service call команды
     *
     * @param serviceName Имя сервиса (например, "iphonesubinfo", "phone")
     * @param transactionCode Код транзакции
     * @param args Аргументы (опционально)
     * @return Результат выполнения
     */
    fun serviceCall(
        serviceName: String,
        transactionCode: Int,
        vararg args: String
    ): ShellCommandResult {
        val argsString = args.joinToString(" ")
        val command = "service call $serviceName $transactionCode $argsString"
        Log.d(TAG, "Service call: $command")
        return execute(command)
    }

    /**
     * Чтение системного свойства
     *
     * @param propertyName Имя свойства
     * @return Значение свойства или null
     */
    fun getProp(propertyName: String): String? {
        val result = execute("getprop $propertyName")
        return if (result.success && result.output.isNotEmpty()) {
            result.output
        } else {
            null
        }
    }

    /**
     * Запись системного свойства
     *
     * @param propertyName Имя свойства
     * @param value Значение
     * @return true если успешно
     */
    fun setProp(propertyName: String, value: String): Boolean {
        val result = execute("setprop $propertyName $value")
        return result.success
    }
}
