# 🚗 MazdaControl v4.2 - Инструкция по использованию

## ⚡ Быстрый старт

### 1. Установка APK

```bash
# Отладочная версия (с логированием)
adb install app/build/outputs/apk/debug/app-debug.apk

# Или релизная версия
adb install app/build/outputs/apk/release/app-release.apk
```

### 2. Запуск приложения

1. Запустите приложение "MazdaControl" на устройстве
2. **Не требуется подключение к сети** - используется локальный сервер автомобиля
3. Нажмите кнопку **"Переключить"** для выбора режима:
   - **🚗 REAL (127.0.0.1:32960)** - реальный автомобиль (Fake32960Server)
   - **🔧 TEST MODE** - эмуляция (без подключения)

### 3. Управление спойлером

| Кнопка | Действие |
|--------|----------|
| **Открыть** | Поднять спойлер вверх |
| **Закрыть** | Опустить спойлер вниз |
| **Переключить** | Смена режима TEST/REAL |
| **🔍 Диагностика сети** | Проверка доступности сервера |
| **💾 Сохранить лог** | Поделиться логом через системное меню |

---

## 📁 Логирование (v4.1)

### Расположение логов

**Внутренняя директория приложения** (не требует root!):

```
/data/data/com.mazda.control/files/
├── mazda_log-YYYYMMDD_HHMMSS.txt
└── tbox_log-YYYYMMDD_HHMMSS.txt
```

### Извлечение логов

**Способ 1: Через приложение (рекомендуется)**
1. Нажмите кнопку **"💾 Сохранить лог"**
2. Выберите приложение для шеринга (Email, мессенджер, файловый менеджер)

**Способ 2: Через adb**
```bash
# Извлечь все логи
adb pull /data/data/com.mazda.control/files/

# Извлечь конкретный лог
adb pull /data/data/com.mazda.control/files/mazda_log-*.txt
```

**Способ 3: Просмотр в реальном времени**
```bash
# Все логи приложения
adb logcat | grep "MazdaControl"

# Только TBoxSpoilerController
adb logcat | grep "TBoxSpoilerController"
```

### Преимущества нового логирования

- ✅ **Не требует root-доступа**
- ✅ **Работает на Android 11+**
- ✅ **Удобный шеринг** через системное меню
- ✅ **Безопасно** через FileProvider
- ✅ **Доступно** через adb pull

---

## 🔧 Режимы работы

### 🚗 AG35TspClient (Реальный автомобиль)

**Подключение:** `172.16.2.30:50001` (TCP)

**Требования:**
- Устройство в сети автомобиля
- Порт 50001 доступен
- Разрешён cleartext traffic

**Протокол:**
- 14-байтовый заголовок (magic 0x5A)
- Тело команды
- CRC16 checksum

### 🔧 TEST MODE (Эмуляция)

**Подключение:** `127.0.0.1:32960` (тестовое)

**Особенности:**
- Не требует подключения к автомобилю
- Эмулирует движение (~2.2 сек)
- Логирует команды в консоль

---

## 📊 Индикация состояния

| Индикатор | Значение |
|-----------|----------|
| 🟢 Зелёный | Спойлер ОТКРЫТ |
| 🔴 Красный | Спойлер ЗАКРЫТ |
| 🟠 Янтарный | ДВИЖЕНИЕ... (~2.2 сек) |
| ⚠️ НЕТ ПОДКЛЮЧЕНИЯ | Ошибка подключения к AG35 |

---

## 🐛 Отладка

### Проверка подключения

```bash
# Ping до сервера
adb shell ping -c 4 172.16.2.30

# Проверка порта
adb shell nc -zv 172.16.2.30 50001

# Или через telnet
adb shell telnet 172.16.2.30 50001
```

### Логи приложения

```bash
# Все логи MazdaControl
adb logcat | grep "MazdaControl"

# Только TBoxSpoilerController
adb logcat | grep "TBoxSpoilerController"

# Только TBoxFraming
adb logcat | grep "TBoxFraming"

# Сохранить в файл
adb logcat -d > logcat.txt
```

