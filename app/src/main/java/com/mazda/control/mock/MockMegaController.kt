package com.mazda.control.mock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.mazda.control.IMegaController
import com.mazda.control.MockMegaService
import com.mazda.control.ShizukuBinderCaller
import rikka.shizuku.Shizuku

/**
 * Mock контроллер для эмуляции спойлера Mazda
 *
 * Использует Shizuku если доступен, иначе прямую связь через bindService
 * Проверяет что Shizuku работает и команды передаются через него
 *
 * Цепочка: Приложение → Shizuku (если есть) → MockMegaService
 */
object MockMegaController : IMegaController {

    private const val TAG = "MockMegaController"
    private const val SERVICE_NAME = "mega.controller"
    private const val TRANSACTION_CALL_SERVICE = 1

    private var context: Context? = null
    private var callCount = 0
    private var spoilerPosition = 0  // 0=CLOSE, 1=OPEN, 2=STOP
    private var mockBinder: IBinder? = null
    private var useShizuku = false

    // Ссылка на ServiceConnection для возможности отписки
    private var serviceConnection: ServiceConnection? = null

    /**
     * Инициализация
     */
    fun init(ctx: Context) {
        context = ctx?.applicationContext
        
        // Проверяем доступен ли Shizuku
        useShizuku = Shizuku.pingBinder()
        Log.d(TAG, "🔍 Shizuku available: $useShizuku")
        
        if (!useShizuku) {
            Log.w(TAG, "⚠️ Shizuku not available, using direct bindService")
            connect()
        } else {
            Log.i(TAG, "✅ Using Shizuku for MockMegaService calls")
        }
    }

