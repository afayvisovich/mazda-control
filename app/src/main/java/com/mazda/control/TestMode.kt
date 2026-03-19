package com.mazda.control

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mazda.control.IMegaController
import com.mazda.control.mock.MockMegaController

/**
 * Переключатель между Mock и Real режимами
 *
 * - MOCK: для эмулятора (без Shizuku)
 * - REAL: для Head Unit (с реальным Shizuku)
 */
object TestMode {

    private const val TAG = "TestMode"
    private const val PREF_NAME = "mazda_control_prefs"
    private const val KEY_MOCK_MODE = "mock_mode"
    private const val KEY_SPOILER_MODE = "spoiler_mode"

    private var prefs: SharedPreferences? = null
    private var mockMode: Boolean = true
    private var spoilerPropertyMode: SpoilerPropertyMode = SpoilerPropertyMode.VARIANT_1

    /**
     * Инициализация
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        mockMode = prefs?.getBoolean(KEY_MOCK_MODE, true) ?: true
        val spoilerModeId = prefs?.getInt(KEY_SPOILER_MODE, 1) ?: 1
        spoilerPropertyMode = SpoilerPropertyMode.fromId(spoilerModeId)

        // Инициализируем MockMegaController если в Mock режиме
        if (mockMode) {
            MockMegaController.init(context)
        }

        Log.d(TAG, "🚀 TestMode initialized: ${if (mockMode) "🎭 MOCK" else "🚗 REAL"}, Spoiler Mode: ${spoilerPropertyMode.name}")
    }

    /**
     * Переключить режим (одна кнопка)
     */
    fun toggle(): Boolean {
        mockMode = !mockMode
        prefs?.edit()?.putBoolean(KEY_MOCK_MODE, mockMode)?.apply()
        Log.d(TAG, "🔄 Mode switched to: ${if (mockMode) "🎭 MOCK" else "🚗 REAL"}")
        return mockMode
    }

    /**
     * Установить режим
     */
    fun setMockMode(mock: Boolean) {
        mockMode = mock
        prefs?.edit()?.putBoolean(KEY_MOCK_MODE, mockMode)?.apply()

        // Устанавливаем режим проверки прав
        ShizukuBinderCaller.setMockSystemPermissions(mock)

        Log.d(TAG, "🔄 Mode set to: ${if (mockMode) "🎭 MOCK" else "🚗 REAL"}")
    }

    /**
     * Установить режим Property ID для спойлера
     */
    fun setSpoilerPropertyMode(mode: SpoilerPropertyMode) {
        spoilerPropertyMode = mode
        prefs?.edit()?.putInt(KEY_SPOILER_MODE, mode.id)?.apply()
        Log.d(TAG, "🔄 Spoiler Property Mode set to: ${mode.name} (${mode.description})")
    }

    /**
     * Получить текущий режим Property ID для спойлера
     */
    fun getSpoilerPropertyMode(): SpoilerPropertyMode {
        return spoilerPropertyMode
    }

    /**
     * Получить текущий режим
     */
    fun isMockMode(): Boolean = mockMode

    /**
     * Получить контроллер в зависимости от режима
     */
    fun getController(): IMegaController {
        return if (mockMode) {
            Log.d(TAG, "🎭 Using MockMegaController")
            MockMegaController
        } else {
            Log.d(TAG, "🚗 Using RealMegaController")
            RealMegaController
        }
    }
}
