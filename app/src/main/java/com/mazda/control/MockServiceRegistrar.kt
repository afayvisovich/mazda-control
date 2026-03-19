package com.mazda.control

import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Утилита для регистрации Mock-сервиса через Shizuku
 * 
 * Позволяет зарегистрировать сервис в ServiceManager даже без system permissions
 * используя Shizuku с его расширенными правами
 */
object MockServiceRegistrar {
    
    private const val TAG = "MockServiceRegistrar"
    
    /**
     * Зарегистрировать сервис в ServiceManager через Shizuku
     * 
     * @param serviceName Имя сервиса
     * @param binder Binder сервиса
     * @param allowIsolated true = разрешить изолированным процессам доступ
     * @param dumpPriority приоритет для dump
     */
    fun registerService(
        serviceName: String, 
        binder: IBinder,
        allowIsolated: Boolean = false,
        dumpPriority: Int = -1
    ): Boolean {
        return try {
            Log.d(TAG, "📝 Registering service: $serviceName (allowIsolated=$allowIsolated, dumpPriority=$dumpPriority)")
            
            // Проверяем Shizuku
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                return false
            }
            
            // Получаем ServiceManager через Shizuku
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            
            // Пробуем новый метод addService с дополнительными параметрами (Android 11+)
            val addServiceMethod = try {
                serviceManagerClass.getMethod(
                    "addService",
                    String::class.java,
                    IBinder::class.java,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            } catch (e: NoSuchMethodException) {
                // Старый метод без дополнительных параметров
                Log.w(TAG, "⚠️ Using legacy addService method")
                serviceManagerClass.getMethod(
                    "addService",
                    String::class.java,
                    IBinder::class.java
                )
            }
            
            // Вызываем через Shizuku (у Shizuku есть права)
            val result = if (addServiceMethod.parameterCount == 4) {
                addServiceMethod.invoke(null, serviceName, binder, allowIsolated, dumpPriority)
            } else {
                addServiceMethod.invoke(null, serviceName, binder)
            }
            
            Log.i(TAG, "✅ Service '$serviceName' registered via Shizuku")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register service: ${e.message}", e)
            false
        }
    }
    
    /**
     * Проверить, зарегистрирован ли сервис
     */
    fun isServiceRegistered(serviceName: String): Boolean {
        return try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, serviceName) as? IBinder
            val result = binder != null
            Log.d(TAG, "Service '$serviceName' is ${if (result) "registered" else "NOT registered"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking service registration", e)
            false
        }
    }
}
