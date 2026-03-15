# 🚗 MazdaControl v4.0 - Инструкция по использованию

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
2. Подключитесь к сети автомобиля (WiFi/Ethernet)
3. Нажмите кнопку **"Переключить"** для выбора режима:
   - **🚗 AG35TspClient** - реальный автомобиль (172.16.2.30:50001)
   - **🔧 TEST MODE** - эмуляция (без подключения)

### 3. Управление спойлером

| Кнопка | Действие |
|--------|----------|
| **Открыть** | Поднять спойлер вверх |
| **Закрыть** | Опустить спойлер вниз |
| **Переключить** | Смена режима TEST/REAL |
| **💾 Сохранить лог** | Запись лога в файл |

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

## 📁 Логирование

### Расположение логов

```
/data/local/tmp/tbox_log-YYYYMMDD_HHMMSS.txt
```

### Извлечение логов

```bash
# Получить список логов
adb shell ls -la /data/local/tmp/tbox_log-*.txt

# Извлечь последний лог
adb pull /data/local/tmp/tbox_log-<timestamp>.txt

# Или все логи
adb pull /data/local/tmp/tbox_log-*.txt
```

### Просмотр логов

```bash
# В реальном времени
adb logcat | grep -E "TBoxSpoilerController|TBoxFraming|MainActivity"

# Из файла
cat tbox_log-*.txt
```

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
| ❌ Permission denied | Требуется root для логов |

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
   - Извлечь лог: `adb pull /data/local/tmp/tbox_log-*.txt`
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
- **Версия:** v4.0 (2026-03-15)
- **Протокол:** AG35TspClient (172.16.2.30:50001)

---

## ⚠️ Предупреждения

1. **Root-доступ:** требуется для записи логов в `/data/local/tmp/`
2. **Сеть автомобиля:** обязательно подключение к бортовой сети
3. **Тестирование:** сначала проверьте в TEST MODE
4. **Безопасность:** не используйте во время движения

---

**🎉 Готово! Приложение готово к тестированию на автомобиле.**
