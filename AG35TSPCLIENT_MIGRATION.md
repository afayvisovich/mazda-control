# Миграция на AG35TspClient протокол

## Критические изменения в v4.0

### 🎯 Новый сервер подключения

**БЫЛО:** `localhost:32960` (Fake32960Server - тестовая заглушка)
**СТАЛО:** `172.16.2.30:50001` (AG35TspClient - реальный сервер автомобиля)

### 📋 Протокол обмена

**AG35TspClient протокол:**
- 14-байтовый заголовок с magic byte `0x5A`
- Тело пакета (данные команды)
- CRC16 checksum в конце (2 байта)

### 🗂️ Новые файлы

| Файл | Назначение |
|------|------------|
| `TBoxFraming.kt` | Framing утилиты (заголовок + CRC16) |
| `TBoxSpoilerController.kt` | Контроллер для подключения к 172.16.2.30:50001 |
| `network_security_config.xml` | Разрешение cleartext traffic |

### 🔧 Изменения в AndroidManifest.xml

Добавлены разрешения:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="true"
    ...
>
```

### 📡 Команды спойлера

**ОТКРЫТЬ:**
- Function Bytes: `BF 11 0D 06 27 21 20 01`
- Byte 355: `0x2B`
- Byte 363: `0x5A`
- Byte 365: `0x00`

**ЗАКРЫТЬ:**
- Function Bytes: `BF 11 0D 05 27 23 20 01`
- Byte 355: `0x2B`
- Byte 363: `0x5A`
- Byte 365: `0x00`

### 🔍 Архитектура подключения

```
┌─────────────────────┐
│  TBoxSpoilerCtrl    │
│  (приложение)       │
└─────────┬───────────┘
          │ TCP Socket
          │ 172.16.2.30:50001
          ▼
┌─────────────────────┐
│   AG35TspClient     │
│  (сервер автомобиля)│
└─────────┬───────────┘
          │ CarProperty API
          ▼
┌─────────────────────┐
│   Spoiler Actuator  │
│  (физическое устр.) │
└─────────────────────┘
```

### 📊 Логирование

Логи сохраняются в: `/data/local/tmp/tbox_log-YYYYMMDD_HHMMSS.txt`

**Извлечение логов:**
```bash
adb pull /data/local/tmp/tbox_log-<timestamp>.txt
```

### 🧪 Тестирование

1. **Подключение к автомобилю:**
   - Устройство должно быть в сети автомобиля (WiFi/Ethernet)
   - Сервер `172.16.2.30:50001` должен быть доступен

2. **Переключение режимов:**
   - Кнопка "Переключить" меняет режимы TEST/REAL
   - TEST: Mock-контроллер (эмуляция)
   - REAL: TBoxSpoilerController (реальный автомобиль)

3. **Индикация:**
   - Зелёный: спойлер открыт
   - Красный: спойлер закрыт
   - Янтарный: движение (~2.2 сек)

### ⚠️ Требования

- **Root-доступ:** требуется для записи логов в `/data/local/tmp/`
- **Сеть автомобиля:** WiFi или Ethernet подключение к бортовой сети
- **Разрешения:** INTERNET, ACCESS_NETWORK_STATE

### 🐛 Отладка

При проблемах с подключением проверьте:

1. Доступность сервера:
   ```bash
   adb shell ping 172.16.2.30
   ```

2. Открытость порта:
   ```bash
   adb shell nc -zv 172.16.2.30 50001
   ```

3. Логи приложения в Logcat:
   ```bash
   adb logcat | grep -E "TBoxSpoilerController|TBoxFraming|MainActivity"
   ```

### 📝 История изменений

- **v4.0** (2026-03-15): Миграция на AG35TspClient (172.16.2.30:50001)
- **v3.1** (2026-03-14): Добавлено логирование ответов сервера
- **v3.0** (2026-03-13): Mock-контроллер для тестирования
- **v2.0** (2026-03-12): Исправлен тип сокета (TCP вместо Unix)
- **v1.0** (2026-03-11): Начальная версия с PacketGenerator

### 📚 Дополнительная документация

- `analysis/docs/REAL_SERVER_DISCOVERY.md` - обнаружение AG35TspClient
- `analysis/docs/TBoxFraming.md` - спецификация протокола
- `analysis/docs/ALL_COMMUNICATION_METHODS.md` - сравнение методов подключения
