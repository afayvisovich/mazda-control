# JSON Mode Integration Guide

## Краткое руководство по использованию JSON режима

**Проблема:** HuronCarSettings использует JSON протокол через Gson, а не TBox 444-байтные пакеты.

**Решение:** JSON mode в `TBoxSpoilerController` для отправки Payload в формате HuronCarSettings.

---

## Что изменилось

### До (TBox mode)

```kotlin
// Старый подход - отправка 444-байтных пакетов
val packet = PacketGenerator.createSpoilerOpenPacket()  // 444 bytes
socket.getOutputStream().write(packet)
```

**Проблема:** Fake32960Server ожидает JSON от CarPropertyService, а не TBox пакеты.

### После (JSON mode)

```kotlin
// Новый подход - отправка JSON Payload
val json = """{"value":1,"valid":true,"relative":false,"time":1234567890}"""
socket.getOutputStream().write(json.toByteArray())
```

**Преимущество:** Нативный протокол HuronCarSettings, работает без root.

---

## Использование

### Вариант 1: TBoxSpoilerController с JSON mode

```kotlin
// Создать контроллер с JSON mode (по умолчанию true)
val controller = TBoxSpoilerController(context, useJsonMode = true)

// Подключиться к Fake32960Server
controller.connect()

// Открыть спойлер
controller.open()  // Отправит: {"value":1,"valid":true,...}

// Закрыть спойлер
controller.close()  // Отправит: {"value":2,"valid":true,...}

// Режим "По скорости"
controller.setFollowSpeedMode()  // Отправит: {"value":3,"valid":true,...}

// Спортивный режим
controller.setSportMode()  // Отправит: {"value":4,"valid":true,...}
```

### Вариант 2: PacketGenerator напрямую

```kotlin
// Создать JSON команду
val openJson = PacketGenerator.createSpoilerOpenJson()
// {"value":1,"valid":true,"relative":false,"time":1234567890,"extension":null}

val closeJson = PacketGenerator.createSpoilerCloseJson()
// {"value":2,"valid":true,"relative":false,"time":1234567890,"extension":null}

val followJson = PacketGenerator.createFollowSpeedJson()
// {"value":3,"valid":true,"relative":false,"time":1234567890,"extension":null}

val sportJson = PacketGenerator.createSportModeJson()
// {"value":4,"valid":true,"relative":false,"time":1234567890,"extension":null}

// Отправить через сокет
val socket = Socket("127.0.0.1", 32960)
socket.getOutputStream().write(openJson.toByteArray(Charsets.UTF_8))
```

### Вариант 3: SpoilerJsonConverter

```kotlin
val converter = SpoilerJsonConverter()

// Конвертация TBox команды в JSON
val json = converter.convertToJson(
    SpoilerJsonConverter.TBoxCommand(0x06, 0x21)  // OPEN
)

// Или напрямую value
val json = converter.convertToJson(1)  // OPEN

// Парсинг ответа от сервера
val payload = converter.parseFromJson(responseJson)
if (payload?.value == 1) {
    // Спойлер открыт
}
```

---

## Формат JSON Payload

```json
{
  "value": 1,                    // Команда: 1=OPEN, 2=CLOSE, 3=FOLLOW, 4=SPORT
  "valid": true,                 // Валидность данных
  "relative": false,             // Относительное значение
  "time": 1234567890,            // Timestamp (milliseconds)
  "extension": null              // Дополнительные данные (null для спойлера)
}
```

### Значения value

| Value | Mode | Description |
|-------|------|-------------|
| **1** | **OPEN** | Открыть спойлер (Включить) |
| **2** | **CLOSE** | Закрыть спойлер (Выключить) |
| **3** | **FOLLOW_SPEED** | Режим "По скорости" (автоматическое открытие при скорости) |
| **4** | **SPORT_MODE** | Спортивный режим (постоянно открыт) |

---

## Логирование

JSON mode включает подробное структурированное логирование:

### Пример логов для JSON команды

```
═══════════════════════════════════════
📤 ОТПРАВКА JSON КОМАНДЫ
═══════════════════════════════════════
🏷️ Action: OPEN
📄 JSON Payload: {"value":1,"valid":true,"relative":false,"time":1234567890,"extension":null}
📦 JSON bytes: 79 bytes

🔢 Hex dump (79 bytes):
0000  7B 22 76 61 6C 75 65 22 3A 31 2C 22 76 61 6C 69 7B 22 76 61 6C 75 65 22 3A 31 2C 22 76 61 6C 69  {"value":1,"valid"
0010  64 22 3A 74 72 75 65 2C 22 72 65 6C 61 74 69 76 65 22 3A 66 61 6C 73 65 2C 22 74 69 6D 65 22 3A  d":true,"relative":
0020  31 32 33 34 35 36 37 38 39 30 2C 22 65 78 74 65 6E 73 69 6F 6E 22 3A 6E 75 6C 6C 7D 0A            1234567890,"extension":null}.

🎯 КЛЮЧЕВЫЕ ПОЛЯ JSON:
  • value: 1 (OPEN)
  • valid: true
  • relative: false
  • time: 1234567890 (2024-01-15 10:30:45.123)
  • extension: null

═══════════════════════════════════════

🚀 Отправка JSON...
✅ JSON отправлен за 5ms
```

### Пример логов для TBox команды

