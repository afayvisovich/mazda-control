package com.mazda.control

import android.util.Log
import kotlinx.coroutines.*
import java.io.DataOutputStream
import java.net.Socket

/**
 * Mock-контроллер для тестирования без реального автомобиля
 * Эмулирует отправку команд и изменение состояния спойлера
 */
class MockSpoilerController : SpoilerControllerInterface {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var isSpoilerOpen = false
    private var isMoving = false
    private var mockJob: Job? = null

    companion object {
        private const val TAG = "MockSpoilerController"
        private const val SERVER_HOST = "127.0.0.1"
        private const val SERVER_PORT = 32960
        private const val SPOILER_MOVEMENT_TIME_MS = 2200L // ~2.2 сек как в реальности
    }

    override fun connect(): Boolean {
        Log.d(TAG, "🔧 MOCK MODE: Подключение к эмулятору")

        // Пытаемся подключиться для проверки, но не требуем успеха
        return try {
            socket = Socket(SERVER_HOST, SERVER_PORT)
            outputStream = DataOutputStream(socket!!.outputStream)
            Log.d(TAG, "✅ MOCK: Подключено к $SERVER_HOST:$SERVER_PORT (тестовое)")
            true
        } catch (e: Exception) {
            Log.d(TAG, "⚠️ MOCK: Сервер не найден (ожидаемо в тесте), работаем в режиме эмуляции")
            false
        }
    }

    override fun open() {
        if (isMoving) {
            Log.d(TAG, "⚠️ MOCK: Спойлер уже движется")
            return
        }
        
        if (isSpoilerOpen) {
            Log.d(TAG, "ℹ️ MOCK: Спойлер уже открыт")
            return
        }
        
        sendCommand(PacketGenerator.createSpoilerOpenPacket(), "OPEN")
        simulateMovement {
            isSpoilerOpen = true
            Log.d(TAG, "✅ MOCK: Спойлер ОТКРЫТ")
        }
    }

    override fun close() {
        if (isMoving) {
            Log.d(TAG, "⚠️ MOCK: Спойлер уже движется")
            return
        }
        
        if (!isSpoilerOpen) {
            Log.d(TAG, "ℹ️ MOCK: Спойлер уже закрыт")
            return
        }
        
        sendCommand(PacketGenerator.createSpoilerClosePacket(), "CLOSE")
        simulateMovement {
            isSpoilerOpen = false
            Log.d(TAG, "✅ MOCK: Спойлер ЗАКРЫТ")
        }
    }

    override fun toggle() {
        if (isSpoilerOpen) {
            close()
        } else {
            open()
        }
    }

    private fun sendCommand(packet: ByteArray, actionName: String) {
        isMoving = true
        
        // Отправляем реальный пакет если подключены
        try {
            outputStream?.write(packet)
            outputStream?.flush()
            Log.d(TAG, "📤 MOCK: Команда $actionName отправлена (512 байт)")
        } catch (e: Exception) {
            Log.d(TAG, "📤 MOCK: Команда $actionName эмулирована (нет подключения)")
        }
    }

    private fun simulateMovement(onComplete: () -> Unit) {
        Log.d(TAG, "⏱️ MOCK: Начало движения (~2.2 сек)")
        
        mockJob = CoroutineScope(Dispatchers.Main).launch {
            delay(SPOILER_MOVEMENT_TIME_MS)
            isMoving = false
            onComplete()
        }
    }

    override fun isConnected(): Boolean = socket?.isConnected == true

    override fun isSpoilerOpen(): Boolean = isSpoilerOpen

    override fun isMoving(): Boolean = isMoving

    override fun disconnect() {
        mockJob?.cancel()
        try {
            outputStream?.close()
            socket?.close()
            Log.d(TAG, "🔌 MOCK: Отключено")
        } catch (e: Exception) {
            Log.d(TAG, "MOCK: Ошибка при отключении: ${e.message}")
        }
    }
}

/**
 * Интерфейс для контроллера спойлера
 */
interface SpoilerControllerInterface {
    fun connect(): Boolean
    fun open()
    fun close()
    fun toggle()
    fun isConnected(): Boolean
    fun isSpoilerOpen(): Boolean
    fun isMoving(): Boolean
    fun disconnect()
}
