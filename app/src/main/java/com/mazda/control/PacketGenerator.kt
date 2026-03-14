package com.mazda.control

import java.util.Calendar

/**
 * Генератор пакетов для управления спойлером
 *
 * Основано на анализе протокола из SPOILER_APP_PLAN.md и 355_COMPLETE_GUIDE.md
 */
object PacketGenerator {

    // VIN из захваченных пакетов
    private val VIN = byteArrayOf(
        0x4C, 0x56, 0x52, 0x48, 0x44, 0x41, 0x45, 0x4A,
        0x38, 0x52, 0x4E, 0x30, 0x31, 0x33, 0x35, 0x37, 0x39
    )

    /**
     * Создать пакет для открытия спойлера
     *
     * Function Bytes: BF 11 0D 06 27 21 20 01
     * - Component: 0x06 (spoiler)
     * - Action: 0x21 (open)
     */
    fun createSpoilerOpenPacket(): ByteArray {
        val packet = ByteArray(512)

        // === HEADER (30 байт) ===
        packet[0] = 0x23.toByte()
        packet[1] = 0x23.toByte()
        packet[2] = 0x02.toByte()  // Command mark = 2 (команда)
        packet[3] = 0xFE.toByte()  // Fixed

        // VIN (байты 4-20)
        System.arraycopy(VIN, 0, packet, 4, VIN.size)

        packet[21] = 0x01.toByte()  // Encrpy-mark
        packet[22] = 0x01.toByte()  // Length (Hi)
        packet[23] = 0x9C.toByte()  // Length (Lo) = 412 байт

        // Timestamp (байты 24-29) - YY MM DD HH MM SS
        val now = Calendar.getInstance()
        packet[24] = (now.get(Calendar.YEAR) % 100).toByte()
        packet[25] = (now.get(Calendar.MONTH) + 1).toByte()
        packet[26] = now.get(Calendar.DAY_OF_MONTH).toByte()
        packet[27] = now.get(Calendar.HOUR_OF_DAY).toByte()
        packet[28] = now.get(Calendar.MINUTE).toByte()
        packet[29] = now.get(Calendar.SECOND).toByte()

        // === REALBODY (30-365) ===
        // Префикс
        packet[30] = 0x01.toByte()
        packet[31] = 0x02.toByte()
        packet[32] = 0x03.toByte()
        packet[33] = 0x01.toByte()

        // Property ID (всегда 0x00000000)
        packet[34] = 0x00.toByte()
        packet[35] = 0x00.toByte()
        packet[36] = 0x00.toByte()
        packet[37] = 0x00.toByte()

        // Function Bytes (38-45)
        // BF 11 0D 06 27 21 20 01
        packet[38] = 0xBF.toByte()
        packet[39] = 0x11.toByte()
        packet[40] = 0x0D.toByte()
        packet[41] = 0x06.toByte()  // Component: spoiler
        packet[42] = 0x27.toByte()
        packet[43] = 0x21.toByte()  // Action: open
        packet[44] = 0x20.toByte()
        packet[45] = 0x01.toByte()

        // === КЛЮЧЕВЫЕ БАЙТЫ ===
        packet[355] = 0x2B.toByte()  // Командный байт (всегда 0x2B)
        packet[363] = 0x5A.toByte()  // Command marker
        packet[365] = 0x00.toByte()  // OPEN

        return packet
    }

    /**
     * Создать пакет для закрытия спойлера
     *
     * Function Bytes: BF 11 0D 05 27 23 20 01
     * - Component: 0x05 (spoiler close)
     * - Action: 0x23 (close)
     */
    fun createSpoilerClosePacket(): ByteArray {
        val packet = ByteArray(512)

        // === HEADER (30 байт) ===
        packet[0] = 0x23.toByte()
        packet[1] = 0x23.toByte()
        packet[2] = 0x02.toByte()  // Command mark = 2 (команда)
        packet[3] = 0xFE.toByte()  // Fixed

        // VIN (байты 4-20)
        System.arraycopy(VIN, 0, packet, 4, VIN.size)

        packet[21] = 0x01.toByte()  // Encrpy-mark
        packet[22] = 0x01.toByte()  // Length (Hi)
        packet[23] = 0x9C.toByte()  // Length (Lo) = 412 байт

        // Timestamp (байты 24-29) - YY MM DD HH MM SS
        val now = Calendar.getInstance()
        packet[24] = (now.get(Calendar.YEAR) % 100).toByte()
        packet[25] = (now.get(Calendar.MONTH) + 1).toByte()
        packet[26] = now.get(Calendar.DAY_OF_MONTH).toByte()
        packet[27] = now.get(Calendar.HOUR_OF_DAY).toByte()
        packet[28] = now.get(Calendar.MINUTE).toByte()
        packet[29] = now.get(Calendar.SECOND).toByte()

        // === REALBODY (30-365) ===
        // Префикс
        packet[30] = 0x01.toByte()
        packet[31] = 0x02.toByte()
        packet[32] = 0x03.toByte()
        packet[33] = 0x01.toByte()

        // Property ID (всегда 0x00000000)
        packet[34] = 0x00.toByte()
        packet[35] = 0x00.toByte()
        packet[36] = 0x00.toByte()
        packet[37] = 0x00.toByte()

        // Function Bytes (38-45)
        // BF 11 0D 05 27 23 20 01
        packet[38] = 0xBF.toByte()
        packet[39] = 0x11.toByte()
        packet[40] = 0x0D.toByte()
        packet[41] = 0x05.toByte()  // Component: spoiler close
        packet[42] = 0x27.toByte()
        packet[43] = 0x23.toByte()  // Action: close
        packet[44] = 0x20.toByte()
        packet[45] = 0x01.toByte()

        // === КЛЮЧЕВЫЕ БАЙТЫ ===
        packet[355] = 0x2B.toByte()  // Командный байт (всегда 0x2B)
        packet[363] = 0x5A.toByte()  // Command marker
        packet[365] = 0x00.toByte()  // CLOSE (в логах spoiler.txt оба 0x00)

        return packet
    }
}
