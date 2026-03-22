package com.mazda.control

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Прямой вызов сервисов через Shizuku + Binder
 *
 * Использует ServiceManager для получения Binder сервиса
 * И выполняет транзакции через Binder.transact()
 *
 * ВАЖНО: Для работы требуется:
 * 1. Запущенный Shizuku (ADB или root)
 * 2. Сервис должен быть зарегистрирован в ServiceManager
 * 3. Сервис может требовать system permissions
 */
object ShizukuBinderCaller {

    private const val TAG = "ShizukuBinderCaller"
    
    /**
     * Проверка system прав (эмуляция для Mock Mode)
     * На реальном Head Unit сервис может отказать в доступе без system UID
     */
    private var mockSystemPermissions = true  // Для Mock Mode
    
    /**
     * Установить режим проверки прав
     * @param mock true = эмулировать успешную проверку (для эмулятора)
     *             false = строгая проверка (для Head Unit)
     */
    fun setMockSystemPermissions(mock: Boolean) {
        mockSystemPermissions = mock
        Log.d(TAG, "🔐 Mock system permissions: $mock")
    }
    
    /**
     * Вызов сервиса через Binder транзакцию
     * 
     * @param serviceName Имя сервиса в ServiceManager
     * @param transactionCode Код транзакции
     * @param data Данные для передачи
     * @return true если успешно
     */
    fun callService(serviceName: String, transactionCode: Int, data: Int): Boolean {
        return try {
            Log.d(TAG, "📞 callService: $serviceName, code=$transactionCode, data=$data")
            
            // 1. Проверяем Shizuku
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return false
            }
            
            // 2. Получаем Binder сервиса
            val binder = getServiceBinder(serviceName)
            if (binder == null) {
                Log.e(TAG, "❌ Service '$serviceName' not found")
                return false
            }
            
            Log.d(TAG, "✅ Got binder for $serviceName")
            
            // 3. Выполняем транзакцию
            val result = transact(binder, transactionCode, data)
            
            if (result) {
                Log.d(TAG, "✅ Transaction successful")
            } else {
                Log.e(TAG, "❌ Transaction failed")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in callService", e)
            false
        }
    }
    
    /**
     * Получить свойство через сервис
     */
    fun getProperty(serviceName: String, propId: Int): Int {
        return try {
            Log.d(TAG, "📖 getProperty: $serviceName, propId=$propId")
            
            if (!Shizuku.pingBinder()) {
                return 0
            }
            
            val binder = getServiceBinder(serviceName)
            if (binder == null) {
                return 0
            }
            
            var dataParcel: Parcel? = null
            var replyParcel: Parcel? = null
            
            return try {
                dataParcel = Parcel.obtain()
                replyParcel = Parcel.obtain()
                
                dataParcel.writeInt(propId)
                
                val transactMethod = IBinder::class.java.getMethod(
                    "transact",
                    Int::class.javaPrimitiveType,
                    Parcel::class.java,
                    Parcel::class.java,
                    Int::class.javaPrimitiveType
                )
                
                transactMethod.invoke(binder, 2, dataParcel, replyParcel, 0)
                
                replyParcel.setDataPosition(0)
                val responseCode = replyParcel.readInt()
                
                if (responseCode == 0) {
                    Log.e(TAG, "❌ Service returned error for getProperty")
                    return 0
                }
                
                val value = replyParcel.readInt()
                Log.d(TAG, "📤 Property value: $value")
                value
            } finally {
                dataParcel?.recycle()
                replyParcel?.recycle()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting property", e)
            0
        }
    }
    
    /**
     * Установить свойство через сервис
     */
    fun setProperty(serviceName: String, propId: Int, value: Int): Boolean {
        return try {
            Log.d(TAG, "✏️ setProperty: $serviceName, propId=$propId, value=$value")
            
            if (!Shizuku.pingBinder()) {
                return false
            }
            
            val binder = getServiceBinder(serviceName)
            if (binder == null) {
                return false
            }
            
            // Для setProperty используем транзакцию с кодом 3
            val data = (propId shl 16) or (value and 0xFFFF)
            
            transact(binder, 3, data)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting property", e)
            false
        }
    }
    
    /**
     * Проверить доступность сервиса через shell
     *
     * Используем service list через ShizukuShellExecutor вместо прямого reflection,
     * т.к. на Head Unit приложение может не иметь прямого доступа к ServiceManager
     */
    fun isServiceAvailable(serviceName: String): Boolean {
        return try {
            // Используем service list для проверки наличия сервиса
            val result = ShizukuShellExecutor.execute("service list")
            if (result.success) {
                val hasService = result.output.contains(serviceName)
                Log.d(TAG, "Service '$serviceName' found: $hasService")
                return hasService
            }

            // Fallback: попробуем service call с кодом 0 (ping)
            val pingResult = ShizukuShellExecutor.serviceCall(serviceName, 0)
            val available = pingResult.success || pingResult.errorOutput.isEmpty()
            Log.d(TAG, "Service '$serviceName' ping result: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking service availability", e)
            false
        }
    }
    
    /**
     * Получить Binder сервиса через ServiceManager
     */
    fun getServiceBinder(serviceName: String): IBinder? {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            
            val binder = getServiceMethod.invoke(null, serviceName) as? IBinder
            
            if (binder != null) {
                Log.d(TAG, "✅ Got binder for $serviceName")
            } else {
                Log.w(TAG, "⚠️ Binder is null for $serviceName")
            }
            
            binder
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting binder for $serviceName", e)
            null
        }
    }
    
    /**
     * Выполнить транзакцию через Binder
     */
    private fun transact(binder: IBinder, code: Int, data: Int): Boolean {
        var dataParcel: Parcel? = null
        var replyParcel: Parcel? = null

        return try {
            val transactMethod = IBinder::class.java.getMethod(
                "transact",
                Int::class.javaPrimitiveType,
                Parcel::class.java,
                Parcel::class.java,
                Int::class.javaPrimitiveType
            )

            dataParcel = Parcel.obtain()
            replyParcel = Parcel.obtain()

            dataParcel.writeInt(data)

            val result = transactMethod.invoke(binder, code, dataParcel, replyParcel, 0) as? Boolean

            // Читаем ответ
            replyParcel.setDataPosition(0)
            val responseCode = replyParcel.readInt()

            if (responseCode == 0) {
                Log.e(TAG, "❌ Service returned error (permission denied?)")
                return false
            }

            result ?: false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in transact", e)
            false
        } finally {
            dataParcel?.recycle()
            replyParcel?.recycle()
        }
    }

    /**
     * Выполнить транзакцию через Binder с Parcel (для MockMegaController)
     */
    fun transact(serviceName: String, code: Int, dataParcel: Parcel, replyParcel: Parcel): Boolean {
        return try {
            Log.d(TAG, "📞 transact: $serviceName, code=$code")

            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return false
            }

            val binder = getServiceBinder(serviceName)
            if (binder == null) {
                Log.e(TAG, "❌ Service '$serviceName' not found")
                return false
            }

            val transactMethod = IBinder::class.java.getMethod(
                "transact",
                Int::class.javaPrimitiveType,
                Parcel::class.java,
                Parcel::class.java,
                Int::class.javaPrimitiveType
            )

            val result = transactMethod.invoke(binder, code, dataParcel, replyParcel, 0) as? Boolean

            // Читаем ответ
            replyParcel.setDataPosition(0)
            val responseCode = replyParcel.readInt()

            if (responseCode == 0) {
                Log.e(TAG, "❌ Service returned error (permission denied?)")
                return false
            }

            result ?: false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in transact", e)
            false
        }
    }
}
