# План: Android-приложение "Spoiler Test"

**Версия:** 1.0  
**Дата:** 2026-03-13  
**Статус:** ✅ Готов к реализации

---

## 🎯 Цель

Минималистичное Android-приложение с 1 кнопкой для управления спойлером + логирование результата выполнения.

---

## 1️⃣ Архитектура

```
┌─────────────────────────────────────────────┐
│  MainActivity                               │
│  ┌───────────────────────────────────────┐  │
│  │  [ КНОПКА: "Открыть/Закрыть" ]        │  │
│  │                                       │  │
│  │  [ TextView: Лог событий ]            │  │
│  │  - Отправка команды                   │  │
│  │  - Ответ от системы                   │  │
│  │  - Текущий статус                     │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────┐
│  SpoilerController (Kotlin/Java)            │
│  - Socket connection to localhost:32960     │
│  - Генерация пакета (512 байт)              │
│  - Отправка команды                         │
│  - Чтение ответа (опционально)              │
└─────────────────────────────────────────────┘
```

---

## 2️⃣ Компоненты

### A. UI (Layout)

**Файл:** `res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- Кнопка управления -->
    <Button
        android:id="@+id/btnToggleSpoiler"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Открыть/Закрыть спойлер"
        android:textSize="18sp"
        android:padding="20dp"
        android:layout_marginBottom="16dp" />

    <!-- Заголовок лога -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Лог событий:"
        android:textStyle="bold"
        android:textSize="14sp"
        android:layout_marginBottom="8dp" />

    <!-- Лог (в ScrollView) -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="#F0F0F0"
        android:padding="8dp">

        <TextView
            android:id="@+id/tvLog"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="monospace"
            android:textSize="12sp"
            android:lineSpacingExtra="4dp" />
    </ScrollView>

</LinearLayout>
```

---

### B. Сетевой слой

**Файл:** `SpoilerController.kt`

```kotlin
package com.example.spoilertest

import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket

class SpoilerController {

    private var socket: Socket? = null
    private var outputStream: DataOutputStream? = null
    private var isSpoilerOpen = false

    /**
     * Подключение к локальному серверу автомобиля
     */
    fun connect(): Boolean {
        return try {
            socket = Socket("127.0.0.1", 32960)
            outputStream = DataOutputStream(socket.outputStream)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Переключение состояния спойлера
     */
    fun toggle() {
        if (isSpoilerOpen) {
            sendCommand(PacketGenerator.createSpoilerClosePacket())
            isSpoilerOpen = false
        } else {
            sendCommand(PacketGenerator.createSpoilerOpenPacket())
            isSpoilerOpen = true
        }
    }

    /**
     * Отправка команды
     */
    private fun sendCommand(packet: ByteArray) {
        outputStream?.write(packet)
        outputStream?.flush()
    }

    /**
     * Проверка подключения
     */
    fun isConnected(): Boolean = socket?.isConnected == true

    /**
     * Отключение
     */
    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Получить текущее состояние
     */
    fun isSpoilerOpen(): Boolean = isSpoilerOpen
}
```

---

### C. Генерация пакета

**Файл:** `PacketGenerator.kt`

