package com.mazda.control

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Конвертер команд TBox → JSON формат для HuronCarSettings
 *
 * Преобразует TBox-команды (444 байта) в JSON Payload,
 * который понимает CarPropertyService через Fake32960Server.
 *
 * Основано на анализе:
 * - CarPropertyUtil.serialize() - Gson.toJson(Payload)
 * - MessageProperty$1.serialize() - конвертация CarPropertyValue → Payload → JSON
 * - Fake32960Server.onChangeEvent() - получает byte[] от IMessageCenter
 *
 * @param useJsonMode true для использования JSON вместо TBox пакетов
 */
class SpoilerJsonConverter(private val useJsonMode: Boolean = true) {

    private val gson = Gson()

    /**
     * JSON Payload структура (как в CarPropertyUtil)
     *
     * Payload fields:
     * - value: Int - команда (1=OPEN, 2=CLOSE, 3=FOLLOW_SPEED, 4=SPORT_MODE)
     * - valid: Boolean - валидность данных
     * - relative: Boolean - относительное значение
     * - time: Long - timestamp
     * - extension: Object - дополнительные данные (null для спойлера)
     */
    data class SpoilerPayload(
        @SerializedName("value")
        val value: Int,

        @SerializedName("valid")
        val valid: Boolean = true,

        @SerializedName("relative")
        val relative: Boolean = false,

        @SerializedName("time")
        val time: Long = System.currentTimeMillis(),

        @SerializedName("extension")
        val extension: Any? = null
    )

    /**
     * TBox команда (из PacketGenerator)
     *
     * @param component Component byte из Function Bytes
     * @param action Action byte из Function Bytes
     */
    data class TBoxCommand(
        val component: Int,
        val action: Int
    ) {
        companion object {
            // TBox команды из PacketGenerator
            const val COMPONENT_OPEN = 0x06
            const val COMPONENT_CLOSE = 0x05
            const val ACTION_OPEN = 0x21
            const val ACTION_CLOSE = 0x23

            // Spoiler команды (Property values)
            const val VALUE_OPEN = 1
            const val VALUE_CLOSE = 2
            const val VALUE_FOLLOW_SPEED = 3
            const val VALUE_SPORT_MODE = 4
        }

        /**
         * Конвертация TBox команды в Spoiler value
         */
        fun toSpoilerValue(): Int {
            return when {
                component == COMPONENT_OPEN && action == ACTION_OPEN -> VALUE_OPEN
                component == COMPONENT_CLOSE && action == ACTION_CLOSE -> VALUE_CLOSE
                else -> 0  // Unknown command
            }
        }
    }

    /**
     * Конвертация TBox команды в JSON Payload
     *
     * @param command TBox команда (из PacketGenerator)
     * @return JSON строка для отправки в Fake32960Server
     */
    fun convertToJson(command: TBoxCommand): String {
        val value = command.toSpoilerValue()
        val payload = SpoilerPayload(value = value)
        return gson.toJson(payload)
    }

    /**
     * Конвертация Spoiler value в JSON Payload
     *
     * @param value 1=OPEN, 2=CLOSE, 3=FOLLOW_SPEED, 4=SPORT_MODE
     * @return JSON строка
     */
    fun convertToJson(value: Int): String {
        val payload = SpoilerPayload(value = value)
        return gson.toJson(payload)
    }

    /**
     * Создать JSON для открытия спойлера
     */
    fun createOpenJson(): String {
        return convertToJson(TBoxCommand.VALUE_OPEN)
    }

    /**
     * Создать JSON для закрытия спойлера
     */
    fun createCloseJson(): String {
        return convertToJson(TBoxCommand.VALUE_CLOSE)
    }

    /**
     * Создать JSON для режима "По скорости"
     */
    fun createFollowSpeedJson(): String {
        return convertToJson(TBoxCommand.VALUE_FOLLOW_SPEED)
    }

    /**
     * Создать JSON для спортивного режима
     */
    fun createSportModeJson(): String {
        return convertToJson(TBoxCommand.VALUE_SPORT_MODE)
    }

    /**
     * Парсинг JSON ответа от сервера
     *
     * @param json JSON строка от Fake32960Server
     * @return SpoilerPayload или null при ошибке
     */
    fun parseFromJson(json: String): SpoilerPayload? {
        return try {
            gson.fromJson(json, SpoilerPayload::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Проверка, является ли JSON валидным Payload
     */
    fun isValidPayload(json: String): Boolean {
        return try {
            val payload = gson.fromJson(json, SpoilerPayload::class.java)
            payload != null && payload.value in 1..4
        } catch (e: Exception) {
            false
        }
    }
}
