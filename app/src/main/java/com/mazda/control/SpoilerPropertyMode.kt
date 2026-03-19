package com.mazda.control

/**
 * Режимы выбора Property ID для управления спойлером
 *
 * Существует 3 варианта Property ID из различных источников:
 * 1. 0x660000c3 - BODYINFO_HU_SPOILERSWITCH (Android VHAL)
 * 2. 0x38 - Spoiler property (HuronCarSettings/VehSettings)
 * 3. 0x6600022C - Spoiler (Android Automotive API)
 */
enum class SpoilerPropertyMode(val id: Int, val propName: String, val description: String) {
    /**
     * Вариант 1: BODYINFO_HU_SPOILERSWITCH
     * Источник: Анализ CarPropertyService
     * Property ID: 0x660000c3
     * Значения: 1=OPEN, 2=CLOSE
     */
    VARIANT_1(
        id = 1,
        propName = "VARIANT 1",
        description = "0x660000c3 (BODYINFO_HU_SPOILERSWITCH)"
    ),

    /**
     * Вариант 2: VehSettings Spoiler
     * Источник: Анализ HuronCarSettings
     * Property ID: 0x38
     * Значения: 1=OPEN, 2=CLOSE, 3=FOLLOW, 4=SPORT
     */
    VARIANT_2(
        id = 2,
        propName = "VARIANT 2",
        description = "0x38 (VehSettings Spoiler)"
    ),

    /**
     * Вариант 3: Android VHAL Spoiler
     * Источник: Android Automotive Documentation
     * Property ID: 0x6600022C
     * Значения: 0=CLOSE, 1=OPEN, 2=STOP
     */
    VARIANT_3(
        id = 3,
        propName = "VARIANT 3",
        description = "0x6600022C (Android VHAL Spoiler)"
    );

    companion object {
        /**
         * Property ID для каждого режима (используются в callService)
         * Формула: data = ((code & 0xFFFF) << 16) | ((propId & 0xFFFF) << 8) | (value & 0xFF)
         */
        val PROPERTY_IDS = mapOf(
            VARIANT_1 to 0x660000c3,
            VARIANT_2 to 0x38,
            VARIANT_3 to 0x6600022C
        )

        /**
         * Получить Property ID для текущего режима
         */
        fun getPropertyId(mode: SpoilerPropertyMode): Int {
            return PROPERTY_IDS[mode] ?: throw IllegalArgumentException("Unknown mode: $mode")
        }

        /**
         * Получить режим по ID
         */
        fun fromId(id: Int): SpoilerPropertyMode {
            return entries.find { it.id == id } ?: VARIANT_1
        }
    }
}