```kotlin
package com.example.spoilertest

object PacketGenerator {

    // VIN из захваченных пакетов
    private val VIN = byteArrayOf(
        0x4C, 0x56, 0x52, 0x48, 0x44, 0x41, 0x45, 0x4A,
        0x38, 0x52, 0x4E, 0x30, 0x31, 0x33, 0x35, 0x37, 0x39
    )

    /**
     * Создать пакет для открытия спойлера
     */
    fun createSpoilerOpenPacket(): ByteArray {
        val packet = ByteArray(512)

        // === HEADER (30 байт) ===
        packet[0] = 0x23
        packet[1] = 0x23
        packet[2] = 0x02  // Command mark = 2 (команда)
        packet[3] = 0xFE  // Fixed

        // VIN (байты 4-20)
        System.arraycopy(VIN, 0, packet, 4, VIN.size)

        packet[21] = 0x01  // Encrpy-mark
        packet[22] = 0x01  // Length (Hi)
        packet[23] = 0x9C  // Length (Lo) = 412 байт

        // Timestamp (байты 24-29) - YY MM DD HH MM SS
        val now = java.util.Calendar.getInstance()
        packet[24] = (now.get(java.util.Calendar.YEAR) % 100).toByte()
        packet[25] = (now.get(java.util.Calendar.MONTH) + 1).toByte()
        packet[26] = now.get(java.util.Calendar.DAY_OF_MONTH).toByte()
        packet[27] = now.get(java.util.Calendar.HOUR_OF_DAY).toByte()
        packet[28] = now.get(java.util.Calendar.MINUTE).toByte()
        packet[29] = now.get(java.util.Calendar.SECOND).toByte()

        // === REALBODY (30-365) ===
        // Префикс
        packet[30] = 0x01
        packet[31] = 0x02
        packet[32] = 0x03
        packet[33] = 0x01

        // Property ID (всегда 0x00000000)
        packet[34] = 0x00
        packet[35] = 0x00
        packet[36] = 0x00
        packet[37] = 0x00

        // Function Bytes (38-45)
        // BF 11 0D 06 27 21 20 01
        packet[38] = 0xBF
        packet[39] = 0x11
        packet[40] = 0x0D
        packet[41] = 0x06  // Component: spoiler
        packet[42] = 0x27
        packet[43] = 0x21  // Action: open
        packet[44] = 0x20
        packet[45] = 0x01

        // === КЛЮЧЕВЫЕ БАЙТЫ ===
        packet[355] = 0x2B  // Командный байт (всегда 0x2B)
        packet[363] = 0x5A  // Command marker
        packet[365] = 0x00  // OPEN

        return packet
    }

    /**
     * Создать пакет для закрытия спойлера
     */
    fun createSpoilerClosePacket(): ByteArray {
        val packet = createSpoilerOpenPacket()

        // Изменить Action Code и Byte 365
        packet[43] = 0x23  // Action: close
        packet[365] = 0x00  // CLOSE (в логах spoiler.txt оба 0x00)

        return packet
    }
}
```

---

### D. Логирование

**Файл:** `MainActivity.kt` (фрагмент)

```kotlin
package com.example.spoilertest

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var tvLog: TextView
    private lateinit var controller: SpoilerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggleSpoiler)
        tvLog = findViewById(R.id.tvLog)

        controller = SpoilerController()

        // Подключение при старте
        log("Подключение к автомобилю...")
        if (controller.connect()) {
            log("✅ Подключено к порту 32960")
        } else {
            log("❌ Ошибка подключения")
            log("   Проверьте, запущено ли приложение автомобиля")
        }

        // Обработчик кнопки
        btnToggle.setOnClickListener {
            toggleSpoiler()
        }
    }

    private fun toggleSpoiler() {
        val state = if (controller.isSpoilerOpen()) "ОТКРЫТ" else "ЗАКРЫТ"
        log("📤 Команда: Спойлер ${if (state == "ОТКРЫТ") "ЗАКРЫТЬ" else "ОТКРЫТЬ"}")
        log("   Текущее состояние: $state")

        try {
            controller.toggle()
            log("✅ Команда отправлена (512 байт)")

            // Ожидание выполнения
            Thread.sleep(500)

            val newState = if (controller.isSpoilerOpen()) "ЗАКРЫТ" else "ОТКРЫТ"
            log("📊 Ожидание изменения состояния...")

        } catch (e: Exception) {
            log("❌ Ошибка: ${e.message}")
        }
    }

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date())
        val logEntry = "[$timestamp] $message\n"

        runOnUiThread {
            tvLog.append(logEntry)

            // Автопрокрутка вниз
            val scrollAmount = tvLog.layout.getLineTop(tvLog.lineCount) - tvLog.height
            if (scrollAmount > 0) {
                tvLog.scrollTo(0, scrollAmount)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.disconnect()
        log("🔌 Отключено")
    }
}
```

