package com.mazda.control

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log

/**
 * Mock-сервис для эмуляции mega.controller
 * 
 * Регистрируется в ServiceManager как "mega.controller"
 * Отвечает на транзакции как реальный контроллер Mazda
 * 
 * ЭМУЛИРУЕТ ПРОВЕРКУ SYSTEM ПРАВ:
 * - Проверяет наличие system signature
 * - Проверяет UID (должен быть system)
 * - Проверяет permissions
 * 
 * Устанавливается в систему эмулятора для тестирования связки:
 * Приложение → Shizuku → Mock-сервис
 */
class MockMegaService : Service() {
    
    companion object {
        private const val TAG = "MockMegaService"
        private const val SERVICE_NAME = "mega.controller"
        
        // Коды транзакций
        private const val TRANSACTION_CALL_SERVICE = 1
        private const val TRANSACTION_GET_PROPERTY = 2
        private const val TRANSACTION_SET_PROPERTY = 3
        
        // Состояние спойлера
        private var spoilerPosition = 0  // 0=CLOSE, 1=OPEN, 2=STOP
        
        // System права (эмуляция)
        private const val REQUIRE_SYSTEM_UID = true
        private const val REQUIRE_SYSTEM_SIGNATURE = true
    }
    
    private val binder = MockBinder()
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "🚀 MockMegaService bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 MockMegaService created")

        // Проверяем права при создании
        checkSystemPermissions()

        // Пытаемся зарегистрировать сервис через Shizuku (если доступен)
        // Если Shizuku недоступен - сервис работает через прямой bindService
        registerServiceViaShizuku()
    }
    
    /**
     * Проверка system прав (как на реальном Head Unit)
     */
    private fun checkSystemPermissions() {
        val callingUid = android.os.Process.myUid()
        val isSystemUid = callingUid == 1000 || callingUid <= 1999
        
        Log.d(TAG, "🔐 Checking system permissions...")
        Log.d(TAG, "   UID: $callingUid")
        Log.d(TAG, "   Is System UID: $isSystemUid")
        
        if (REQUIRE_SYSTEM_UID && !isSystemUid) {
            Log.w(TAG, "⚠️ Service running with non-system UID: $callingUid")
            Log.w(TAG, "💡 On real Head Unit, this service requires system UID (1000-1999)")
            Log.w(TAG, "💡 Install as system app: /system/priv-app/ or /system/app/")
        }
        
        // Проверяем signature
        val hasSystemSignature = checkSystemSignature()
        Log.d(TAG, "   Has System Signature: $hasSystemSignature")
        
        if (REQUIRE_SYSTEM_SIGNATURE && !hasSystemSignature) {
            Log.w(TAG, "⚠️ Service does not have system signature")
            Log.w(TAG, "💡 On real Head Unit, sign with platform key")
        }
        
        // Проверяем permissions
        val hasModifyPhoneState = checkPermission("android.permission.MODIFY_PHONE_STATE")
        val hasSystemAlertWindow = checkPermission("android.permission.SYSTEM_ALERT_WINDOW")
        
        Log.d(TAG, "   MODIFY_PHONE_STATE: ${if (hasModifyPhoneState) "✅" else "❌"}")
        Log.d(TAG, "   SYSTEM_ALERT_WINDOW: ${if (hasSystemAlertWindow) "✅" else "❌"}")
        
        if (!hasModifyPhoneState || !hasSystemAlertWindow) {
            Log.w(TAG, "⚠️ Missing system permissions")
            Log.w(TAG, "💡 On real Head Unit, these permissions are required")
        }
        
        Log.i(TAG, "🔐 System permission check complete")
    }
    
    /**
     * Проверка system signature
     */
    @Suppress("DEPRECATION")
    private fun checkSystemSignature(): Boolean {
        return try {
            val pm: PackageManager = packageManager
            val info = pm.getPackageInfo(packageName, 0)

            // Проверяем signature (на эмуляторе всегда false)
            val signatures = info.signatures
            signatures != null && signatures.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking signature", e)
            false
        }
    }
    
    /**
     * Проверка наличия permission
     */
    private fun checkPermission(permission: String): Boolean {
        return try {
            val result = checkCallingOrSelfPermission(permission)
            result == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking permission: $permission", e)
            false
        }
    }
    
    /**
     * Зарегистрировать сервис в ServiceManager через Shizuku
     */
    private fun registerServiceViaShizuku() {
        val success = MockServiceRegistrar.registerService(SERVICE_NAME, binder)
        
        if (success) {
            Log.i(TAG, "✅ Service '$SERVICE_NAME' registered via Shizuku")
        } else {
            Log.e(TAG, "❌ Failed to register service via Shizuku")
        }
    }
    
    /**
     * Binder для нашего сервиса
     */
    inner class MockBinder : Binder() {
        
        override fun getInterfaceDescriptor(): String {
            return SERVICE_NAME
        }
        
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            Log.d(TAG, "📥 onTransact: code=$code, flags=$flags")
            
            // Проверяем права вызывающего (как на реальном Head Unit)
            val callingUid = Binder.getCallingUid()
            val callingPid = Binder.getCallingPid()
            
            Log.d(TAG, "   Calling UID: $callingUid")
            Log.d(TAG, "   Calling PID: $callingPid")
            
            // На эмуляторе пропускаем проверку UID
            // На реальном Head Unit здесь была бы проверка:
            // if (callingUid != 1000 && !isSystemApp()) return false
            
            return try {
                when (code) {
                    TRANSACTION_CALL_SERVICE -> handleCallService(data, reply)
                    TRANSACTION_GET_PROPERTY -> handleGetProperty(data, reply)
                    TRANSACTION_SET_PROPERTY -> handleSetProperty(data, reply)
                    else -> super.onTransact(code, data, reply, flags)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in onTransact", e)
                false
            }
        }
        
        /**
         * Обработка вызова callService(code, propId, value)
         */
        private fun handleCallService(data: Parcel, reply: Parcel?): Boolean {
            val fullData = data.readInt()
            
            // Разбираем данные
            val code = (fullData shr 16) and 0xFFFF
            val propId = (fullData shr 8) and 0xFF
            val value = fullData and 0xFF
            
            Log.d(TAG, "📞 callService: code=0x${code.toString(16)}, propId=0x${propId.toString(16)}, value=$value")
            
            // Проверяем права (эмуляция)
            if (!checkCallerPermissions()) {
                Log.e(TAG, "❌ Permission denied for caller")
                reply?.writeInt(0)  // Возвращаем ошибку
                return true
            }
            
            // Обрабатываем команду спойлера
            if (code == 0x0001 && propId == 0x12) {
                when (value) {
                    0 -> {
                        spoilerPosition = 0
                        Log.d(TAG, "🔒 Spoiler CLOSED")
                    }
                    1 -> {
                        spoilerPosition = 1
                        Log.d(TAG, "🔓 Spoiler OPEN")
                    }
                    2 -> {
                        spoilerPosition = 2
                        Log.d(TAG, "✋ Spoiler STOP")
                    }
                }
            }
            
            // Возвращаем успех
            reply?.writeInt(1)
            return true
        }
        
        /**
         * Проверка прав вызывающего
         */
        private fun checkCallerPermissions(): Boolean {
            val callingUid = Binder.getCallingUid()
            
            // На эмуляторе пропускаем все вызовы
            // На реальном Head Unit здесь была бы проверка:
            // return callingUid == 1000 || isTrustedApp(callingUid)
            
            return true
        }
        
        /**
         * Обработка вызова getProperty(propId)
         */
        private fun handleGetProperty(data: Parcel, reply: Parcel?): Boolean {
            val propId = data.readInt()
            Log.d(TAG, "📖 getProperty: propId=0x${propId.toString(16)}")
            
            if (!checkCallerPermissions()) {
                Log.e(TAG, "❌ Permission denied for caller")
                reply?.writeInt(0)
                return true
            }
            
            // Возвращаем текущее состояние спойлера
            val value = when (propId) {
                0x12345678 -> spoilerPosition
                else -> 0
            }
            
            Log.d(TAG, "📤 Returning: $value")
            reply?.writeInt(value)
            return true
        }
        
        /**
         * Обработка вызова setProperty(propId, value)
         */
        private fun handleSetProperty(data: Parcel, reply: Parcel?): Boolean {
            val fullData = data.readInt()
            val propId = (fullData shr 16) and 0xFFFF
            val value = fullData and 0xFFFF
            
            Log.d(TAG, "✏️ setProperty: propId=0x${propId.toString(16)}, value=$value")
            
            if (!checkCallerPermissions()) {
                Log.e(TAG, "❌ Permission denied for caller")
                reply?.writeInt(0)
                return true
            }
            
            // Устанавливаем свойство
            if (propId == 0x12345678) {
                spoilerPosition = value
                Log.d(TAG, "✅ Spoiler position set to: $value")
            }
            
            reply?.writeInt(1)
            return true
        }
    }
    
    /**
     * Получить текущее состояние спойлера
     */
    fun getSpoilerPosition(): Int {
        return spoilerPosition
    }
    
    /**
     * Сбросить состояние
     */
    fun reset() {
        spoilerPosition = 0
        Log.d(TAG, "🔄 Spoiler reset to CLOSED")
    }
}
