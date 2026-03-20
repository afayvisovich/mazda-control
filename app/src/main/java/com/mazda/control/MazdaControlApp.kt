package com.mazda.control

import android.app.Application
import android.util.Log

/**
 * Application класс для инициализации
 */
class MazdaControlApp : Application() {

    companion object {
        private const val TAG = "MazdaControlApp"
        lateinit var instance: MazdaControlApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "🚀 Application created")

        // Регистрируем Shizuku listeners для получения событий binder
        ShizukuIntegrationHelper.registerStatusListeners()
        Log.d(TAG, "📡 Shizuku status listeners registered")

        // Привязываемся к ShellExecutorService если Shizuku доступен
        if (ShizukuIntegrationHelper.isShizukuAvailable()) {
            Log.i(TAG, "Shizuku available, binding ShellExecutorService...")
            ShizukuShellExecutor.bind(this) { success ->
                Log.d(TAG, "ShellExecutorService bind result: $success")
            }
        }

        // Инициализируем TestMode
        TestMode.init(this)

        // Если Mock Mode - регистрируем Mock-сервис сразу
        if (TestMode.isMockMode()) {
            Log.i(TAG, "🎭 Mock Mode detected, will register MockMegaService")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Отписываемся от Shizuku событий
        ShizukuIntegrationHelper.unregisterStatusListeners()
        Log.d(TAG, "📡 Shizuku status listeners unregistered")

        // Отвязываемся от ShellExecutorService
        ShizukuShellExecutor.unbind()
        Log.d(TAG, "🔌 ShellExecutorService unbound")
    }
}
