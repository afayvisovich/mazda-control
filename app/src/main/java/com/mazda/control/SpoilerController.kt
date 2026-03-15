package com.mazda.control

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Контроллер для управления спойлером через TCP socket
 *
 * Подключение к локальному серверу автомобиля: 127.0.0.1:32960
 * Основано на SPOILER_APP_PLAN.md
 *
 * Порт 32960 использует TCP socket (подтверждено логами CallServerInterceptor)
 */
class SpoilerController {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: java.io.InputStream? = null
    private var readerThread: Thread? = null
    private var isReading = false
    private var isSpoilerOpen = false
    private var isMoving = false

    private val handler = Handler(Looper.getMainLooper())
    private val movementDelay = 2200L // ~2.2 секунды на движение спойлера

    // Callback для уведомлений об ответах сервера (для UI)
    var onServerResponse: ((ServerResponse) -> Unit)? = null

    companion object {
        private const val TAG = "SpoilerController"
        private const val SERVER_HOST = "127.0.0.1"
        private const val SERVER_PORT = 32960
        private const val LOG_DIR = "/data/local/tmp/"
        private const val PACKET_SIZE = 512
    }

    /**
     * Результат чтения ответа от сервера (для UI)
     */
    data class ServerResponse(
        val timestamp: Long,
        val packetType: PacketType,
        val mark: Int,
        val commandByte: Int?,
        val markerByte: Int?,
        val valueByte: Int?,
        val spoilerStatus: SpoilerStatus?,
        val rawHeader: String
    ) {
        enum class PacketType { COMMAND, STATUS, UNKNOWN }
        enum class SpoilerStatus { OPEN, CLOSED, MOVING, UNKNOWN }

        /**
         * Человекочитаемое представление для UI
         */
        fun toUiString(): String {
            val typeStr = when (packetType) {
                PacketType.COMMAND -> "📤 Команда"
                PacketType.STATUS -> "📊 Статус"
                PacketType.UNKNOWN -> "❓ Неизвестно"
            }

            val statusStr = when (spoilerStatus) {
                SpoilerStatus.OPEN -> "ОТКРЫТ ✅"
                SpoilerStatus.CLOSED -> "ЗАКРЫТ 🔒"
                SpoilerStatus.MOVING -> "ДВИЖЕНИЕ ⏳"
                SpoilerStatus.UNKNOWN -> ""
                null -> ""
            }

            val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))