```
═══════════════════════════════════════
📤 ОТПРАВКА TBox ПАКЕТА
═══════════════════════════════════════
🏷️ Action: OPEN
📦 Packet size: 444 bytes (444 expected)

📋 ЗАГОЛОВОК (30 байт):
0000  02 F0 00 00 00 00 00 00 00 00 00 00 00 00 00 00  ................
0010  00 00 00 00 00 00 00 00 00 00                    ..........

📦 ТЕЛО ПАКЕТА (412 байт):
001E  ... (тело пакета) ...

✅ CRC16: AB CD

🎯 КЛЮЧЕВЫЕ БАЙТЫ:
  Function Bytes [288-295]: 01 00 00 00 00 00 00 00
  Byte 355: 01
  Byte 363: 00

═══════════════════════════════════════

🚀 Отправка пакета...
✅ Пакет отправлен за 12ms
```

### Ключевые отличия логирования

| Режим | Формат | Размер | Структура лога |
|-------|--------|--------|----------------|
| **JSON** | Текст (UTF-8) | ~80 bytes | JSON string + hex dump + parsed fields |
| **TBox** | Бинарный | 444 bytes | Header (30) + Body (412) + CRC (2) |

---

## Тестирование

### Через adb shell

```bash
# Подключиться к Fake32960Server и отправить JSON
adb shell "echo -n '{\"value\":1,\"valid\":true}' | nc localhost 32960"

# Проверить логи
adb logcat | grep TBoxSpoilerController
```

### Через приложение

```kotlin
// MainActivity.kt
val controller = TBoxSpoilerController(this, useJsonMode = true)

buttonOpen.setOnClickListener {
    if (controller.connect()) {
        controller.open()
    }
}

buttonClose.setOnClickListener {
    controller.close()
}
```

---

## Переключение между режимами

### TBox mode (legacy)

```kotlin
val controller = TBoxSpoilerController(context, useJsonMode = false)
controller.open()  // Отправит 444-байтный пакет
```

### JSON mode (рекомендуется)

```kotlin
val controller = TBoxSpoilerController(context, useJsonMode = true)
controller.open()  // Отправит JSON Payload
```

---

## Интеграция с существующим кодом

### Если используется PacketGenerator

**Было:**

```kotlin
val packet = PacketGenerator.createSpoilerOpenPacket()
sendPacket(packet)
```

**Стало:**

```kotlin
val json = PacketGenerator.createSpoilerOpenJson()
sendJson(json)
```

### Если используется TBoxFraming

**Было:**

```kotlin
val body = ByteArray(412)
// ... заполнение body
val packet = TBoxFraming.createPacket(body)
sendPacket(packet)
```

**Стало:**

```kotlin
val json = PacketGenerator.createSpoilerOpenJson()
sendJson(json)  // Без TBoxFraming!
```

---

## Проверка работы

### 1. Подключение к Fake32960Server

```kotlin
val connected = controller.connect()
if (connected) {
    Log.d("SPOILER", "✅ Connected to Fake32960Server")
} else {
    Log.e("SPOILER", "❌ Connection failed")
}
```

### 2. Отправка команды

```kotlin
controller.open()
```

### 3. Проверка логов

```bash
adb logcat | grep -E "TBoxSpoilerController|SpoilerJsonConverter"
```

### 4. Ожидание ответа

```kotlin
controller.onServerResponse = { response ->
    Log.d("SPOILER", "Server response: ${response.toUiString()}")
}
```

---

## Возможные проблемы

### ❌ Connection refused

**Проблема:** Fake32960Server не запущен.

**Решение:** Убедитесь, что huronRMU или аналогичный сервис запущен.

### ❌ JSON not accepted

**Проблема:** Сервер ожидает другой формат.

**Решение:** Проверьте структуру Payload:
- `value` должен быть 1-4
- `valid` должен быть true
- `time` должен быть timestamp

### ❌ No response from server

**Проблема:** Сервер не отправляет подтверждение.

**Решение:** Включите логирование и проверьте, отправляется ли JSON:
```kotlin
Log.d("SPOILER", "Sending JSON: $json")
```

---

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│  TBoxSpoilerController (JSON mode)                      │
│    ↓                                                     │
│  SpoilerJsonConverter                                   │
│    ↓ gson.toJson()                                       │
│  JSON: {"value":1,"valid":true,...}                     │
│    ↓ toByteArray()                                       │
│  Socket localhost:32960                                 │
└─────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────┐
│  Fake32960Server                                        │
│    ↓ onChangeEvent(byte[])                               │
│  handleTransparentData()                                │
│    ↓                                                     │
│  TLVManager                                             │
└─────────────────────────────────────────────────────────┘
```

---

## Следующие шаги

1. **Протестировать JSON mode**
   ```kotlin
   val controller = TBoxSpoilerController(context, useJsonMode = true)
   controller.connect()
   controller.open()
   ```

2. **Проверить логи**
   ```bash
   adb logcat | grep TBoxSpoilerController
   ```

3. **Убедиться, что Fake32960Server получает JSON**
   ```bash
   adb shell "nc -l localhost 32960"  # В одном терминале
   # Запустить приложение в другом терминале
   ```

4. **При успехе - использовать в продакшене**

---

## Дополнительные ресурсы

- `SPOILER_PROTOCOL_DISCOVERY.md` - Полный анализ протокола
- `SpoilerJsonConverter.kt` - Конвертер TBox → JSON
- `PacketGenerator.kt` - Генератор JSON команд
- `TBoxSpoilerController.kt` - Контроллер с JSON mode
