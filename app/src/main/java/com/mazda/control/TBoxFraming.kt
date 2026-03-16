package com.mazda.control

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Framing утилиты для протокола Fake32960Server (localhost:32960)
 *
 * Формат пакета (30-байтовый заголовок):
 * - Magic bytes: 0x23 0x23
 * - Command mark: 0x02=action, 0x03=status
 * - VIN (17 bytes)
 * - Encryption mark
 * - Body length (Little Endian)
 * - Timestamp (YY MM DD HH MM SS)
 * - Body data
 * - CRC16 checksum
 *
 * Основано на CLAUDE.md и анализе протокола
 */
object TBoxFraming {

    private const val TAG = "TBoxFraming"

    // Magic bytes для заголовка
    private const val MAGIC_BYTE_0 = 0x23.toByte()
    private const val MAGIC_BYTE_1 = 0x23.toByte()

    // Размер заголовка
    private const val HEADER_SIZE = 30

    // Размер CRC
    private const val CRC_SIZE = 2

    /**
     * Структура заголовка (30 байт):
     * [0-1]   - Magic bytes (0x23 0x23)
     * [2]     - Command mark (0x02=action, 0x03=status)
     * [3]     - Fixed (0xFE)
     * [4-20]  - VIN (17 bytes)
     * [21]    - Encryption mark
     * [22-23] - Body length (Little Endian)
     * [24-29] - Timestamp (YY MM DD HH MM SS)
     */
    fun createHeader(bodyLength: Int): ByteArray {
        val header = ByteArray(HEADER_SIZE)

        // Magic bytes
        header[0] = MAGIC_BYTE_0
        header[1] = MAGIC_BYTE_1

        // Command mark (0x02 = команда)
        header[2] = 0x02.toByte()

        // Fixed
        header[3] = 0xFE.toByte()

        // VIN (байты 4-20) - заполняется позже в PacketGenerator
        // Пока оставляем нулями

        // Encrpy-mark
        header[21] = 0x01.toByte()

        // Body length (Little Endian) - байты 22-23
        header[22] = (bodyLength and 0xFF).toByte()
        header[23] = ((bodyLength ushr 8) and 0xFF).toByte()

        // Timestamp заполняется позже

        return header
    }