---

## 3️⃣ Разрешения

**Файл:** `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.spoilertest">

    <!-- Доступ к интернету (для socket) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Доступ к сети (опционально) -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.Light"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

## 4️⃣ Поток выполнения

```
┌─────────────────────────────────────────────────────────┐
│  1. Пользователь открывает приложение                   │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  2. MainActivity.onCreate()                             │
│      - Инициализация UI                                 │
│      - Подключение к localhost:32960                    │
│      - Лог: "✅ Подключено к порту 32960"               │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  3. Пользователь нажимает кнопку                        │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  4. onClick()                                           │
│      - Лог: "📤 Команда: Спойлер ОТКРЫТЬ"               │
│      - Генерация пакета (PacketGenerator)               │
│      - Отправка через socket (SpoilerController)         │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  5. Ожидание 0.5 сек                                    │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  6. Лог: "✅ Команда отправлена (512 байт)"             │
│      - Чтение ответа (если есть)                        │
│      - Обновление статуса                               │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│  7. Пользователь видит результат в логе                 │
└─────────────────────────────────────────────────────────┘
```

---

## 5️⃣ Обработка ошибок

```kotlin
// Подключение
try {
    controller.connect()
    log("✅ Подключено")
} catch (e: ConnectException) {
    log("❌ Ошибка: Сервер не запущен")
    log("   Запустите приложение автомобиля")
    Toast.makeText(this, "Нет подключения к автомобилю", Toast.LENGTH_LONG).show()
} catch (e: IOException) {
    log("❌ Ошибка сети: ${e.message}")
    Toast.makeText(this, "Ошибка сети", Toast.LENGTH_SHORT).show()
}

// Отправка команды
try {
    controller.toggle()
    log("✅ Команда отправлена")
} catch (e: IOException) {
    log("❌ Ошибка отправки: ${e.message}")
    Toast.makeText(this, "Не удалось отправить команду", Toast.LENGTH_SHORT).show()
}
```

---

## 6️⃣ Стек технологий

| Компонент | Технология |
|-----------|------------|
| **Язык** | Kotlin (рекомендуется) или Java |
| **UI** | XML Layout (классический View) |
| **Сеть** | `java.net.Socket` |
| **Логирование** | `TextView` + `StringBuilder` |
| **Сборка** | Gradle |
| **Min SDK** | API 21 (Android 5.0) |
| **Target SDK** | API 33 (Android 13) |

---

## 7️⃣ Структура проекта

```
app/
├── src/main/
│   ├── java/com/example/spoilertest/
│   │   ├── MainActivity.kt          # UI + логирование
│   │   ├── SpoilerController.kt     # Сетевой слой
│   │   └── PacketGenerator.kt       # Генерация пакетов
│   │
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml    # Разметка UI
│   │   └── values/
│   │       └── strings.xml          # Строковые ресурсы
│   │
│   └── AndroidManifest.xml          # Разрешения
│
├── build.gradle                     # Зависимости
└── settings.gradle                  # Настройки проекта
```

---

## 8️⃣ build.gradle

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.spoilertest'
    compileSdk 33

    defaultConfig {
        applicationId "com.example.spoilertest"
        minSdk 21
        targetSdk 33
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.8.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
```

---

## 9️⃣ Критерии готовности MVP

- [ ] Кнопка отправляет команду при нажатии
- [ ] Лог показывает timestamp + статус
- [ ] Подключение к :32960 при старте приложения
- [ ] Обработка ошибок подключения (нет сервера)
- [ ] Автопрокрутка лога к последним записям
- [ ] Индикация текущего статуса (открыт/закрыт)
- [ ] Корректная генерация пакета (512 байт)
- [ ] Отключение при закрытии приложения

