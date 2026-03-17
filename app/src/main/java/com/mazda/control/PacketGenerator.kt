package com.mazda.control

import com.google.gson.Gson
import java.util.Calendar

/**
 * Генератор пакетов для управления спойлером
 *
 * Поддержка двух режимов:
 * 1. **TBox Mode** (legacy): 444-байтные пакеты для телеметрии
 * 2. **JSON Mode** (рекомендуется): JSON Payload для HuronCarSettings
 *
 * Формат TBox пакета (444 байта):
 * - Header: 30 байт (magic 0x23 0x23)
 * - Body: 412 байт (0x019C)
 * - CRC16: 2 байта
 *
 * JSON Payload формат:
 * ```json
 * {
 *   "value": 1,  // 1=OPEN, 2=CLOSE, 3=FOLLOW_SPEED, 4=SPORT_MODE
 *   "valid": true,
 *   "relative": false,
 *   "time": 1234567890,
 *   "extension": null
 * }
 * ```
 *
 * Основано на анализе:
 * - SPOILER_PROTOCOL_DISCOVERY.md
 * - CarPropertyUtil.serialize() - Gson.toJson(Payload)
 * - Fake32960Server.onChangeEvent() - получение byte[]
 */
object PacketGenerator {

    // VIN из захваченных пакетов
    private val VIN = byteArrayOf(
        0x4C, 0x56, 0x52, 0x48, 0x44, 0x41, 0x45, 0x4A,
        0x38, 0x52, 0x4E, 0x30, 0x31, 0x33, 0x35, 0x37, 0x39
    )
    
    // Размер тела пакета
    private const val BODY_SIZE = 412
    
    // Полный размер пакета: header (30) + body (412) + crc (2)
    private const val PACKET_SIZE = 30 + BODY_SIZE + 2

    /**
     * Создать пакет для открытия спойлера
     *
     * Function Bytes: BF 11 0D 06 27 21 20 01
     * - Component: 0x06 (spoiler)
     * - Action: 0x21 (open)
     */
    fun createSpoilerOpenPacket(): ByteArray {
        val body = ByteArray(BODY_SIZE)
        
        // === BODY (412 байт) ===
        
        // Префикс
        body[0] = 0x01.toByte()
        body[1] = 0x02.toByte()
        body[2] = 0x03.toByte()
        body[3] = 0x01.toByte()

        // Property ID (всегда 0x00000000)
        body[4] = 0x00.toByte()
        body[5] = 0x00.toByte()
        body[6] = 0x00.toByte()
        body[7] = 0x00.toByte()

        // Function Bytes (8-15)
        // BF 11 0D 06 27 21 20 01
        body[8] = 0xBF.toByte()
        body[9] = 0x11.toByte()
        body[10] = 0x0D.toByte()
        body[11] = 0x06.toByte()  // Component: spoiler
        body[12] = 0x27.toByte()
        body[13] = 0x21.toByte()  // Action: open
        body[14] = 0x20.toByte()
        body[15] = 0x01.toByte()

        // === КЛЮЧЕВЫЕ БАЙТЫ ===
        body[325] = 0x2B.toByte()  // Командный байт (всегда 0x2B)
        body[333] = 0x5A.toByte()  // Command marker
        body[335] = 0x00.toByte()  // OPEN
        
        // Остальные байты заполнены нулями (по умолчанию)

        // Создаём полный пакет с заголовком и CRC
        return TBoxFraming.createPacket(body)
    }

    /**
     * Создать пакет для закрытия спойлера
     *
     * Function Bytes: BF 11 0D 05 27 23 20 01
     * - Component: 0x05 (spoiler close)
     * - Action: 0x23 (close)
     */
    fun createSpoilerClosePacket(): ByteArray {
        val body = ByteArray(BODY_SIZE)
        
        // === BODY (412 байт) ===
        
        // Префикс
        body[0] = 0x01.toByte()
        body[1] = 0x02.toByte()
        body[2] = 0x03.toByte()
        body[3] = 0x01.toByte()

        // Property ID (всегда 0x00000000)
        body[4] = 0x00.toByte()
        body[5] = 0x00.toByte()
        body[6] = 0x00.toByte()
        body[7] = 0x00.toByte()

        // Function Bytes (8-15)
        // BF 11 0D 05 27 23 20 01
        body[8] = 0xBF.toByte()
        body[9] = 0x11.toByte()
        body[10] = 0x0D.toByte()
        body[11] = 0x05.toByte()  // Component: spoiler close
        body[12] = 0x27.toByte()
        body[13] = 0x23.toByte()  // Action: close
        body[14] = 0x20.toByte()
        body[15] = 0x01.toByte()

        // === КЛЮЧЕВЫЕ БАЙТЫ ===
        body[325] = 0x2B.toByte()  // Командный байт (всегда 0x2B)
        body[333] = 0x5A.toByte()  // Command marker
        body[335] = 0x00.toByte()  // CLOSE
        
        // Остальные байты заполнены нулями (по умолчанию)

        // Создаём полный пакет с заголовком и CRC
        return TBoxFraming.createPacket(body)
    }

    // ========================================================================
    // JSON MODE (рекомендуется для HuronCarSettings)
    // ========================================================================

    private val gson = Gson()

    /**
     * JSON Payload для спойлера
     *
     * @param value 1=OPEN, 2=CLOSE, 3=FOLLOW_SPEED, 4=SPORT_MODE
     * @param valid валидность данных
     * @param relative относительное значение
     * @param time timestamp
     * @param extension дополнительные данные
     */
    data class SpoilerPayload(
        val value: Int,
        val valid: Boolean = true,
        val relative: Boolean = false,
        val time: Long = System.currentTimeMillis(),
        val extension: Any? = null
    )

    /**
     * Создать JSON для открытия спойлера
     *
     * @return JSON строка: {"value":1,"valid":true,...}
     */
    fun createSpoilerOpenJson(): String {
        return gson.toJson(SpoilerPayload(value = 1))
    }

    /**
     * Создать JSON для закрытия спойлера
     *
     * @return JSON строка: {"value":2,"valid":true,...}
     */
    fun createSpoilerCloseJson(): String {
        return gson.toJson(SpoilerPayload(value = 2))
    }

    /**
     * Создать JSON для режима "По скорости"
     *
     * @return JSON строка: {"value":3,"valid":true,...}
     */
    fun createFollowSpeedJson(): String {
        return gson.toJson(SpoilerPayload(value = 3))
    }

    /**
     * Создать JSON для спортивного режима
     *
     * @return JSON строка: {"value":4,"valid":true,...}
     */
    fun createSportModeJson(): String {
        return gson.toJson(SpoilerPayload(value = 4))
    }

    /**
     * Создать JSON команду из TBox команды
     *
     * @param component TBox component (0x06=open, 0x05=close)
     * @param action TBox action (0x21=open, 0x23=close)
     * @return JSON строка
     */
    fun createJsonFromTBoxCommand(component: Int, action: Int): String {
        val value = when {
            component == 0x06 && action == 0x21 -> 1  // OPEN
            component == 0x05 && action == 0x23 -> 2  // CLOSE
            else -> 0  // Unknown
        }
        return gson.toJson(SpoilerPayload(value = value))
    }

    /**
     * Парсить JSON ответ от сервера
     *
     * @param json JSON строка от Fake32960Server
     * @return SpoilerPayload или null
     */
    fun parseSpoilerJson(json: String): SpoilerPayload? {
        return try {
            gson.fromJson(json, SpoilerPayload::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