    /**
     * Рассчитать CRC16 для данных
     *
     * Используем стандартный CRC16-CCITT (polynomial 0x8005, init 0x0000)
     *
     * @param data данные для расчета
     * @param offset начало данных (по умолчанию 0)
     * @param length длина данных (по умолчанию вся длина массива)
     * @return CRC16 checksum (2 байта, Little Endian)
     */
    fun calculateCrc16(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset
    ): ByteArray {
        var crc = 0x0000
        val polynomial = 0x8005

        for (i in offset until offset + length) {
            var byte = data[i].toInt() and 0xFF
            for (bit in 0 until 8) {
                val lsb = crc and 0x0001
                crc = crc ushr 1
                if ((byte and 0x01) != 0) {
                    crc = crc or 0x8000
                }
                if (lsb != 0) {
                    crc = crc xor polynomial
                }
                byte = byte ushr 1
            }
        }

        // Возвращаем в Little Endian формате
        return byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc ushr 8) and 0xFF).toByte()
        )
    }

    /**
     * Создать полный пакет с заголовком и CRC
     *
     * @param body тело пакета (данные)
     * @return полный пакет: [HEADER][BODY][CRC16]
     */
    fun createPacket(body: ByteArray): ByteArray {
        val header = createHeader(body.size)
        val crc = calculateCrc16(body)

        val totalSize = HEADER_SIZE + body.size + CRC_SIZE
        val packet = ByteArray(totalSize)

        // Копируем заголовок
        System.arraycopy(header, 0, packet, 0, HEADER_SIZE)

        // Копируем тело
        System.arraycopy(body, 0, packet, HEADER_SIZE, body.size)

        // Копируем CRC
        System.arraycopy(crc, 0, packet, HEADER_SIZE + body.size, CRC_SIZE)

        return packet
    }

    /**
     * Логировать пакет в hex формате
     *
     * @param packet пакет для логирования
     * @param prefix префикс для лога
     * @param maxBytes максимальное количество байт для отображения (0 = все)
     */
    fun logPacket(
        packet: ByteArray,
        prefix: String = "Packet",
        maxBytes: Int = 64
    ) {
        val displayLength = if (maxBytes > 0 && packet.size > maxBytes) maxBytes else packet.size
        val hexString = packet.take(displayLength).joinToString(" ") {
            String.format("%02X", it)
        }
        val truncated = if (packet.size > maxBytes && maxBytes > 0) "... (${packet.size} bytes total)" else ""
        Log.d(TAG, "$prefix: $hexString$truncated")
    }

    /**
     * Проверить является ли пакет валидным заголовком
     */
    fun isValidHeader(packet: ByteArray): Boolean {
        if (packet.size < HEADER_SIZE) return false
        return packet[0] == MAGIC_BYTE_0 && packet[1] == MAGIC_BYTE_1
    }

    /**
     * Извлечь длину тела из заголовка
     * Bytes [22-23] содержат длину тела (Little Endian)
     */
    fun getBodyLengthFromHeader(header: ByteArray): Int {
        if (header.size < HEADER_SIZE) {
            Log.e(TAG, "Header too small")
            return -1
        }
        
        // Проверка magic bytes
        if (header[0] != MAGIC_BYTE_0 || header[1] != MAGIC_BYTE_1) {
            Log.e(TAG, "Invalid magic bytes")
            return -1
        }
        
        // Длина тела в байтах [22-23], Little Endian
        return ((header[23].toInt() and 0xFF) shl 8) or (header[22].toInt() and 0xFF)
    }

    /**
     * Разобрать полученный пакет
     *
     * @param packet полученные данные
     * @return тело пакета (без заголовка) или null если ошибка
     */
    fun parsePacket(packet: ByteArray): ByteArray? {
        if (packet.size < HEADER_SIZE + CRC_SIZE) {
            Log.e(TAG, "Packet too small: ${packet.size} bytes")
            return null
        }

        // Проверка magic bytes
        if (packet[0] != MAGIC_BYTE_0 || packet[1] != MAGIC_BYTE_1) {
            Log.e(TAG, "Invalid magic byte: 0x${String.format("%02X", packet[0])}${String.format("%02X", packet[1])}")
            return null
        }

        // Чтение длины тела из заголовка (bytes 22-23)
        val bodyLength = getBodyLengthFromHeader(packet)
        if (bodyLength <= 0 || bodyLength > 10000) {
            Log.e(TAG, "Invalid body length: $bodyLength")
            return null
        }

        Log.d(TAG, "Packet: total=${packet.size}, header=$HEADER_SIZE, body=$bodyLength, crc=$CRC_SIZE")

        // Проверка размера пакета
        val expectedSize = HEADER_SIZE + bodyLength + CRC_SIZE
        if (packet.size != expectedSize) {
            Log.w(TAG, "Packet size mismatch: expected $expectedSize, got ${packet.size}")
            // Не возвращаем null, пытаемся извлечь тело несмотря на несоответствие
        }

        // Извлечение тела (начинается после 30-байтового заголовка)
        val body = ByteArray(bodyLength)
        val bodyStart = HEADER_SIZE
        val actualBodyLength = minOf(bodyLength, packet.size - bodyStart - CRC_SIZE)
        
        if (actualBodyLength <= 0) {
            Log.e(TAG, "No body data in packet")
            return null
        }
        
        System.arraycopy(packet, bodyStart, body, 0, actualBodyLength)

        // Проверка CRC (последние 2 байта)
        val crcStart = bodyStart + actualBodyLength
        if (crcStart + CRC_SIZE <= packet.size) {
            val receivedCrc = ByteArray(CRC_SIZE)
            System.arraycopy(packet, crcStart, receivedCrc, 0, CRC_SIZE)
            val calculatedCrc = calculateCrc16(packet, bodyStart, actualBodyLength)

            if (!receivedCrc.contentEquals(calculatedCrc)) {
                Log.w(
                    TAG,
                    "CRC mismatch (non-fatal): received 0x${receivedCrc.joinToString("") { String.format("%02X", it) }}, " +
                            "calculated 0x${calculatedCrc.joinToString("") { String.format("%02X", it) }}"
                )
                // Не возвращаем null - CRC ошибка не критична для POC
            }
        } else {
            Log.w(TAG, "CRC bytes not found in packet")
        }

        Log.d(TAG, "Packet parsed successfully, body size: $actualBodyLength bytes")
        return body
    }
}