---

## 🔟 Расширения (после MVP)

| Приоритет | Функция | Описание |
|-----------|---------|----------|
| **P1** | Две кнопки | Отдельно "Открыть" + "Закрыть" |
| **P1** | Чтение статуса | Парсинг ответов от автомобиля |
| **P2** | Тайминг | Замер времени выполнения команды |
| **P2** | История | Сохранение лога в файл |
| **P2** | Индикатор | Визуальный статус (🟢/🔴) |
| **P3** | Багажник | Добавить управление багажником |
| **P3** | Окна | Добавить управление окнами |
| **P3** | HVAC | Добавить управление климатом |

---

## 1️⃣1️⃣ Меры безопасности

⚠️ **Важно:**

1. **Тестирование на стоянке** — не использовать во время вождения
2. **Контроль движения** — следить за спойлером при выполнении
3. **Интервал между командами** — минимум 2-3 секунды (время движения спойлера)
4. **Аварийное завершение** — кнопка "Стоп" для экстренной остановки
5. **Логирование ошибок** — сохранять все ошибки для отладки

---

## 1️⃣2️⃣ План реализации

### Этап 1: Подготовка (1-2 часа)
- [ ] Установить Android Studio
- [ ] Создать новый проект (Empty Activity)
- [ ] Настроить `build.gradle`

### Этап 2: Ядро (2-3 часа)
- [ ] Реализовать `PacketGenerator.kt`
- [ ] Реализовать `SpoilerController.kt`
- [ ] Протестировать генерацию пакетов

### Этап 3: UI (1-2 часа)
- [ ] Создать `activity_main.xml`
- [ ] Реализовать `MainActivity.kt`
- [ ] Добавить логирование

### Этап 4: Тестирование (1-2 часа)
- [ ] Запустить на эмуляторе
- [ ] Проверить генерацию пакетов
- [ ] Протестировать на автомобиле

### Этап 5: Полировка (1 час)
- [ ] Добавить обработку ошибок
- [ ] Улучшить UI
- [ ] Сохранить логи

**Итого:** ~6-10 часов

---

## 1️⃣3️⃣ Следующие шаги

После утверждения плана:

1. ✅ **Создать Android проект** в Android Studio
2. ✅ **Реализовать `PacketGenerator`** (байты из логов)
3. ✅ **Реализовать `SpoilerController`** (socket)
4. ✅ **Собрать UI** (кнопка + лог)
5. ✅ **Протестировать** на автомобиле

---

## 📚 Приложения

### A. Таблица байтов пакета

| Байт | Значение | Описание |
|------|----------|----------|
| 0-1 | `23 23` | Magic bytes |
| 2 | `02` | Command mark (2=команда) |
| 3 | `FE` | Fixed |
| 4-20 | VIN | 17-байтный VIN |
| 21 | `01` | Encrpy-mark |
| 22-23 | `01 9C` | Length (412 байта) |
| 24-29 | Timestamp | YY MM DD HH MM SS |
| 30-33 | `01 02 03 01` | Префикс |
| 34-37 | `00 00 00 00` | Property ID (VHAL) |
| 38-45 | Function Bytes | Компонент + действие |
| 355 | `2B` | Командный счётчик |
| 363 | `5A` | Command marker |
| 365 | `00` | OPEN (0x00) / CLOSE (0x00) |

### B. Function Bytes для спойлера

**ОТКРЫТЬ:**
```
BF 11 0D 06 27 21 20 01
         │  │  │
         │  │  └─ Action: 0x21 (open)
         │  └──── Const: 0x27
         └─────── Component: 0x06 (spoiler)
```

**ЗАКРЫТЬ:**
```
BF 11 0D 05 27 23 20 01
         │  │  │
         │  │  └─ Action: 0x23 (close)
         │  └──── Const: 0x27
         └─────── Component: 0x05 (spoiler close)
```

---

**Готов к реализации!** 🚀
