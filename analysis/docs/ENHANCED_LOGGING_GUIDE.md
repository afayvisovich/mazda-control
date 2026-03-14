# 📋 Расширенное логирование в MazdaControl

**Дата:** 2026-03-14  
**Версия:** APK с расширенным логированием подключения

---

## ✅ Что добавлено

### 1. **Детальное логирование подключения** (SpoilerController.kt)

**4 шага подключения:**
```
=== START CONNECTION ===
Server: 127.0.0.1:32960
Step 1: Creating socket...
Socket created: 12345678
Step 2: Connecting to 127.0.0.1:32960...
Connected in 15ms
Step 3: Connection details:
  Local address: 127.0.0.1:45678
  Remote address: 127.0.0.1:32960
  Is connected: true
  Is bound: true
Step 4: Creating output stream...
Output stream created: 87654321
=== CONNECTION SUCCESSFUL ===
✅ Connected successfully
```

**5 типов ошибок:**
- ❌ **UnknownHostException** — адрес не найден
- ❌ **ConnectException** — сервер отклонил подключение
  - 1. Server not running
  - 2. Wrong port
  - 3. Firewall blocking
- ❌ **SocketTimeoutException** — таймаут >5 сек
- ❌ **IOException** — другая ошибка ввода/вывода
- ❌ **Exception** — неожиданная ошибка

---

### 2. **Детальное логирование отправки команд**

**Что логируется:**
```
=== SENDING COMMAND ===
Action: OPEN
Packet size: 512 bytes
Header (30 bytes): 23 23 02 4C 56 52 48 ...
Key bytes:
  Byte[2] (mark): 0x02
  Byte[355] (command): 0x2B
  Byte[363] (marker): 0x5A
  Byte[365] (value): 0x00
Sending packet...
✅ Command sent in 3ms
✅ Command OPEN sent (512 bytes)
```

---

### 3. **Логирование в MainActivity.kt**

**При подключении:**
```
=== CONNECTING CONTROLLERS ===
🚗 REAL MODE: Подключение к автомобилю...
🔌 Server: 127.0.0.1:32960 (TCP)
✅ REAL: Успешное подключение к автомобилю
📊 Local: 127.0.0.1:45678
📊 Remote: 127.0.0.1:32960
```

**При ошибке:**
```
❌ REAL: Не удалось подключиться к автомобилю
⚠️ Проверьте:
  1. Запущен ли сервер на автомобиле
  2. Правильность порта (32960)
  3. Настройки ADB forward
📋 См. логи в /data/local/tmp/
```

---

## 📁 Куда записываются логи

### 1. **Файл лога** (в приложении)
```
/data/local/tmp/log-YYYYMMDD_HHMMSS.txt
```

**Извлечение:**
```bash
adb shell su -c "cp /data/local/tmp/log-*.txt /sdcard/Download/"
adb pull /sdcard/Download/log-*.txt
```

### 2. **ADB Logcat** (системный лог)
```bash
adb logcat -v time > full_logcat.txt
```

**Фильтр по тегам:**
```bash
adb logcat -v time -s "SpoilerController" "MainActivity" "MockSpoilerController"
```

---

## 🔍 Что покажут логи

### ✅ Успешное подключение
```
Connected in 15ms
Local address: 127.0.0.1:45678
Remote address: 127.0.0.1:32960
```
**Вывод:** Сервер работает, порт 32960 активен

---

### ❌ ConnectException
```
❌ ConnectException: Connection refused
Server refused connection on 127.0.0.1:32960
Possible reasons:
  1. Server not running
  2. Wrong port
  3. Firewall blocking
```
**Вывод:** Сервер не запущен или порт неправильный

---

### ❌ SocketTimeoutException
```
❌ SocketTimeoutException: timeout
Connection timeout after 5000ms
```
**Вывод:** Сервер есть, но не отвечает (завис?)

---

### ❌ UnknownHostException
```
❌ UnknownHostException: 127.0.0.1
Server address 127.0.0.1:32960 not found
```
**Вывод:** Проблема с сетью (маловероятно для localhost)

---