### Типичные проблемы

| Проблема | Решение |
|----------|---------|
| ❌ НЕТ ПОДКЛЮЧЕНИЯ | Проверьте сеть автомобиля |
| ❌ Connection refused | Порт 50001 недоступен |
| ❌ Connection timeout | Сервер не отвечает |
| ❌ Ошибка записи лога | Проверьте права приложения |

---

## 🔐 Разрешения

### Необходимые разрешения

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Сетевая конфигурация

```xml
<!-- network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">172.16.2.30</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

### FileProvider для шеринга

```xml
<!-- FileProvider в AndroidManifest.xml -->
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

---

## 📦 Структура проекта

```
app/src/main/java/com/mazda/control/
├── MainActivity.kt              # UI и управление
├── TBoxSpoilerController.kt     # Подключение к 172.16.2.30:50001
├── TBoxFraming.kt               # Framing утилиты (header + CRC)
├── MockSpoilerController.kt     # Тестовый режим
├── PacketGenerator.kt           # Генерация пакетов (512 байт)
└── ui/theme/                    # Material Design 3 темы

app/src/main/res/xml/
├── network_security_config.xml  # Cleartext traffic
└── file_paths.xml               # Paths для FileProvider
```

---

## 🧪 Тестирование на автомобиле

### План тестирования

1. **Подготовка:**
   - Установить APK на устройство
   - Подключиться к сети автомобиля
   - Запустить приложение

2. **Проверка подключения:**
   - Переключить в режим AG35TspClient
   - Проверить статус подключения
   - Посмотреть логи

3. **Тест команд:**
   - Нажать "Открыть" → ждать 2.2 сек → проверить статус
   - Нажать "Закрыть" → ждать 2.2 сек → проверить статус
   - Повторить 3-5 раз

4. **Анализ логов:**
   - Извлечь лог: `adb pull /data/data/com.mazda.control/files/`
   - Проверить отправленные пакеты
   - Проверить ответы сервера

### Критерии успеха

- ✅ Подключение устанавливается
- ✅ Команды отправляются (512 байт)
- ✅ Спойлер движется (~2.2 сек)
- ✅ Статус обновляется корректно
- ✅ Нет ошибок в логах

---

## 📚 Техническая документация

- **AG35TSPCLIENT_MIGRATION.md** - описание миграции на новый протокол
- **LOGGING_GUIDE_v4.1.md** - полное руководство по логированию
- **USAGE_v4.md** - эта инструкция
- **analysis/docs/** - подробный анализ протокола и архитектуры

### Ключевые документы

| Документ | Описание |
|----------|----------|
| `REAL_SERVER_DISCOVERY.md` | Обнаружение AG35TspClient |
| `TBoxFraming.md` | Спецификация протокола |
| `ALL_COMMUNICATION_METHODS.md` | Сравнение методов |
| `PACKET_ANALYSIS.md` | Анализ пакетов |

---

## 📞 Контакты

- **GitHub:** https://github.com/afayvisovich/mazda-control
- **Версия:** v4.1 (2026-03-15)
- **Протокол:** AG35TspClient (172.16.2.30:50001)

---

## ⚠️ Предупреждения

1. **Root-доступ:** НЕ требуется для логирования
2. **Сеть автомобиля:** обязательно подключение к бортовой сети
3. **Тестирование:** сначала проверьте в TEST MODE
4. **Безопасность:** не используйте во время движения

---

## 🎯 Что нового в v4.1

### Исправления

- ✅ **Логирование:** перенесено в `context.filesDir` (не требует root)
- ✅ **FileProvider:** добавлен для удобного шеринга логов
- ✅ **Android 11+:** полная совместимость
- ✅ **Кнопка "💾 Сохранить лог":** работает через системное меню

### Технические изменения

- `TBoxSpoilerController` теперь принимает `Context` в конструкторе
- Логи сохраняются во внутреннюю директорию приложения
- Добавлен `file_paths.xml` для FileProvider
- Обновлён `AndroidManifest.xml` с provider

---

**🎉 Готово! Приложение готово к тестированию на автомобиле.**
