package com.mazda.control

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mazda.control.IShellExecutor
import org.json.JSONObject
import rikka.shizuku.Shizuku

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
 * Исполнитель shell-команд через Shizuku UserService.
 *
 * Использует UserService вместо deprecated Shizuku.newProcess().
 * UserService выполняется как отдельный процесс с UID 2000 (shell).
 *
 * Примеры использования:
 * - service call commands для вызова системных сервисов
 * - getprop для чтения системных свойств
 * - cmd commands для вызова команд Android
 */
object ShizukuShellExecutor {

    private const val TAG = "ShizukuExecutor"

    private var shellExecutorBinder: IShellExecutor? = null
    private var isBinding = false

    // Lock for thread-safe access to shellExecutorBinder
    private val binderLock = Any()

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
     * Проверка доступности ShellExecutor сервиса
     */
    fun isServiceBound(): Boolean {
        synchronized(binderLock) {
            return shellExecutorBinder?.asBinder()?.isBinderAlive == true
        }
    }

    /**
     * Привязка к UserService для выполнения shell команд.
     * Вызывать при старте приложения после проверки Shizuku.
     *
     * @param context Application context
     * @param callback Вызывается после успешного биндинга
     */
    fun bind(context: Context, callback: ((Boolean) -> Unit)? = null) {
        if (isBinding) {
            Log.w(TAG, "Already binding...")
            return
        }

        if (!isShizukuAvailable()) {
            Log.e(TAG, "Shizuku not available")
            callback?.invoke(false)
            return
        }

        if (isServiceBound()) {
            Log.d(TAG, "Already bound")
            callback?.invoke(true)
            return
        }

        isBinding = true

        val userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellExecutorService::class.java.name)
        )
            .processNameSuffix("shell-executor")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        Log.d(TAG, "Binding to ShellExecutorService...")

        try {
            Shizuku.bindUserService(userServiceArgs, object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                    Log.i(TAG, "ShellExecutorService connected")
                    synchronized(binderLock) {
                        shellExecutorBinder = IShellExecutor.Stub.asInterface(binder)
                    }
                    isBinding = false
                    callback?.invoke(true)
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    Log.w(TAG, "ShellExecutorService disconnected")
                    synchronized(binderLock) {
                        shellExecutorBinder = null
                    }
                    isBinding = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind", e)
            isBinding = false
            callback?.invoke(false)
        }
    }

    /**
     * Отвязка от UserService.
     * Вызывать при остановке приложения.
     */
    fun unbind() {
        if (!isServiceBound()) return

        try {
            val userServiceArgs = Shizuku.UserServiceArgs(
                ComponentName(MazdaControlApp.instance.packageName, ShellExecutorService::class.java.name)
            )
                .processNameSuffix("shell-executor")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            Shizuku.unbindUserService(userServiceArgs, object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {}
                override fun onServiceDisconnected(componentName: ComponentName) {}
            }, false)

            synchronized(binderLock) {
                shellExecutorBinder = null
            }
            Log.d(TAG, "Unbound from ShellExecutorService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind", e)
        }
    }

    /**
     * Выполнение shell-команды через UserService.
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

        // Get binder reference under lock to avoid race condition
        val binder: IShellExecutor?
        synchronized(binderLock) {
            binder = shellExecutorBinder
        }

        if (binder == null) {
            Log.e(TAG, "ShellExecutorService not bound")
            return ShellCommandResult(
                exitCode = -1,
                output = "",
                errorOutput = "ShellExecutorService not bound. Call bind() first."
            )
        }

        return try {
            Log.d(TAG, "Executing via UserService: $command")

            val resultJson = binder.executeFull(command)
            val json = JSONObject(resultJson)

            ShellCommandResult(
                exitCode = json.getInt("exitCode"),
                output = json.getString("output"),
                errorOutput = json.getString("errorOutput"),
                success = json.getBoolean("success")
            )
        } catch (e: RemoteException) {
            Log.e(TAG, "Remote execution failed", e)
            ShellCommandResult(
                exitCode = -1,
                output = "",
                errorOutput = e.message ?: "Remote execution failed"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed", e)
            ShellCommandResult(
                exitCode = -1,
                output = "",
                errorOutput = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Выполнение service call команды.
     *
     * @param serviceName Имя сервиса (например, "mega.controller")
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
     * Чтение системного свойства.
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
     * Запись системного свойства.
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
