package com.mazda.control

/**
 * Упрощённый интерфейс - только спойлер
 */
interface IMegaController {

    /**
     * Вызов сервиса (базовый метод)
     */
    fun callService(code: Int, propId: Int, value: Int): Boolean

    /**
     * Получить свойство
     */
    fun getProperty(propId: Int): Int

    /**
     * Установить свойство
     */
    fun setProperty(propId: Int, value: Int): Boolean

    /**
     * Управление спойлером
     * @param position: 0=CLOSE, 1=OPEN, 2=STOP
     */
    fun setSpoiler(position: Int): Boolean

    /**
     * Статистика
     */
    fun getStats(): Map<String, Any>
}