## 🎯 Как использовать

### 1. **Перед запуском в автомобиле**
```bash
# Очистить старые логи
adb logcat -c

# Начать сбор логов
adb logcat -v time > full_logcat.txt
```

### 2. **Запустить приложение**
- Открыть MazdaControl
- Переключиться в **REAL MODE**
- Наблюдать логи в приложении

### 3. **После подключения**
```bash
# Остановить logcat (Ctrl+C)
# Извлечь логи приложения
adb shell su -c "cp /data/local/tmp/log-*.txt /sdcard/Download/"
adb pull /sdcard/Download/
```

### 4. **Анализ**
```bash
# Найти подключение
grep "CONNECTING CONTROLLERS" full_logcat.txt

# Найти ошибки
grep "ConnectException\|SocketTimeoutException" full_logcat.txt

# Найти отправленные команды
grep "SENDING COMMAND" full_logcat.txt
```

---

## 📊 Примеры логов

### Пример 1: Успешное подключение и команда
```
=== CONNECTING CONTROLLERS ===
🚗 REAL MODE: Подключение к автомобилю...
🔌 Server: 127.0.0.1:32960 (TCP)
=== START CONNECTION ===
Server: 127.0.0.1:32960
Step 1: Creating socket...
Socket created: 12345678
Step 2: Connecting to 127.0.0.1:32960...
Connected in 12ms
Step 3: Connection details:
  Local address: 127.0.0.1:45678
  Remote address: 127.0.0.1:32960
  Is connected: true
Step 4: Creating output stream...
Output stream created: 87654321
=== CONNECTION SUCCESSFUL ===
✅ Connected successfully
✅ REAL: Успешное подключение к автомобилю
📊 Local: 127.0.0.1:45678
📊 Remote: 127.0.0.1:32960

=== SENDING COMMAND ===
Action: OPEN
Packet size: 512 bytes
Header (30 bytes): 23 23 02 4C 56 52 48 ...
Key bytes:
  Byte[2] (mark): 0x02
  Byte[355] (command): 0x2B
  Byte[363] (marker): 0x5A
✅ Command sent in 3ms
```

---

### Пример 2: Ошибка подключения
```
=== CONNECTING CONTROLLERS ===
🚗 REAL MODE: Подключение к автомобилю...
🔌 Server: 127.0.0.1:32960 (TCP)
=== START CONNECTION ===
Server: 127.0.0.1:32960
Step 1: Creating socket...
Socket created: 12345678
Step 2: Connecting to 127.0.0.1:32960...
=== CONNECTION FAILED ===
❌ ConnectException: Connection refused
Server refused connection on 127.0.0.1:32960
Possible reasons:
  1. Server not running
  2. Wrong port
  3. Firewall blocking
❌ REAL: Не удалось подключиться к автомобилю
⚠️ Проверьте:
  1. Запущен ли сервер на автомобиле
  2. Правильность порта (32960)
  3. Настройки ADB forward
```

---

## 🚀 Быстрая диагностика

### Команды для быстрого анализа
```bash
# Было ли подключение?
grep "Connected successfully" full_logcat.txt

# Была ли ошибка?
grep "CONNECTION FAILED" full_logcat.txt

# Отправлялись ли команды?
grep "SENDING COMMAND" full_logcat.txt

# Какой тип ошибки?
grep "ConnectException\|SocketTimeoutException\|IOException" full_logcat.txt
```

---

## 📞 Что делать с логами

1. **Скопировать** `full_logcat.txt` и `log-*.txt`
2. **Отправить** для анализа
3. **Сравнить** с ожидаемым поведением

**Ожидаемое поведение:**
- ✅ Подключение за <100ms
- ✅ Local address: 127.0.0.1:xxxxx
- ✅ Remote address: 127.0.0.1:32960
- ✅ Команды отправляются за <10ms

**Неожиданное поведение:**
- ❌ ConnectException — сервер не запущен
- ❌ SocketTimeoutException — сервер завис
- ❌ Подключение >1 секунды — проблема с сетью

---

**Удачи в тестировании! 🚗🔧**
