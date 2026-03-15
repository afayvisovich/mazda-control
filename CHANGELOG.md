# 🎉 MazdaControl - История изменений

## v4.1 (2026-03-15) - Исправление логирования

### Проблема
- ❌ `/data/local/tmp/` требует root-доступ
- ❌ Android 11+ ограничивает доступ к файловой системе
- ❌ На головном устройстве нет SD-карты

### Решение
✅ **Логи во внутренней директории приложения**

### Изменения
- 📁 `logFile = File(context.filesDir, ...)` - внутренняя директория
- 📤 `shareLogFile()` - шеринг через FileProvider
- 🔧 `TBoxSpoilerController(context)` - конструктор с Context
- 📄 `file_paths.xml` - пути для FileProvider

### Преимущества
- ✅ Не требует root-доступа
- ✅ Работает на Android 11+
- ✅ Удобный шеринг через системное меню
- ✅ Безопасно через FileProvider

### APK
- `app-debug.apk` (23MB)
- `app-release.apk` (17MB)

---

## v4.0 (2026-03-15) - Миграция на AG35TspClient

### Критические изменения
- 🎯 **НОВЫЙ СЕРВЕР:** `172.16.2.30:50001` вместо `localhost:32960`
- 📋 **НОВЫЙ ПРОТОКОЛ:** 14-байтовый заголовок (0x5A) + тело + CRC16

### Новые файлы
- `TBoxFraming.kt` - framing утилиты (header + CRC16)
- `TBoxSpoilerController.kt` - контроллер для 172.16.2.30:50001
- `network_security_config.xml` - cleartext traffic

### Изменения в Manifest
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<application android:usesCleartextTraffic="true">
```

### Команды спойлера
- **OPEN:** `BF 11 0D 06 27 21 20 01` (Function Bytes)
- **CLOSE:** `BF 11 0D 05 27 23 20 01` (Function Bytes)

### Архитектура
```
TBoxSpoilerController
    ↓ TCP Socket (172.16.2.30:50001)
AG35TspClient (автомобиль)
    ↓ CarProperty API
Spoiler Actuator
```

---

## v3.1 (2026-03-14) - Логирование ответов

### Добавлено
- 📊 Чтение ответов от сервера
- 📄 ServerResponse data class
- 📱 UI отображение ответов
- ⏱️ Таймер движения 2.2 секунды

---

## v3.0 (2026-03-13) - Mock-контроллер

### Добавлено
- 🔧 MockSpoilerController для тестирования
- 🔄 Переключение режимов TEST/REAL
- 📊 Детальное логирование пакетов
- ✅ Валидация пакетов

---

## v2.0 (2026-03-12) - Исправление сокета

### Исправлено
- ✅ TCP сокет вместо Unix Domain Socket
- 📍 Порт 32960 (неверный)
- 🔍 Улучшенное логирование подключения

---

## v1.0 (2026-03-11) - Начальная версия

### Создано
- 📦 PacketGenerator.kt (512 байт, CRC16)
- 🎨 UI с двумя кнопками (Open/Close)
- 📄 Логирование в `/data/local/tmp/`

---

# 📊 Сравнение версий

| Версия | Сервер | Протокол | Логи | Root | Android 11+ |
|--------|--------|----------|------|------|-------------|
| **v4.1** | 172.16.2.30:50001 | AG35TspClient (CRC16) | filesDir | ❌ Нет | ✅ Да |
| **v4.0** | 172.16.2.30:50001 | AG35TspClient (CRC16) | /data/local/tmp | ✅ Да | ❌ Нет |
| **v3.x** | localhost:32960 | Fake32960Server | /data/local/tmp | ✅ Да | ❌ Нет |
| **v2.x** | localhost:32960 | Unix Socket | /data/local/tmp | ✅ Да | ❌ Нет |
| **v1.0** | localhost:32960 | Простой TCP | /data/local/tmp | ✅ Да | ❌ Нет |

---

# 🎯 Текущее состояние (v4.1)

## ✅ Готово к использованию

### APK файлы
```bash
app/build/outputs/apk/debug/app-debug.apk      (23MB)
app/build/outputs/apk/release/app-release.apk  (17MB)
```

### Документация
- ✅ `AG35TSPCLIENT_MIGRATION.md` - миграция на AG35TspClient
- ✅ `LOGGING_GUIDE_v4.1.md` - руководство по логированию
- ✅ `USAGE_v4.1.md` - инструкция пользователя
- ✅ `CHANGELOG.md` - этот файл

### Исходный код
- ✅ `TBoxSpoilerController.kt` - подключение к 172.16.2.30:50001
- ✅ `TBoxFraming.kt` - framing утилиты
- ✅ `PacketGenerator.kt` - генерация пакетов
- ✅ `MockSpoilerController.kt` - тестовый режим
- ✅ `MainActivity.kt` - UI и управление

### Конфигурация
- ✅ `AndroidManifest.xml` - разрешения + FileProvider
- ✅ `network_security_config.xml` - cleartext traffic
- ✅ `file_paths.xml` - пути для FileProvider

### Git
```
5 commits pushed to GitHub:
- 968cbfa docs: Обновлена инструкция пользователя v4.1
- d7091b7 docs: Добавлено руководство по логированию v4.1
- eab463b fix: Перенос логов во внутреннюю директорию
- 15b3ade docs: Добавлена инструкция по использованию v4.0
- 8edd81f v4.0: Миграция на AG35TspClient протокол
```

---

# 🚀 Быстрый старт

## 1. Установка
```bash
adb install app/build/outputs/apk/release/app-release.apk
```

## 2. Запуск
1. Откройте приложение "MazdaControl"
2. Подключитесь к сети автомобиля
3. Выберите режим (TEST/REAL)
4. Нажмите "Открыть" или "Закрыть"

## 3. Логи
- Нажмите **"💾 Сохранить лог"** для шеринга
- Или: `adb pull /data/data/com.mazda.control/files/`

---

# 📞 Контакты

- **GitHub:** https://github.com/afayvisovich/mazda-control
- **Версия:** v4.1
- **Дата:** 2026-03-15
- **Протокол:** AG35TspClient (172.16.2.30:50001)

---

**🎉 Приложение готово к тестированию!**
