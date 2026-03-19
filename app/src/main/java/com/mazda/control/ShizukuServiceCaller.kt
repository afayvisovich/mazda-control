package com.mazda.control

import android.util.Log
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Shizuku Service Caller для вызова системных сервисов Mazda
 * 
 * Использует Shizuku для вызова service call
 */
object ShizukuServiceCaller {
    
    private const val TAG = "ShizukuServiceCaller"
    
    /**
     * Вызвать сервис через Shizuku
     * 
     * @param serviceName Имя сервиса (например "mega.controller")
     * @param code Код команды
     * @param propId ID свойства
     * @param value Значение
     * @return true если успешно
     */
    fun callService(serviceName: String, code: Int, propId: Int, value: Int): Boolean {
        return try {
            // Проверяем Shizuku
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return false
            }
            
            // Получаем сервис через ServiceManager
            val serviceBinder = getServiceBinder(serviceName)
            
            if (serviceBinder == null) {
                Log.e(TAG, "❌ Service $serviceName not found!")
                return false
            }
            
            // Вызываем транзакцию
            // code * 1000000 + propId * 1000 + value
            val data = (code * 1000000) + (propId * 1000) + value
            
            Log.d(TAG, "📞 Calling $serviceName: code=0x${code.toString(16)}, propId=0x${propId.toString(16)}, value=$value, data=$data")
            
            // Используем транзакцию через Binder
            val result = transact(serviceBinder, code, data)
            
            Log.d(TAG, "✅ Result: $result")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calling service", e)
            false
        }
    }
    
    /**
     * Получить свойство из сервиса
     * 
     * @param serviceName Имя сервиса
     * @param propId ID свойства
     * @return Значение свойства
     */
    fun getProperty(serviceName: String, propId: Int): Int {
        return try {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return 0
            }
            
            val serviceBinder = getServiceBinder(serviceName)
            
            if (serviceBinder == null) {
                Log.e(TAG, "❌ Service $serviceName not found!")
                return 0
            }
            
            Log.d(TAG, "📖 Getting property $propId from $serviceName")
            
            // TODO: Реальный вызов getProperty
            0
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting property", e)
            0
        }
    }
    
    /**
     * Установить свойство в сервисе
     * 
     * @param serviceName Имя сервиса
     * @param propId ID свойства
     * @param value Значение
     * @return true если успешно
     */
    fun setProperty(serviceName: String, propId: Int, value: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return false
            }
            
            val serviceBinder = getServiceBinder(serviceName)
            
            if (serviceBinder == null) {
                Log.e(TAG, "❌ Service $serviceName not found!")
                return false
            }
            
            Log.d(TAG, "✏️ Setting property $propId = $value in $serviceName")
            
            // TODO: Реальный вызов setProperty
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting property", e)
            false
        }
    }
    
    /**
     * Получить Binder сервиса через ServiceManager
     */
    private fun getServiceBinder(serviceName: String): IBinder? {
        return try {
            // Используем reflection для вызова ServiceManager.getService()
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod: Method = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, serviceName) as? IBinder
            binder
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting service binder", e)
            null
        }
    }
    
    /**
     * Выполнить транзакцию через Binder
     */
    private fun transact(binder: IBinder, code: Int, data: Int): Boolean {
        return try {
            // Используем reflection для вызова Binder.transact()
            val transactMethod: Method = IBinder::class.java.getMethod(
                "transact",
                Int::class.javaPrimitiveType,
                android.os.Parcel::class.java,
                android.os.Parcel::class.java,
                Int::class.javaPrimitiveType
            )
            
            // Создаём Parcel для данных
            val dataParcel = android.os.Parcel.obtain()
            val replyParcel = android.os.Parcel.obtain()
            
            try {
                // Записываем данные
                dataParcel.writeInt(data)
                
                // Выполняем транзакцию
                val result = transactMethod.invoke(binder, code, dataParcel, replyParcel, 0) as Boolean
                result
            } finally {
                // Освобождаем Parcel
                dataParcel.recycle()
                replyParcel.recycle()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in transact", e)
            false
        }
    }
    
    /**
     * Интерфейс Binder (для reflection)
     */
    interface IBinder : android.os.IBinder
}
