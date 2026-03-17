package com.mazda.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import com.google.gson.Gson

/**
 * Контроллер для управления спойлером через Fake32960Server протокол
 *
 * Поддержка двух режимов:
 * 1. **JSON Mode** (рекомендуется): Отправка JSON Payload через Gson
 *    - Использует нативный протокол HuronCarSettings
 *    - CarPropertyService → Gson.toJson(Payload) → byte[]
 *    - Работает без root доступа
 *
 * 2. **TBox Mode** (legacy): Отправка 444-байтных пакетов
 *    - Устаревший формат для телеметрии
 *    - Может не работать с Fake32960Server
 *
 * Основано на анализе:
 * - SPOILER_PROTOCOL_DISCOVERY.md
 * - CarPropertyUtil.serialize() - JSON конвертация
 * - Fake32960Server.onChangeEvent() - получение данных
 */
class TBoxSpoilerController(
    private val context: Context,
    private val useJsonMode: Boolean = true  // JSON mode по умолчанию
) {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var inputStream: java.io.InputStream? = null
    private var readerThread: Thread? = null
    private var isReading = false
    private var isSpoilerOpen = false
    private var isMoving = false
    private var isConnected = false

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val movementDelay = 2200L // ~2.2 секунды на движение спойлера

    // JSON конвертер (используется в JSON mode)
    private val jsonConverter = SpoilerJsonConverter(useJsonMode)
    private val gson = Gson()

    // Callback для уведомлений об ответах сервера (для UI)
    var onServerResponse: ((ServerResponse) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "TBoxSpoilerController"
        // Реальный сервер автомобиля (Fake32960Server) - работает через localhost
        private const val SERVER_HOST = "127.0.0.1"
        private const val SERVER_PORT = 32960
        private const val CONNECTION_TIMEOUT_MS = 10000
        private const val READ_BUFFER_SIZE = 1024

        // TBox пакеты (используются только в TBox mode)
        private val SPOILER_OPEN_PACKET = PacketGenerator.createSpoilerOpenPacket()
        private val SPOILER_CLOSE_PACKET = PacketGenerator.createSpoilerClosePacket()

        // JSON команды (используются в JSON mode)
        private const val JSON_OPEN = """{"value":1,"valid":true,"relative":false,"time":0,"extension":null}"""
        private const val JSON_CLOSE = """{"value":2,"valid":true,"relative":false,"time":0,"extension":null}"""
        private const val JSON_FOLLOW_SPEED = """{"value":3,"valid":true,"relative":false,"time":0,"extension":null}"""
        private const val JSON_SPORT_MODE = """{"value":4,"valid":true,"relative":false,"time":0,"extension":null}"""
    }

    /**
     * Результат чтения ответа от сервера (для UI)
     */
    data class ServerResponse(
        val timestamp: Long,
        val packetType: PacketType,
        val rawBody: ByteArray,
        val rawHeader: String,
        val crcValid: Boolean,
        val bodyLength: Int
    ) {
        enum class PacketType { COMMAND, STATUS, UNKNOWN }

        /**
         * Человекочитаемое представление для UI
         */
        fun toUiString(): String {
            val typeStr = when (packetType) {
                PacketType.COMMAND -> "📤 Команда"
                PacketType.STATUS -> "📊 Статус"
                PacketType.UNKNOWN -> "❓ Неизвестно"
            }

            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                .format(Date(timestamp))

            return "[$timeStr] $typeStr | Body=${bodyLength}b | CRC=${if (crcValid) "OK" else "FAIL"}"
        }
    }

    private val logTimestamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    private val logFile: File by lazy {
        // Сохраняем логи в общедоступной директории для удобного извлечения
        val logDir = File("/sdcard/Download/MazdaControl")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        File(logDir, "tbox_log-$logTimestamp.txt")
    }

    /**
     * Запись лога в файл
     */
    private fun writeLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] $message\n"

            FileOutputStream(logFile, true).use { fos ->
                fos.write(logEntry.toByteArray())
            }
            Log.d(TAG, "Log written: $message")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log: ${e.message}")
        }
    }

    /**
     * Подключение к серверу Fake32960Server
     * @return true если подключение успешно
     */
    fun connect(): Boolean {
        if (isConnected) {
            Log.w(TAG, "Already connected")
            writeLog("⚠️ Already connected")
            return true
        }

        return try {
            Log.d(TAG, "=== START CONNECTION ===")
            writeLog("=== START CONNECTION ===")
            Log.d(TAG, "Server: $SERVER_HOST:$SERVER_PORT")
            writeLog("Server: $SERVER_HOST:$SERVER_PORT")

            // Создание сокета
            Log.d(TAG, "Creating socket...")
            writeLog("Creating socket...")
            socket = Socket()

            // Подключение с таймаутом
            Log.d(TAG, "Connecting with timeout ${CONNECTION_TIMEOUT_MS}ms...")
            writeLog("Connecting with timeout ${CONNECTION_TIMEOUT_MS}ms...")
            val connectStartTime = System.currentTimeMillis()
            socket?.connect(
                InetSocketAddress(SERVER_HOST, SERVER_PORT),
                CONNECTION_TIMEOUT_MS
            )
            val connectTime = System.currentTimeMillis() - connectStartTime
            Log.d(TAG, "Connected in ${connectTime}ms")
            writeLog("Connected in ${connectTime}ms")

            // Информация о подключении
            Log.d(TAG, "Connection details:")
            writeLog("Connection details:")
            Log.d(TAG, "  Local: ${socket?.localAddress?.hostAddress}:${socket?.localPort}")
            writeLog("  Local: ${socket?.localAddress?.hostAddress}:${socket?.localPort}")
            Log.d(TAG, "  Remote: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
            writeLog("  Remote: ${socket?.inetAddress?.hostAddress}:${socket?.port}")
            Log.d(TAG, "  Is connected: ${socket?.isConnected}")
            writeLog("  Is connected: ${socket?.isConnected}")

            // Создание потоков
            Log.d(TAG, "Creating streams...")
            writeLog("Creating streams...")
            outputStream = DataOutputStream(socket?.outputStream)
            inputStream = socket?.inputStream

            // Запуск потока чтения
            startReaderThread()

            isConnected = true
            handler.post {
                onConnectionStateChanged?.invoke(true)
            }

            Log.d(TAG, "=== CONNECTION SUCCESSFUL ===")
            writeLog("=== CONNECTION SUCCESSFUL ===")
            Log.d(TAG, "✅ Connected to Fake32960Server")
            writeLog("✅ Connected to Fake32960Server")
            true

        } catch (e: IOException) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ Connection error: ${e.message}")
            writeLog("❌ Connection error: ${e.message}")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            writeLog("Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            isConnected = false
            handler.post {
                onConnectionStateChanged?.invoke(false)
            }
            socket = null
            outputStream = null
            inputStream = null
            false
        } catch (e: Exception) {
            Log.e(TAG, "=== CONNECTION FAILED ===")
            writeLog("=== CONNECTION FAILED ===")
            Log.e(TAG, "❌ Unexpected error: ${e.message}")
            writeLog("❌ Unexpected error: ${e.message}")
            e.printStackTrace()
            isConnected = false
            handler.post {
                onConnectionStateChanged?.invoke(false)
            }
            socket = null
            outputStream = null
            inputStream = null
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
        if (!isConnected) {
            Log.w(TAG, "⚠️ Command ignored: not connected")
            writeLog("⚠️ Command ignored: not connected")
            return
        }

        if (useJsonMode) {
            sendJsonCommand(JSON_OPEN, "OPEN (JSON)")
        } else {
            sendPacket(SPOILER_OPEN_PACKET, "OPEN (TBox)")
        }

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
        if (!isConnected) {
            Log.w(TAG, "⚠️ Command ignored: not connected")
            writeLog("⚠️ Command ignored: not connected")
            return
        }

        if (useJsonMode) {
            sendJsonCommand(JSON_CLOSE, "CLOSE (JSON)")
        } else {
            sendPacket(SPOILER_CLOSE_PACKET, "CLOSE (TBox)")
        }

        isMoving = true
        handler.postDelayed({
            isSpoilerOpen = false
            isMoving = false
            Log.d(TAG, "✅ Movement complete: spoiler is now CLOSED")
            writeLog("✅ Movement complete: spoiler is now CLOSED")
        }, movementDelay)
    }

    /**
     * Установить режим спойлера "По скорости"
     */
    fun setFollowSpeedMode() {
        if (isMoving) {
            Log.w(TAG, "⚠️ Command ignored: already moving")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "⚠️ Command ignored: not connected")
            return
        }

        if (useJsonMode) {
            sendJsonCommand(JSON_FOLLOW_SPEED, "FOLLOW_SPEED (JSON)")
        } else {
            Log.w(TAG, "⚠️ FOLLOW_SPEED mode not supported in TBox mode")
        }

        isMoving = true
        handler.postDelayed({
            isMoving = false
            Log.d(TAG, "✅ Mode set: FOLLOW_SPEED")
            writeLog("✅ Mode set: FOLLOW_SPEED")
        }, movementDelay)
    }

    /**
     * Установить спортивный режим спойлера
     */
    fun setSportMode() {
        if (isMoving) {
            Log.w(TAG, "⚠️ Command ignored: already moving")
            return
        }
        if (!isConnected) {
            Log.w(TAG, "⚠️ Command ignored: not connected")
            return
        }

        if (useJsonMode) {
            sendJsonCommand(JSON_SPORT_MODE, "SPORT_MODE (JSON)")
        } else {
            Log.w(TAG, "⚠️ SPORT_MODE not supported in TBox mode")
        }

        isMoving = true
        handler.postDelayed({
            isMoving = false
            Log.d(TAG, "✅ Mode set: SPORT_MODE")
            writeLog("✅ Mode set: SPORT_MODE")
        }, movementDelay)
    }

    /**
     * Отправка JSON команды в Fake32960Server
     *
     * @param json JSON строка (Payload format)
     * @param actionName Название действия для лога
     */
    private fun sendJsonCommand(json: String, actionName: String) {
        executor.execute {
            try {
                Log.d(TAG, "=== SENDING JSON COMMAND ===")
                writeLog("")
                writeLog("═══════════════════════════════════════")
                writeLog("📤 ОТПРАВКА JSON КОМАНДЫ")
                writeLog("═══════════════════════════════════════")
                Log.d(TAG, "Action: $actionName")
                writeLog("🏷️ Action: $actionName")
                Log.d(TAG, "JSON: $json")
                writeLog("📄 JSON Payload: $json")

                // Конвертация JSON в byte[]
                val jsonBytes = json.toByteArray(Charsets.UTF_8)
                writeLog("📦 JSON bytes: ${jsonBytes.size} bytes")
                writeLog("")

                // Логирование в hex
                writeLog("🔢 Hex dump (${jsonBytes.size} bytes):")
                writeLog(hexDump(jsonBytes, 0))

                // Парсинг JSON для отображения ключевых полей
                try {
                    val payload = gson.fromJson(json, SpoilerJsonConverter.SpoilerPayload::class.java)
                    writeLog("")
                    writeLog("🎯 КЛЮЧЕВЫЕ ПОЛЯ JSON:")
                    writeLog("  • value: ${payload.value} (${getValueName(payload.value)})")
                    writeLog("  • valid: ${payload.valid}")
                    writeLog("  • relative: ${payload.relative}")
                    writeLog("  • time: ${payload.time} (${formatTimestamp(payload.time)})")
                    writeLog("  • extension: ${payload.extension ?: "null"}")
                } catch (e: Exception) {
                    writeLog("  ⚠️ Failed to parse JSON fields: ${e.message}")
                }

                writeLog("")
                writeLog("═══════════════════════════════════════")
                writeLog("")

                // Отправка в сокет
                Log.d(TAG, "Sending JSON...")
                writeLog("🚀 Отправка JSON...")
                val sendStartTime = System.currentTimeMillis()
                outputStream?.write(jsonBytes)
                outputStream?.flush()
                val sendTime = System.currentTimeMillis() - sendStartTime
                Log.d(TAG, "✅ JSON sent in ${sendTime}ms")
                writeLog("✅ JSON отправлен за ${sendTime}ms")
                writeLog("")

            } catch (e: IOException) {
                Log.e(TAG, "❌ Failed to send JSON command: ${e.message}")
                writeLog("❌ Ошибка отправки JSON: ${e.message}")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error sending JSON: ${e.message}")
                writeLog("❌ Неожиданная ошибка: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Получить название для value
     */
    private fun getValueName(value: Int): String {
        return when (value) {
            1 -> "OPEN"
            2 -> "CLOSE"
            3 -> "FOLLOW_SPEED"
            4 -> "SPORT_MODE"
            else -> "UNKNOWN"
        }
    }

    /**
     * Форматировать timestamp в читаемый вид
     */
    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Invalid timestamp"
        }
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
     * Отправка пакета (TBox mode)
     *
     * @param packet 444-байтный пакет
     * @param actionName Название действия для лога
     */
    private fun sendPacket(packet: ByteArray, actionName: String) {
        executor.execute {
            try {
                Log.d(TAG, "=== SENDING TBox COMMAND ===")
                writeLog("")
                writeLog("═══════════════════════════════════════")
                writeLog("📤 ОТПРАВКА TBox ПАКЕТА")
                writeLog("═══════════════════════════════════════")
                Log.d(TAG, "Action: $actionName")
                writeLog("🏷️ Action: $actionName")
                Log.d(TAG, "Packet size: ${packet.size} bytes")
                writeLog("📦 Packet size: ${packet.size} bytes (444 expected)")
                writeLog("")

                // Заголовок (30 байт)
                val header = packet.sliceArray(0 until 30)
                writeLog("📋 ЗАГОЛОВОК (30 байт):")
                writeLog(hexDump(header, 0))
                writeLog("")

                // Тело пакета (байты 30 до CRC)
                val bodyEnd = packet.size - 2
                val body = packet.sliceArray(30 until bodyEnd)
                writeLog("📦 ТЕЛО ПАКЕТА (${body.size} байт):")
                writeLog(hexDump(body, 30))
                writeLog("")

                // CRC (последние 2 байта)
                val crc = packet.sliceArray(bodyEnd until packet.size)
                writeLog("✅ CRC16: ${crc.joinToString(" ") { String.format("%02X", it) }}")
                writeLog("")

                // Ключевые байты для спойлера
                if (actionName.contains("OPEN") || actionName.contains("CLOSE")) {
                    writeLog("🎯 КЛЮЧЕВЫЕ БАЙТЫ:")
                    // Function Bytes (смещение ~288-295)
                    if (packet.size > 295) {
                        val functionBytes = packet.sliceArray(288 until 296)
                        writeLog("  Function Bytes [288-295]: ${functionBytes.joinToString(" ") { String.format("%02X", it) }}")
                    }
                    // Байт 355
                    if (packet.size > 355) {
                        writeLog("  Byte 355: ${String.format("%02X", packet[355])}")
                    }
                    // Байт 363
                    if (packet.size > 363) {
                        writeLog("  Byte 363: ${String.format("%02X", packet[363])}")
                    }
                    writeLog("")
                }

                writeLog("═══════════════════════════════════════")
                writeLog("")

                // Отправка
                Log.d(TAG, "Sending packet...")
                writeLog("🚀 Отправка пакета...")
                val sendStartTime = System.currentTimeMillis()
                outputStream?.write(packet)
                outputStream?.flush()
                val sendTime = System.currentTimeMillis() - sendStartTime
                Log.d(TAG, "✅ Command sent in ${sendTime}ms")
                writeLog("✅ Пакет отправлен за ${sendTime}ms")
                writeLog("")

            } catch (e: IOException) {
                Log.e(TAG, "❌ Failed to send command: ${e.message}")
                writeLog("❌ Ошибка отправки: ${e.message}")
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error sending command: ${e.message}")
                writeLog("❌ Неожиданная ошибка: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Hex dump массива байтов
     * @param data Массив байтов
     * @param offset Смещение для нумерации
     */
    private fun hexDump(data: ByteArray, offset: Int): String {
        val sb = StringBuilder()
        val perLine = 16
        
        for (i in data.indices step perLine) {
            // Адрес строки
            sb.append(String.format("%04X  ", offset + i))
            
            // Hex байты
            for (j in 0 until perLine) {
                if (i + j < data.size) {
                    sb.append(String.format("%02X ", data[i + j]))
                } else {
                    sb.append("   ")
                }
                
                // Разделитель после 8 байт
                if (j == 7) sb.append(" ")
            }
            
            // ASCII представление
            sb.append(" | ")
            for (j in 0 until perLine) {
                if (i + j < data.size) {
                    val b = data[i + j].toInt() and 0xFF
                    if (b in 0x20..0x7E) {
                        sb.append(b.toChar())
                    } else {
                        sb.append('.')
                    }
                } else {
                    sb.append(' ')
                }
            }
            sb.append("|\n")
        }
        
        return sb.toString()
    }

    /**
     * Проверка подключения
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Получить текущее состояние
     */
    fun isSpoilerOpen(): Boolean = isSpoilerOpen

    /**
     * Проверка, движется ли спойлер сейчас
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
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var bytesRead: Int
            var packetBuffer = ByteArrayOutputStream()
            var expectedBodyLength = -1
            var readingPacket = false

            try {
                while (isReading && inputStream != null) {
                    bytesRead = inputStream?.read(buffer) ?: -1

                    if (bytesRead > 0) {
                        val readTime = System.currentTimeMillis()
                        Log.d(TAG, "=== DATA RECEIVED ===")
                        writeLog("=== DATA RECEIVED ===")
                        Log.d(TAG, "Bytes read: $bytesRead")
                        writeLog("Bytes read: $bytesRead")

                        // Логирование полученных данных
                        val receivedHex = buffer.take(bytesRead).joinToString(" ") {
                            String.format("%02X", it)
                        }
                        Log.d(TAG, "Data: $receivedHex")
                        writeLog("📥 ПОЛУЧЕНЫ ДАННЫЕ:")
                        writeLog("═══════════════════════════════════════")
                        writeLog("Байт: $bytesRead")
                        writeLog("Hex: $receivedHex")
                        writeLog(hexDump(buffer.sliceArray(0 until bytesRead), 0))
                        writeLog("═══════════════════════════════════════")
                        writeLog("")

                        // Обработка данных
                        // Формат Fake32960Server: magic bytes 0x23 0x23
                        for (i in 0 until bytesRead) {
                            val byte = buffer[i]

                            if (!readingPacket) {
                                // Поиск начала пакета (magic byte 0x23)
                                if (byte == 0x23.toByte()) {
                                    Log.d(TAG, "Found first magic byte 0x23 at position $i")
                                    writeLog("Found first magic byte 0x23 at position $i")
                                    readingPacket = true
                                    packetBuffer = ByteArrayOutputStream()
                                    packetBuffer.write(byte.toInt())
                                }
                            } else {
                                packetBuffer.write(byte.toInt())

                                // Проверка второго magic byte (позиция 1)
                                if (packetBuffer.size() == 2) {
                                    val secondByte = packetBuffer.toByteArray()[1]
                                    if (secondByte == 0x23.toByte()) {
                                        Log.d(TAG, "Found second magic byte 0x23, Fake32960Server format confirmed")
                                        writeLog("Found second magic byte 0x23, Fake32960Server format confirmed")
                                    } else {
                                        Log.w(TAG, "Second byte is not 0x23, resetting packet buffer")
                                        writeLog("Second byte is not 0x23, resetting packet buffer")
                                        readingPacket = false
                                        packetBuffer = ByteArrayOutputStream()
                                        continue
                                    }
                                }

                                // Если уже прочитали заголовок (30 байт), читаем длину тела
                                if (packetBuffer.size() == 30) {
                                    val headerBytes = packetBuffer.toByteArray()
                                    if (TBoxFraming.isValidHeader(headerBytes)) {
                                        // Чтение длины тела из заголовка (байты 22-23, Little Endian)
                                        expectedBodyLength = TBoxFraming.getBodyLengthFromHeader(headerBytes)

                                        Log.d(TAG, "Expected body length: $expectedBodyLength bytes")
                                        writeLog("Expected body length: $expectedBodyLength bytes")

                                        // Проверка на разумный размер
                                        if (expectedBodyLength <= 0 || expectedBodyLength > 10000) {
                                            Log.e(TAG, "Invalid body length: $expectedBodyLength")
                                            writeLog("Invalid body length: $expectedBodyLength")
                                            readingPacket = false
                                            packetBuffer = ByteArrayOutputStream()
                                            continue
                                        }
                                    } else {
                                        Log.e(TAG, "Invalid protocol header")
                                        writeLog("Invalid protocol header")
                                        readingPacket = false
                                        packetBuffer = ByteArrayOutputStream()
                                        continue
                                    }
                                }

                                // Проверка完整性 пакета (заголовок + тело + CRC)
                                val expectedPacketSize = 30 + expectedBodyLength + 2
                                if (packetBuffer.size() >= expectedPacketSize && expectedBodyLength > 0) {
                                    val completePacket = packetBuffer.toByteArray()
                                    Log.d(TAG, "Complete packet received: ${completePacket.size} bytes")
                                    writeLog("Complete packet received: ${completePacket.size} bytes")

                                    // Разбор пакета
                                    val body = TBoxFraming.parsePacket(completePacket)
                                    if (body != null) {
                                        Log.d(TAG, "✅ Packet parsed successfully")
                                        writeLog("✅ Packet parsed successfully")

                                        // Логирование тела
                                        TBoxFraming.logPacket(body, "Parsed body", 64)
                                        writeLog("Parsed body (first 64): ${body.take(64).joinToString(" ") { String.format("%02X", it) }}")

                                        // Создание ответа для UI
                                        val response = ServerResponse(
                                            timestamp = System.currentTimeMillis(),
                                            packetType = ServerResponse.PacketType.STATUS,
                                            rawBody = body,
                                            rawHeader = completePacket.take(14).joinToString(" ") {
                                                String.format("%02X", it)
                                            },
                                            crcValid = true,
                                            bodyLength = body.size
                                        )

                                        // Уведомление UI
                                        handler.post {
                                            onServerResponse?.invoke(response)
                                        }
                                    } else {
                                        Log.e(TAG, "❌ Failed to parse packet")
                                        writeLog("❌ Failed to parse packet")
                                    }

                                    // Сброс буфера
                                    readingPacket = false
                                    packetBuffer = ByteArrayOutputStream()
                                    expectedBodyLength = -1
                                }
                            }
                        }
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "End of stream (connection closed)")
                        writeLog("End of stream (connection closed)")
                        isConnected = false
                        handler.post {
                            onConnectionStateChanged?.invoke(false)
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Reader thread error: ${e.message}")
                writeLog("Reader thread error: ${e.message}")
                e.printStackTrace()
                isConnected = false
                handler.post {
                    onConnectionStateChanged?.invoke(false)
                }
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
            readerThread?.join(2000)
            executor.shutdown()
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
            handler.removeCallbacksAndMessages(null)
            outputStream?.close()
            inputStream?.close()
            socket?.close()
            isConnected = false
            Log.d(TAG, "🔌 Disconnected from Fake32960Server")
            writeLog("🔌 Disconnected from Fake32960Server")
        } catch (e: IOException) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
            writeLog("Error during disconnect: ${e.message}")
            e.printStackTrace()
        }
    }
}
