# Mock System Documentation

## 📖 Обзор

Mock-система для эмуляции proprietary сервисов Mazda без доступа к реальному автомобилю.

### Зачем нужно?

- ✅ **Разработка дома** - не нужно постоянно ездить в автомобиль
- ✅ **Быстрое тестирование** - мгновенная обратная связь
- ✅ **Безопасность** - ничего не сломается в реальном авто
- ✅ **Отладка UI** - проверка интерфейса без реальных вызовов
- ✅ **Демонстрация** - показать работу без доступа к железу

---

## 🏗️ Архитектура

```
┌─────────────────────────────────────────────────────┐
│                  TestMode (Switcher)                │
│  - Переключатель Mock/Real                          │
│  - Сохранение режима в SharedPreferences            │
│  - Предоставляет контроллеры                        │
└─────────────────────────────────────────────────────┘
           │
           ├─── Mock Mode (Эмуляция)
           │    ├── MockMegaController (спойлер, фары, замки)
           │    ├── MockCarService (данные авто)
           │    └── MockHvacController (климат)
           │
           └─── Real Mode (Реальные вызовы)
                ├── RealMegaController (Shizuku)
                ├── RealCarService (Shizuku)
                └── RealHvacController (Shizuku)
```

---

## 📦 Компоненты

### 1. Интерфейсы

#### `IMegaController`
Главный контроллер автомобиля:
- `setSpoiler(position)` - спойлер
- `setLights(mode)` - фары
- `setLock(door, locked)` - замки
- `setWindow(window, position)` - окна
- `callService(code, propId, value)` - общий вызов сервиса

#### `ICarService`
Автомобильный сервис (данные):
- `getSpeed()` - скорость (км/ч)
- `getRpm()` - обороты двигателя
- `getEngineTemp()` - температура двигателя
- `getFuelLevel()` - уровень топлива
- `getBatteryVoltage()` - напряжение батареи
- `getInteriorTemp()` - температура в салоне
- `getExteriorTemp()` - температура снаружи

#### `IHvacController`
Климат контроль:
- `setTemperature(zone, temp)` - температура по зонам
- `setAcEnabled(enabled)` - кондиционер
- `setFanSpeed(speed)` - вентилятор (0-7)
- `setAirMode(mode)` - режим обдува
- `setSeatHeating(seat, level)` - обогрев сидений

---

### 2. Mock-реализации

#### `MockMegaController`
- Эмулирует задержки 50-150ms (как в реальном авто)
- Хранит состояния свойств
- Логирует все вызовы
- Возвращает реалистичные значения

**Пример:**
```kotlin
// Эмуляция открытия спойлера
MockMegaController.setSpoiler(1)  // 1 = OPEN

// Лог:
// 📞 callService #1: code=0x1, propId=0x12345678, value=1
// 🚗 Spoiler position: 1
// ✅ Result: true
```

#### `MockCarService`
- Генерирует реалистичные данные
- Эмулирует работу двигателя
- Обновляет параметры в реальном времени

**Пример данных:**
```kotlin
MockCarService.startSimulation()

// Данные:
// Speed: 0-120 km/h
// RPM: 800-2500
// Engine Temp: 20-90°C
// Fuel: 75%
// Battery: 12.6-14.4V
```

#### `MockHvacController`
- Двухзонный климат (водитель/пассажир)
- Температуры 16-30°C
- 7 скоростей вентилятора
- 3 уровня обогрева сидений

**Пример:**
```kotlin
MockHvacController.setTemperature(0, 220)  // 22.0°C водитель
MockHvacController.setFanSpeed(3)
MockHvacController.setAcEnabled(true)
MockHvacController.setComfortMode()  // Быстрая установка
```

---

### 3. Real-реализации (через Shizuku)

#### `RealMegaController`
- Вызывает `com.mega.controller` через Shizuku
- Проверяет доступность Shizuku
- Обрабатывает ошибки

#### `RealCarService`
- Вызывает `com.mega.car` через Shizuku
- Получает реальные данные автомобиля
- Возвращает `CarPropertyValue`

#### `RealHvacController`
- Вызывает `com.mega.hvac` через Shizuku
- Управляет реальным климатом
- Поддерживает все зоны

---

### 4. TestMode (Переключатель)

**Использование:**
```kotlin
// Инициализация (в onCreate)
TestMode.init(context)

// Проверка режима
if (TestMode.isMockMode) {
    // Mock режим
} else {
    // Real режим
}

// Переключить режим
TestMode.toggleMode()

// Установить режим
TestMode.setMockMode(true)  // или false

// Получить контроллеры
val controller = TestMode.getController()
val carService = TestMode.getCarService()
val hvac = TestMode.getHvacController()
```

**Сохранение режима:**
- Режим сохраняется в `SharedPreferences`
- Восстанавливается при перезапуске приложения
- По умолчанию: Mock режим (безопаснее)

---

## 🚀 Использование

### 1. Разработка с Mock