            return "[$timeStr] $typeStr | Mark=0x${String.format("%02X", mark)} $statusStr"
        }
    }

    private val logTimestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    private val logFilePath = "$LOG_DIR/log-$logTimestamp.txt"

    /**
     * Запись лога в файл
     */
    private fun writeLog(message: String) {
        try {
            val logDir = File(LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logFilePath)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"
            
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logEntry.toByteArray())
            }
            Log.d(TAG, "Log written: $message")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Подключение к локальному серверу автомобиля через TCP socket
     * @return true если подключение успешно
     */
    public fun connect(): Boolean {
        return try {
            Log.d(TAG, "=== START CONNECTION ===")
            Log.d(TAG, "Server: $SERVER_HOST:$SERVER_PORT")
            writeLog("=== START CONNECTION ===")
            writeLog("Server: $SERVER_HOST:$SERVER_PORT")
            
            // Шаг 1: Создание сокета
            Log.d(TAG, "Step 1: Creating socket...")
            writeLog("Step 1: Creating socket...")
            socket = Socket()
            Log.d(TAG, "Socket created: ${socket?.hashCode()}")
            writeLog("Socket created: ${socket?.hashCode()}")
            
            // Шаг 2: Подключение
            Log.d(TAG, "Step 2: Connecting to $SERVER_HOST:$SERVER_PORT...")
            writeLog("Step 2: Connecting to $SERVER_HOST:$SERVER_PORT...")
            val connectStartTime = System.currentTimeMillis()
            socket?.connect(java.net.InetSocketAddress(SERVER_HOST, SERVER_PORT), 5000)
            val connectTime = System.currentTimeMillis() - connectStartTime
            Log.d(TAG, "Connected in ${connectTime}ms")
            writeLog("Connected in ${connectTime}ms")
            
            // Шаг 3: Информация о подключении
            Log.d(TAG, "Step 3: Connection details:")
            writeLog("Step 3: Connection details:")
            Log.d(TAG, "  Local address: ${socket?.localAddress?.hostAddress}:${socket?.localPort}")
            writeLog("  Local address: ${socket?.localAddress?.hostAddress}:${socket?.localPort}")
            Log.d(TAG, "  Remote address: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
            writeLog("  Remote address: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
            Log.d(TAG, "  Is connected: ${socket?.isConnected}")
            writeLog("  Is connected: ${socket?.isConnected}")
            Log.d(TAG, "  Is bound: ${socket?.isBound}")
            writeLog("  Is bound: ${socket?.isBound}")
            
            // Шаг 4: Создание выходного потока
            Log.d(TAG, "Step 4: Creating output stream...")
            writeLog("Step 4: Creating output stream...")
            outputStream = socket?.outputStream?.let {
                Log.d(TAG, "Output stream created: ${it.hashCode()}")
                writeLog("Output stream created: ${it.hashCode()}")
                DataOutputStream(it)
            }

            // Шаг 5: Создание входного потока и запуск чтения
            Log.d(TAG, "Step 5: Creating input stream and starting reader thread...")
            writeLog("Step 5: Creating input stream and starting reader thread...")
            inputStream = socket?.inputStream
            startReaderThread()

            Log.d(TAG, "=== CONNECTION SUCCESSFUL ===")
            writeLog("=== CONNECTION SUCCESSFUL ===")
            Log.d(TAG, "✅ Connected successfully")
            writeLog("✅ Connected successfully")
            true
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ UnknownHostException: ${e.message}")
            writeLog("❌ UnknownHostException: ${e.message}")
            Log.e(TAG, "Server address $SERVER_HOST:$SERVER_PORT not found")
            writeLog("Server address $SERVER_HOST:$SERVER_PORT not found")
            e.printStackTrace()
            socket = null
            outputStream = null
            false
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ ConnectException: ${e.message}")
            writeLog("❌ ConnectException: ${e.message}")
            Log.e(TAG, "Server refused connection on $SERVER_HOST:$SERVER_PORT")
            writeLog("Server refused connection on $SERVER_HOST:$SERVER_PORT")
            Log.e(TAG, "Possible reasons:")
            writeLog("Possible reasons:")
            Log.e(TAG, "  1. Server not running")
            writeLog("  1. Server not running")
            Log.e(TAG, "  2. Wrong port")
            writeLog("  2. Wrong port")
            Log.e(TAG, "  3. Firewall blocking")
            writeLog("  3. Firewall blocking")
            e.printStackTrace()
            socket = null
            outputStream = null
            false
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ SocketTimeoutException: ${e.message}")
            writeLog("❌ SocketTimeoutException: ${e.message}")
            Log.e(TAG, "Connection timeout after 5000ms")
            writeLog("Connection timeout after 5000ms")
            e.printStackTrace()
            socket = null
            outputStream = null
            false
        } catch (e: IOException) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ Connection failed: ${e.message}")
            writeLog("❌ Connection failed: ${e.message}")
            Log.e(TAG, "IOException type: ${e.javaClass.simpleName}")
            writeLog("IOException type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            socket = null
            outputStream = null
            false
        } catch (e: Exception) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ Unexpected error: ${e.message}")
            writeLog("❌ Unexpected error: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            writeLog("Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            socket = null
            outputStream = null
            false
        }
    }

    /**
     * Открыть спойлер
     */
    fun open() {
        if (isMoving) {
            Log.w(TAG, "⚠️ Command ignored: already moving")
            writeLog("⚠️ Command ignored: already moving")
            return
        }
        sendCommand(PacketGenerator.createSpoilerOpenPacket(), "OPEN")
        isMoving = true
        handler.postDelayed({
            isSpoilerOpen = true
            isMoving = false
            Log.d(TAG, "✅ Movement complete: spoiler is now OPEN")
            writeLog("✅ Movement complete: spoiler is now OPEN")
        }, movementDelay)
    }

    /**
     * Закрыть спойлер
     */
    fun close() {
        if (isMoving) {
            Log.w(TAG, "⚠️ Command ignored: already moving")
            writeLog("⚠️ Command ignored: already moving")
            return
        }
        sendCommand(PacketGenerator.createSpoilerClosePacket(), "CLOSE")
        isMoving = true
        handler.postDelayed({
            isSpoilerOpen = false
            isMoving = false
            Log.d(TAG, "✅ Movement complete: spoiler is now CLOSED")
            writeLog("✅ Movement complete: spoiler is now CLOSED")
        }, movementDelay)
    }

    /**
     * Переключение состояния спойлера
     */
    fun toggle() {
        if (isSpoilerOpen) {
            close()
        } else {
            open()
        }
    }

    /**
     * Отправка команды
     */
    private fun sendCommand(packet: ByteArray, actionName: String) {
        try {
            Log.d(TAG, "=== SENDING COMMAND ===")
            writeLog("=== SENDING COMMAND ===")
            Log.d(TAG, "Action: $actionName")
            writeLog("Action: $actionName")
            Log.d(TAG, "Packet size: ${packet.size} bytes")
            writeLog("Packet size: ${packet.size} bytes")
            
            // Логирование первых 30 байт (заголовок)
            val headerBytes = packet.take(30).toByteArray()
            val headerHex = headerBytes.joinToString(" ") { String.format("%02X", it) }
            Log.d(TAG, "Header (30 bytes): $headerHex")
            writeLog("Header (30 bytes): $headerHex")
            
            // Логирование ключевых байтов
            Log.d(TAG, "Key bytes:")
            writeLog("Key bytes:")
            Log.d(TAG, "  Byte[2] (mark): 0x${String.format("%02X", packet[2])}")
            writeLog("  Byte[2] (mark): 0x${String.format("%02X", packet[2])}")
            if (packet.size > 355) {
                Log.d(TAG, "  Byte[355] (command): 0x${String.format("%02X", packet[355])}")
                writeLog("  Byte[355] (command): 0x${String.format("%02X", packet[355])}")
            }
            if (packet.size > 363) {
                Log.d(TAG, "  Byte[363] (marker): 0x${String.format("%02X", packet[363])}")
                writeLog("  Byte[363] (marker): 0x${String.format("%02X", packet[363])}")
            }
            if (packet.size > 365) {
                Log.d(TAG, "  Byte[365] (value): 0x${String.format("%02X", packet[365])}")
                writeLog("  Byte[365] (value): 0x${String.format("%02X", packet[365])}")
            }
            
            // Отправка
            Log.d(TAG, "Sending packet...")
            writeLog("Sending packet...")
            val sendStartTime = System.currentTimeMillis()
            outputStream?.write(packet)
            outputStream?.flush()
            val sendTime = System.currentTimeMillis() - sendStartTime
            Log.d(TAG, "✅ Command sent in ${sendTime}ms")
            writeLog("✅ Command sent in ${sendTime}ms")
            Log.d(TAG, "✅ Command $actionName sent (512 bytes)")
            writeLog("✅ Command $actionName sent (512 bytes)")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to send command: ${e.message}")
            writeLog("❌ Failed to send command: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            writeLog("Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error sending command: ${e.message}")
            writeLog("❌ Unexpected error sending command: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            writeLog("Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }

    /**
     * Проверка подключения
     */
    fun isConnected(): Boolean = socket?.isConnected == true

    /**
     * Получить текущее состояние
     */
    fun isSpoilerOpen(): Boolean = isSpoilerOpen

    /**
     * Проверка, движется ли спойлер сейчас
     * Эмулируется на основе таймера (~2.2 сек)
     */
    fun isMoving(): Boolean = isMoving

    /**
     * Поток для чтения ответов от сервера
     */
    private fun startReaderThread() {
        if (isReading) {
            Log.w(TAG, "Reader thread already running")
            writeLog("Reader thread already running")
            return
        }

        isReading = true
        readerThread = Thread {
            Log.d(TAG, "Reader thread started")
            writeLog("Reader thread started")
            try {
                val buffer = ByteArray(PACKET_SIZE)
                var bytesRead: Int

                while (isReading && inputStream != null) {
                    bytesRead = inputStream?.read(buffer) ?: -1

                    if (bytesRead > 0) {
                        val readTime = System.currentTimeMillis()
                        Log.d(TAG, "=== RESPONSE RECEIVED ===")
                        writeLog("=== RESPONSE RECEIVED ===")
                        Log.d(TAG, "Bytes read: $bytesRead")
                        writeLog("Bytes read: $bytesRead")
                        Log.d(TAG, "Read time: ${readTime}")
                        writeLog("Read time: ${readTime}")

                        // Логирование первых 30 байт (заголовок ответа)
                        val headerBytes = buffer.take(30).toByteArray()
                        val headerHex = headerBytes.joinToString(" ") { String.format("%02X", it) }
                        Log.d(TAG, "Response header (30 bytes): $headerHex")
                        writeLog("Response header (30 bytes): $headerHex")

                        // Логирование ключевых байтов ответа
                        Log.d(TAG, "Response key bytes:")
                        writeLog("Response key bytes:")
                        Log.d(TAG, "  Byte[2] (mark): 0x${String.format("%02X", buffer[2])}")
                        writeLog("  Byte[2] (mark): 0x${String.format("%02X", buffer[2])}")
                        if (bytesRead > 355) {
                            Log.d(TAG, "  Byte[355] (command): 0x${String.format("%02X", buffer[355])}")
                            writeLog("  Byte[355] (command): 0x${String.format("%02X", buffer[355])}")
                        }
                        if (bytesRead > 363) {
                            Log.d(TAG, "  Byte[363] (marker): 0x${String.format("%02X", buffer[363])}")
                            writeLog("  Byte[363] (marker): 0x${String.format("%02X", buffer[363])}")
                        }
                        if (bytesRead > 365) {
                            Log.d(TAG, "  Byte[365] (value): 0x${String.format("%02X", buffer[365])}")
                            writeLog("  Byte[365] (value): 0x${String.format("%02X", buffer[365])}")
                        }

                        // Определение типа пакета
                        val mark = buffer[2]
                        val packetType = when (mark) {
                            0x02.toByte() -> ServerResponse.PacketType.COMMAND
                            0x03.toByte() -> ServerResponse.PacketType.STATUS
                            else -> ServerResponse.PacketType.UNKNOWN
                        }

                        // Определение состояния спойлера
                        val spoilerStatus = if (bytesRead > 365) {
                            val status = buffer[365]
                            when (status) {
                                0x00.toByte() -> ServerResponse.SpoilerStatus.CLOSED
                                0x64.toByte() -> ServerResponse.SpoilerStatus.OPEN
                                else -> ServerResponse.SpoilerStatus.UNKNOWN
                            }
                        } else {
                            null
                        }

                        // Создание объекта ответа
                        val response = ServerResponse(
                            timestamp = System.currentTimeMillis(),
                            packetType = packetType,
                            mark = mark.toInt() and 0xFF,
                            commandByte = if (bytesRead > 355) buffer[355].toInt() and 0xFF else null,
                            markerByte = if (bytesRead > 363) buffer[363].toInt() and 0xFF else null,
                            valueByte = if (bytesRead > 365) buffer[365].toInt() and 0xFF else null,
                            spoilerStatus = spoilerStatus,
                            rawHeader = headerHex
                        )

                        // Уведомление UI (через Handler)
                        handler.post {
                            onServerResponse?.invoke(response)
                        }

                        // Логирование типа пакета
                        when (packetType) {
                            ServerResponse.PacketType.COMMAND -> {
                                Log.d(TAG, "✅ Packet type: COMMAND (Mark=0x02)")
                                writeLog("✅ Packet type: COMMAND (Mark=0x02)")
                            }
                            ServerResponse.PacketType.STATUS -> {
                                Log.d(TAG, "✅ Packet type: STATUS (Mark=0x03)")
                                writeLog("✅ Packet type: STATUS (Mark=0x03)")
                            }
                            ServerResponse.PacketType.UNKNOWN -> {
                                Log.w(TAG, "⚠️ Unknown packet type: Mark=0x${String.format("%02X", mark)}")
                                writeLog("⚠️ Unknown packet type: Mark=0x${String.format("%02X", mark)}")
                            }
                        }

                        // Обновление состояния спойлера на основе ответа
                        if (bytesRead > 365) {
                            val spoilerStatus = buffer[365]
                            if (spoilerStatus == 0x00.toByte()) {
                                isSpoilerOpen = false
                                Log.d(TAG, "Spoiler status: CLOSED")
                                writeLog("Spoiler status: CLOSED")
                            } else if (spoilerStatus == 0x64.toByte()) {
                                isSpoilerOpen = true
                                Log.d(TAG, "Spoiler status: OPEN")
                                writeLog("Spoiler status: OPEN")
                            }
                        }
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "End of stream (connection closed)")
                        writeLog("End of stream (connection closed)")
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Reader thread error: ${e.message}")
                writeLog("Reader thread error: ${e.message}")
                e.printStackTrace()
            } finally {
                isReading = false
                Log.d(TAG, "Reader thread stopped")
                writeLog("Reader thread stopped")
            }
        }
        readerThread?.start()
    }

    /**
     * Отключение
     */
    fun disconnect() {
        try {
            isReading = false
            readerThread?.join(2000) // Wait up to 2 seconds for thread to stop
            handler.removeCallbacksAndMessages(null)
            outputStream?.close()
            inputStream?.close()
            socket?.close()
            Log.d(TAG, "🔌 Disconnected")
            writeLog("🔌 Disconnected")
        } catch (e: IOException) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
            writeLog("Error during disconnect: ${e.message}")
            e.printStackTrace()
        }
    }
}
