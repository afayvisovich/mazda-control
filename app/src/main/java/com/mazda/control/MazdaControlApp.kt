package com.mazda.control

import android.app.Application
import android.util.Log

/**
 * Application класс для инициализации
 */
class MazdaControlApp : Application() {
    
    companion object {
        private const val TAG = "MazdaControlApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 Application created")
        
        // Инициализируем TestMode
        TestMode.init(this)
        
        // Если Mock Mode - регистрируем Mock-сервис сразу
        if (TestMode.isMockMode()) {
            Log.i(TAG, "🎭 Mock Mode detected, will register MockMegaService")
        }
    }
}