```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Инициализируем TestMode
    TestMode.init(applicationContext)
    
    // Получаем контроллеры (автоматически Mock или Real)
    val controller = TestMode.getController()
    val carService = TestMode.getCarService()
    val hvac = TestMode.getHvacController()
    
    // Используем как обычные объекты
    controller.setSpoiler(1)
    val speed = carService.getSpeed()
    hvac.setTemperature(0, 220)
}
```

### 2. Тестирование на эмуляторе

```bash
# Запускаем эмулятор (без root!)
emulator -avd Pixel_4_API_30

# Устанавливаем приложение
adb install app/build/outputs/apk/debug/app-debug.apk

# Запускаем
# Приложение работает в Mock режиме по умолчанию
```

### 3. Переключение на Real режим

```kotlin
// В UI (кнопка переключения режима)
TestMode.toggleMode()

// Теперь контроллеры используют Shizuku
val controller = TestMode.getController()  // RealMegaController
controller.setSpoiler(1)  // Реальный вызов через Shizuku
```

---

## 📊 Статистика

### Mock-режим

```kotlin
val stats = TestMode.getStats()

// Пример:
// {
//   "mode": "Mock",
//   "mockController": {
//     "callCount": 15,
//     "propertiesCount": 5,
//     "properties": {...}
//   },
//   "mockCarService": {
//     "requestCount": 23,
//     "engineRunning": true,
//     "speed": 60,
//     "rpm": 2500,
//     "fuelLevel": 75.0
//   },
//   "mockHvac": {
//     "commandCount": 8,
//     "driverTemp": "22.0°C",
//     "passengerTemp": "22.0°C",
//     "fanSpeed": 3,
//     "acEnabled": true
//   }
// }
```

### Real-режим

```kotlin
val stats = TestMode.getStats()

// Пример:
// {
//   "mode": "Real",
//   "realController": {
//     "callCount": 10,
//     "shizukuRunning": true
//   },
//   "realCarService": {
//     "requestCount": 15,
//     "shizukuRunning": true
//   },
//   "realHvac": {
//     "commandCount": 5,
//     "shizukuRunning": true
//   }
// }
```

---

## 🔧 Настройка

### 1. Добавление зависимостей

В `build.gradle.kts`:
```kotlin
dependencies {
    // Shizuku (для Real режима)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
```

### 2. Добавление разрешений

В `AndroidManifest.xml`:
```xml
<!-- Для Real режима (Shizuku) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Shizuku provider -->
<application>
    <provider
        android:name="rikka.shizuku.ShizukuProvider"
        android:authorities="${applicationId}.shizuku"
        android:enabled="true"
        android:exported="true"
        android:multiprocess="false"
        android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
</application>
```

### 3. Инициализация в Application

```kotlin
class MazdaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализируем TestMode
        TestMode.init(applicationContext)
    }
}
```

---

## 🎯 Сценарии использования

### Сценарий 1: Разработка дома

```kotlin
// 1. Устанавливаем Mock режим
TestMode.setMockMode(true)

// 2. Разрабатываем UI
// 3. Тестируем логику
controller.setSpoiler(1)
val speed = carService.getSpeed()

// 4. Проверяем логи
Logger.getMessages()
```

**Преимущества:**
- ✅ Быстро (любой эмулятор)
- ✅ Безопасно
- ✅ Не нужно авто

---

### Сценарий 2: Тестирование на эмуляторе

```kotlin
// 1. Запускаем эмулятор
// 2. Устанавливаем приложение
// 3. Тестируем функции

// MockCarService эмулирует движение
MockCarService.startSimulation()

// Обновляем данные
MockCarService.updateSimulation()

// Получаем данные
val speed = MockCarService.getSpeed()  // 60 km/h
val rpm = MockCarService.getRpm()      // 2500
```

---

### Сценарий 3: Финальное тестирование в авто

```kotlin
// 1. Подключаемся к Head Unit
adb connect 192.168.1.100:5555

// 2. Запускаем Shizuku
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh

// 3. Переключаем на Real режим
TestMode.setMockMode(false)

// 4. Тестируем реальные вызовы
controller.setSpoiler(1)  // Реальный вызов через Shizuku
```

---

## 🐛 Отладка

### Логирование

Все Mock-контроллеры логируют вызовы:

```
🎭 MockMegaController
📞 callService #1: code=0x1, propId=0x12345678, value=1
🚗 Spoiler position: 1
✅ Result: true

🎭 MockCarService
📖 getProperty #1: propId=0x1, value=60
🚗 Speed: 60 km/h

🎭 MockHvacController
🌡️ setTemperature #1: zone=0, temp=22.0°C
✅ Success
```

### Проверка состояния

```kotlin
// Получить статистику
val stats = TestMode.getStats()

// Сбросить статистику
TestMode.reset()

// Проверить Shizuku
val shizukuRunning = Shizuku.pingBinder()
```

---

## ⚠️ Ограничения

