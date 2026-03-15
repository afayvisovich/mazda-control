# 📁 Логирование в MazdaControl v4.1

## ✅ Решение для Android 11+

### Проблема

- ❌ `/data/local/tmp/` требует **root-доступ**
- ❌ Android 11+ ограничивает доступ к файловой системе
- ❌ На головном устройстве **нет SD-карты**
- ❌ Приложение не может сохранять файлы в общие директории

### Решение

✅ **Внутренняя директория приложения** - работает без root!

```
/data/data/com.mazda.control/files/
├── mazda_log-20260315_170000.txt
└── tbox_log-20260315_170000.txt
```

---

## 📊 Типы логов

### 1. Логи MainActivity

**Путь:** `/data/data/com.mazda.control/files/mazda_log-YYYYMMDD_HHMMSS.txt`

**Содержит:**
- События UI (нажатия кнопок, смена режима)
- Статус подключения
- Ответы от сервера
- Ошибки

**Пример:**
```
[17:00:00.123] === Сессия началась ===
[17:00:00.125] Файл лога: /data/data/com.mazda.control/files/mazda_log-20260315_170000.txt
[17:00:00.126] Режим: REAL AG35TspClient
[17:00:01.234] 🚗 REAL MODE: Подключение к AG35TspClient...
[17:00:01.456] ✅ AG35TspClient: Успешное подключение к 172.16.2.30:50001
[17:00:05.789] 📤 Команда: Спойлер ОТКРЫТЬ
[17:00:08.012] 📥 ОТВЕТ AG35: [17:00:08.010] 📊 Статус | Body=512b | CRC=OK
```

### 2. Логи TBoxSpoilerController

**Путь:** `/data/data/com.mazda.control/files/tbox_log-YYYYMMDD_HHMMSS.txt`

**Содержит:**
- Детали подключения к серверу
- Отправленные пакеты (hex)
- Полученные ответы
- Ошибки соединения

**Пример:**
```
[17:00:01.234] === START CONNECTION ===
[17:00:01.235] Server: 172.16.2.30:50001
[17:00:01.456] Connected in 221ms
[17:00:05.789] === SENDING COMMAND ===
[17:00:05.790] Action: OPEN
[17:00:05.791] Packet size: 548 bytes
[17:00:05.792] Header: 5A01000000000200000000000000
[17:00:05.793] ✅ Command sent in 12ms
```

---

## 📥 Извлечение логов

### Способ 1: Через adb (рекомендуется)

```bash
# Получить список логов
adb shell ls -la /data/data/com.mazda.control/files/

# Извлечь все логи
adb pull /data/data/com.mazda.control/files/ ./logs/

# Извлечь конкретный лог
adb pull /data/data/com.mazda.control/files/mazda_log-20260315_170000.txt
```

### Способ 2: Через приложение (кнопка 💾)

1. Нажмите кнопку **"💾 Сохранить лог"** в приложении
2. Выберите приложение для шеринга:
   - Email
   - Мессенджер (Telegram, WhatsApp)
   - Файловый менеджер
   - Bluetooth

### Способ 3: Через файловый менеджер

Если на устройстве есть файловый менеджер с root-доступом:
```
/data/data/com.mazda.control/files/
```

---

## 🔍 Просмотр логов в реальном времени

### Logcat

```bash
# Все логи приложения
adb logcat | grep "MazdaControl"

# Только MainActivity
adb logcat | grep "MainActivity"

# Только TBoxSpoilerController
adb logcat | grep "TBoxSpoilerController"

# Только TBoxFraming
adb logcat | grep "TBoxFraming"

# Сохранить в файл
adb logcat -d > logcat.txt
```

### Фильтрация по тегам

```bash
# Логи подключения
adb logcat | grep -E "CONNECT|DISCONNECT"

# Логи команд
adb logcat | grep -E "SEND|COMMAND"

# Логи ошибок
adb logcat | grep -E "ERROR|Exception|Failed"
```

---

## 🛠️ Отладка

### Проверка наличия логов

```bash
# Проверка директории
adb shell ls -la /data/data/com.mazda.control/files/

# Проверка конкретного файла
adb shell cat /data/data/com.mazda.control/files/mazda_log-*.txt
```

### Очистка старых логов

```bash
# Удалить все логи
adb shell rm /data/data/com.mazda.control/files/*.txt

# Удалить логи старше 7 дней (если есть find)
adb shell find /data/data/com.mazda.control/files/ -name "*.txt" -mtime +7 -delete
```

### Анализ проблем

| Проблема | Решение |
|----------|---------|
| ❌ Нет логов | Проверьте права на запись |
| ❌ Пустой файл | Проверьте вызов log() |
| ❌ Файл не создаётся | Проверьте filesDir |

---

## 📦 Структура файлов приложения

```
/data/data/com.mazda.control/
├── files/
│   ├── mazda_log-YYYYMMDD_HHMMSS.txt    # Логи MainActivity
│   └── tbox_log-YYYYMMDD_HHMMSS.txt     # Логи TBoxController
├── cache/
│   └── ...
└── code_cache/
    └── ...
```

---

## 🔐 Безопасность

### FileProvider

Для шеринга файлов используется `FileProvider`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### file_paths.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="logs" path="." />
    <cache-path name="cache" path="." />
</paths>
```

### Преимущества

- ✅ Файлы доступны только вашему приложению
- ✅ Шеринг через безопасный URI
- ✅ Временный доступ на чтение для других приложений
- ✅ Нет утечки путей к файлам

---

## 📊 Сравнение с предыдущей версией

| Характеристика | v4.0 (old) | v4.1 (new) |
|----------------|------------|------------|
| **Путь** | `/data/local/tmp/` | `context.filesDir` |
| **Root** | ❌ Требуется | ✅ Не требуется |
| **Android 11+** | ❌ Проблемы | ✅ Работает |
| **Шеринг** | ❌ Нет | ✅ Через FileProvider |
| **adb pull** | ✅ Да | ✅ Да |
| **SD-карта** | ❌ Не нужна | ✅ Не нужна |

---

## ✅ Итоги

**Новое решение:**
- 📁 Логи во внутренней директории приложения
- 🔓 Не требует root-доступа
- 📱 Работает на Android 11+
- 📤 Удобный шеринг через системное меню
- 🔒 Безопасно через FileProvider

**Как использовать:**
1. Нажмите **"💾 Сохранить лог"** в приложении
2. Или извлеките через `adb pull /data/data/com.mazda.control/files/`
3. Или посмотрите в реальном времени через `adb logcat`

**Готово!** 🎉