    /**
     * Подключиться к MockMegaService напрямую (если Shizuku недоступен)
     */
    private fun connect() {
        val ctx = context ?: run {
            Log.e(TAG, "❌ Context not initialized!")
            return
        }

        Log.d(TAG, "🔌 Connecting to MockMegaService via bindService...")

        val intent = Intent(ctx, MockMegaService::class.java)

        // Создаём именованный ServiceConnection и сохраняем ссылку
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mockBinder = service
                Log.i(TAG, "✅ Connected to MockMegaService (direct)")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mockBinder = null
                Log.w(TAG, "⚠️ Disconnected from MockMegaService")
            }
        }

        ctx.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
    }

    /**
     * Отключиться от MockMegaService
     */
    fun disconnect() {
        val ctx = context ?: return
        serviceConnection?.let { connection ->
            try {
                ctx.unbindService(connection)
                Log.d(TAG, "🔌 Unbound from MockMegaService")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Failed to unbind: ${e.message}")
            }
        }
        serviceConnection = null
        mockBinder = null
    }

    /**
     * Проверка доступности Shizuku
     */
    private fun checkShizuku(): Boolean {
        if (useShizuku) {
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "❌ Shizuku not running!")
                useShizuku = false
                return false
            }
            Log.d(TAG, "✅ Shizuku available")
            return true
        }
        return mockBinder != null
    }

    override fun callService(code: Int, propId: Int, value: Int): Boolean {
        callCount++

        Log.d(TAG, "📞 callService #$callCount: code=0x${code.toString(16)}, propId=0x${propId.toString(16)}, value=$value")

        if (!checkShizuku()) {
            Log.e(TAG, "❌ Cannot call service - not connected")
            return false
        }

        try {
            val data = ((code and 0xFFFF) shl 16) or ((propId and 0xFFFF) shl 8) or (value and 0xFF)

            val dataParcel = Parcel.obtain()
            val replyParcel = Parcel.obtain()

            try {
                dataParcel.writeInt(data)

                // Вызываем сервис через Shizuku или напрямую
                val result = if (useShizuku) {
                    Log.d(TAG, "📡 Using Shizuku for transaction")
                    ShizukuBinderCaller.transact(SERVICE_NAME, TRANSACTION_CALL_SERVICE, dataParcel, replyParcel)
                } else {
                    Log.d(TAG, "📡 Using direct binder for transaction")
                    mockBinder?.transact(TRANSACTION_CALL_SERVICE, dataParcel, replyParcel, 0) == true
                }

                replyParcel.setDataPosition(0)
                val responseCode = replyParcel.readInt()

                val success = (result == true) && (responseCode == 1)

                if (success) {
                    Log.d(TAG, "✅ Service call successful via ${if (useShizuku) "Shizuku" else "direct binder"}")
                } else {
                    Log.e(TAG, "❌ Service call failed")
                }

                return success

            } finally {
                dataParcel.recycle()
                replyParcel.recycle()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in callService", e)
            return false
        }
    }

    override fun getProperty(propId: Int): Int {
        Log.d(TAG, "📖 getProperty: propId=0x${propId.toString(16)}")

        if (!checkShizuku()) {
            return 0
        }

        try {
            val dataParcel = Parcel.obtain()
            val replyParcel = Parcel.obtain()

            try {
                dataParcel.writeInt(propId)

                val result = if (useShizuku) {
                    Log.d(TAG, "📡 Using Shizuku for getProperty")
                    ShizukuBinderCaller.transact(SERVICE_NAME, 2, dataParcel, replyParcel)
                } else {
                    Log.d(TAG, "📡 Using direct binder for getProperty")
                    mockBinder?.transact(2, dataParcel, replyParcel, 0) == true
                }

                replyParcel.setDataPosition(0)
                val value = replyParcel.readInt()

                Log.d(TAG, "✅ Got property via ${if (useShizuku) "Shizuku" else "direct binder"}: $value")
                return value

            } finally {
                dataParcel.recycle()
                replyParcel.recycle()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting property", e)
            return 0
        }
    }

    override fun setProperty(propId: Int, value: Int): Boolean {
        Log.d(TAG, "✏️ setProperty: propId=0x${propId.toString(16)}, value=$value")

        if (!checkShizuku()) {
            return false
        }

        try {
            val dataParcel = Parcel.obtain()
            val replyParcel = Parcel.obtain()

            try {
                val data = (propId shl 16) or (value and 0xFFFF)
                dataParcel.writeInt(data)

                val result = if (useShizuku) {
                    Log.d(TAG, "📡 Using Shizuku for setProperty")
                    ShizukuBinderCaller.transact(SERVICE_NAME, 3, dataParcel, replyParcel)
                } else {
                    Log.d(TAG, "📡 Using direct binder for setProperty")
                    mockBinder?.transact(3, dataParcel, replyParcel, 0) == true
                }

                replyParcel.setDataPosition(0)
                val responseCode = replyParcel.readInt()

                val success = (result == true) && (responseCode == 1)

                if (success) {
                    Log.d(TAG, "✅ Property set via ${if (useShizuku) "Shizuku" else "direct binder"}")
                }

                return success

            } finally {
                dataParcel.recycle()
                replyParcel.recycle()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting property", e)
            return false
        }
    }

    override fun setSpoiler(position: Int): Boolean {
        Log.d(TAG, "🚗 setSpoiler: position=$position")

        when (position) {
            0 -> {
                Log.d(TAG, "🔒 Closing spoiler...")
                spoilerPosition = 0
            }
            1 -> {
                Log.d(TAG, "🔓 Opening spoiler...")
                spoilerPosition = 1
            }
            2 -> {
                Log.d(TAG, "✋ Stopping spoiler")
                spoilerPosition = 2
            }
            else -> {
                Log.e(TAG, "⚠️ Invalid position: $position")
                return false
            }
        }

        // Вызываем сервис через Shizuku для обновления состояния
        val success = callService(TRANSACTION_CALL_SERVICE, 0x12345678, position)

        if (success) {
            Log.d(TAG, "✅ Spoiler command sent via Shizuku: $spoilerPosition")
        } else {
            Log.e(TAG, "❌ Failed to send spoiler command via Shizuku")
        }

        return success
    }

    override fun getStats(): Map<String, Any> {
        return mapOf(
            "callCount" to callCount,
            "spoilerPosition" to spoilerPosition,
            "mode" to "MOCK",
            "shizukuUsed" to useShizuku,
            "shizukuAvailable" to Shizuku.pingBinder(),
            "directBinderConnected" to (mockBinder != null)
        )
    }

    fun reset() {
        callCount = 0
        spoilerPosition = 0
        Log.d(TAG, "🔄 Reset")
    }
}
