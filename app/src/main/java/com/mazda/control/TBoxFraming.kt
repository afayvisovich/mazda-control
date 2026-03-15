package com.mazda.control

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Framing утилиты для протокола AG35TspClient
 *
 * Формат пакета:
 * - 14-байтовый заголовок с magic byte 0x5A
 * - Тело (body)
 * - CRC16 checksum в конце (2 байта)
 *
 * Основано на спецификации TBoxFraming.md
 */
object TBoxFraming {

    private const val TAG = "TBoxFraming"

    // Magic byte для заголовка
    private const val MAGIC_BYTE = 0x5A.toByte()

    // Размер заголовка
    private const val HEADER_SIZE = 14

    // Размер CRC
    private const val CRC_SIZE = 2

    /**
     * Структура заголовка (14 байт):
     * [0]     - Magic byte (0x5A)
     * [1]     - Version (0x01)
     * [2-3]   - Reserved (0x0000)
     * [4-7]   - Body length (UInt32, Little Endian)
     * [8-13]  - Reserved (6 байт, 0x00)
     */
    fun createHeader(bodyLength: Int): ByteArray {
        val header = ByteArray(HEADER_SIZE)

        // Magic byte
        header[0] = MAGIC_BYTE

        // Version
        header[1] = 0x01.toByte()

        // Reserved (2 байта)
        header[2] = 0x00.toByte()
        header[3] = 0x00.toByte()

        // Body length (UInt32, Little Endian)
        val lengthBytes = ByteBuffer.allocate(4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(bodyLength)
        }.array()
        System.arraycopy(lengthBytes, 0, header, 4, 4)

        // Reserved (6 байт)
        for (i in 8..13) {
            header[i] = 0x00.toByte()
        }

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
     * Разобрать полученный пакет
     *
     * @param packet полученные данные
     * @return тело пакета (без заголовка и CRC) или null если ошибка
     */
    fun parsePacket(packet: ByteArray): ByteArray? {
        if (packet.size < HEADER_SIZE + CRC_SIZE) {
            Log.e(TAG, "Packet too small: ${packet.size} bytes")
            return null
        }

        // Проверка magic byte
        if (packet[0] != MAGIC_BYTE) {
            Log.e(TAG, "Invalid magic byte: 0x${String.format("%02X", packet[0])}")
            return null
        }

        // Чтение длины тела
        val bodyLength = ByteBuffer.wrap(packet, 4, 4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }.int

        Log.d(TAG, "Body length from header: $bodyLength bytes")

        // Проверка размера
        val expectedSize = HEADER_SIZE + bodyLength + CRC_SIZE
        if (packet.size != expectedSize) {
            Log.e(TAG, "Packet size mismatch: expected $expectedSize, got ${packet.size}")
            return null
        }

        // Извлечение тела
        val body = ByteArray(bodyLength)
        System.arraycopy(packet, HEADER_SIZE, body, 0, bodyLength)

        // Проверка CRC
        val receivedCrc = ByteArray(CRC_SIZE)
        System.arraycopy(packet, HEADER_SIZE + bodyLength, receivedCrc, 0, CRC_SIZE)

        val calculatedCrc = calculateCrc16(packet, HEADER_SIZE, bodyLength)

        if (!receivedCrc.contentEquals(calculatedCrc)) {
            Log.e(
                TAG,
                "CRC mismatch: received 0x${receivedCrc.joinToString("") { String.format("%02X", it) }}, " +
                        "calculated 0x${calculatedCrc.joinToString("") { String.format("%02X", it) }}"
            )
            return null
        }

        Log.d(TAG, "Packet parsed successfully, body size: $bodyLength bytes")
        return body
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
     * Проверить является ли пакет валидным заголовком AG35
     */
    fun isValidHeader(packet: ByteArray): Boolean {
        if (packet.size < HEADER_SIZE) return false
        return packet[0] == MAGIC_BYTE
    }
}