### Mock-режим

- ❌ Не реальные CAN сообщения
- ❌ Не тестирует Shizuku
- ❌ Эмуляция задержек (не точные)
- ❌ Нет реальных ошибок

### Real-режим

- ✅ Реальные вызовы
- ✅ Реальные CAN сообщения
- ✅ Тестирует Shizuku
- ⚠️ Требует Head Unit
- ⚠️ Требует запущенный Shizuku

---

## 📈 Расширение

### Добавление нового сервиса

1. **Создать интерфейс:**
```kotlin
interface INewService {
    fun doSomething(): Boolean
    fun getValue(): Int
}
```

2. **Создать Mock-реализацию:**
```kotlin
object MockNewService : INewService {
    override fun doSomething(): Boolean {
        Log.d("MockNewService", "doSomething")
        return true
    }
    
    override fun getValue(): Int = 42
}
```

3. **Создать Real-реализацию:**
```kotlin
object RealNewService : INewService {
    override fun doSomething(): Boolean {
        if (!Shizuku.pingBinder()) return false
        
        // Реальный вызов через Shizuku
        return true
    }
    
    override fun getValue(): Int {
        // Реальный вызов
        return 0
    }
}
```

4. **Добавить в TestMode:**
```kotlin
object TestMode {
    fun getNewService(): INewService {
        return if (isMockMode) MockNewService else RealNewService
    }
}
```

---

## 🎯 Best Practices

### 1. Всегда использовать TestMode

```kotlin
// ✅ Правильно
val controller = TestMode.getController()
controller.setSpoiler(1)

// ❌ Неправильно
val controller = MockMegaController  // Жёсткое кодирование
```

### 2. Проверять режим

```kotlin
if (TestMode.isMockMode) {
    // Mock-specific логика
} else {
    // Real-specific логика
}
```

### 3. Логировать вызовы

```kotlin
Log.d(TAG, "📞 callService: code=0x${code.toString(16)}")
```

### 4. Обрабатывать ошибки

```kotlin
try {
    controller.setSpoiler(1)
} catch (e: Exception) {
    Log.e(TAG, "Error", e)
}
```

---

## 📚 Примеры кода

### Пример 1: Управление спойлером

```kotlin
// Получаем контроллер
val controller = TestMode.getController()

// Открываем спойлер
val success = controller.setSpoiler(1)

if (success) {
    Log.d("Spoiler", "✅ Opened")
} else {
    Log.e("Spoiler", "❌ Failed")
}
```

### Пример 2: Получение данных авто

```kotlin
// Получаем сервис
val carService = TestMode.getCarService()

// Получаем данные
val speed = carService.getSpeed()
val rpm = carService.getRpm()
val fuel = carService.getFuelLevel()

Log.d("Car", "Speed: $speed km/h, RPM: $rpm, Fuel: $fuel%")
```

### Пример 3: Настройка климата

```kotlin
// Получаем контроллер
val hvac = TestMode.getHvacController()

// Настраиваем комфорт
hvac.setTemperature(0, 220)  // 22.0°C водитель
hvac.setTemperature(1, 220)  // 22.0°C пассажир
hvac.setFanSpeed(3)
hvac.setAcEnabled(true)
hvac.setAirMode(3)  // Комби
```

### Пример 4: Быстрая смена режима

```kotlin
// Comfort mode
MockHvacController.setComfortMode()

// Sport mode
MockHvacController.setSportMode()

// Winter mode
MockHvacController.setWinterMode()
```

---

## 🔍 FAQ

### Q: Как переключиться на Real режим?

A: Используйте `TestMode.toggleMode()` или кнопку в UI.

### Q: Нужно ли root для Mock режима?

A: Нет! Mock режим работает на любом эмуляторе без root.

### Q: Можно ли использовать Mock и Real одновременно?

A: Нет, режим общий для всех контроллеров. Но можно получить разные контроллеры:

```kotlin
// ❌ Неправильно (режим общий)
TestMode.setMockMode(true)
val controller = TestMode.getController()  // Mock
val carService = TestMode.getCarService()  // Mock

// ✅ Правильно (один режим для всех)
TestMode.setMockMode(true)
val controller = TestMode.getController()  // Mock
val carService = TestMode.getCarService()  // Mock
```

### Q: Как узнать, какой режим активен?

A: `TestMode.isMockMode` или `TestMode.getModeDescription()`.

### Q: Сохраняется ли режим после перезапуска?

A: Да! Режим сохраняется в `SharedPreferences`.

---

## 📝 Changelog

### v1.0.0 (2026-03-18)
- ✅ Initial release
- ✅ MockMegaController (спойлер, фары, замки, окна)
- ✅ MockCarService (данные авто)
- ✅ MockHvacController (климат)
- ✅ TestMode (переключатель)
- ✅ Real-реализации (Shizuku)
- ✅ Интеграция в MainActivity

---

## 📞 Контакты

Вопросы и предложения: @ruafya7
