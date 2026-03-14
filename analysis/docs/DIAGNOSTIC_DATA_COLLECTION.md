# 📋 Сбор диагностических данных для MazdaControl

**Дата:** 2026-03-14  
**Цель:** Сбор полной информации о системе автомобиля для отладки подключения к порту 32960  
**Статус:** Готово к использованию в автомобиле

---

## 🔍 Быстрая проверка

### Проверка root-доступа
```bash
adb shell whoami
# shell = НЕТ root
# root = ЕСТЬ root
```

```bash
adb shell id
# uid=2000(shell) = НЕТ root
# uid=0(root) = ЕСТЬ root
```

---

## 📦 Минимальный набор (5 минут, БЕЗ root)

### 1. Логи работы приложения
```bash
# Очистить старые логи
adb logcat -c

# Запустить приложение MazdaControl
# Собирать логи в файл
adb logcat -v time > full_logcat.txt

# Остановить сбор через 5 минут (Ctrl+C)
```

**Что ищем:**
- Подключение к порту 32960
- Ошибки подключения
- Ответы от сервера (статус-пакеты)

---

### 2. Список всех процессов
```bash
adb shell ps -A > processes.txt
```

**Зачем:** Определить PID процессов com.mega.*

---

### 3. Список всех пакетов
```bash
adb shell pm list packages -f > packages.txt
```

**Зачем:** Найти все пакеты com.mega.* для извлечения APK

---

### 4. Системные свойства
```bash
adb shell getprop > system_properties.txt
```

**Зачем:** Информация о конфигурации TBox/RMU

---

### 5. Сетевая информация (может работать без root)
```bash
# TCP подключения
adb shell cat /proc/net/tcp > proc_net_tcp.txt

# Unix сокеты
adb shell cat /proc/net/unix > proc_net_unix.txt

# Попытка netstat (может не работать без root)
adb shell netstat -tuln > netstat.txt 2>&1
```

---

### 6. Проверка порта 32960
```bash
# Настроить ADB forward
adb forward tcp:32960 tcp:32960

# Запустить тест подключения
python test_connection.py
```

**Результат:**
- ✅ **Порт открыт** — сервер отвечает
- ❌ **Порт закрыт** — сервер недоступен или не запущен

---

## 🎯 Полный набор (15 минут, ТРЕБУЕТСЯ root)

### 1. Все команды из минимального набора +

### 2. Сетевая диагностика с root
```bash
# Все слушающие TCP порты
adb shell su -c "netstat -tuln" > netstat_tcp.txt

# Все Unix domain сокеты
adb shell su -c "netstat -xl" > netstat_unix.txt

# Конкретно порт 32960
adb shell su -c "netstat -an | grep 32960" > port_32960.txt

# Альтернатива: ss
adb shell su -c "ss -tuln" > ss_output.txt
```

---

### 3. Определение процесса по порту
```bash
# Если есть lsof
adb shell su -c "lsof -i :32960" > lsof_32960.txt

# Если нет lsof, используем /proc/net/tcp
adb shell su -c "cat /proc/net/tcp" | grep ":80E0" > tcp_32960.txt
# 80E0 = 32960 в шестнадцатеричном формате
```

---

### 4. Трассировка сети (strace)
```bash
# Найти PID процесса com.mega.rmu
adb shell pidof com.mega.rmu

# Запустить strace (если установлен)
adb shell su -c "strace -p <PID> -e trace=network -o /data/local/tmp/rmu_network.log"
```

---

## 📥 Извлечение APK для анализа

### Быстрое извлечение (точечно)
```bash
# com.mega.rmu (Remote Management Unit)
adb shell pm path com.mega.rmu
# Копировать путь и выполнить:
adb pull <path> apks/com.mega.rmu.apk

# com.mega.gateway (Gateway Service)
adb shell pm path com.mega.gateway
adb pull <path> apks/com.mega.gateway.apk

# com.mega.car.tbox (TBox Service)
adb shell pm path com.mega.car.tbox
adb pull <path> apks/com.mega.car.tbox.apk

# com.mega.car.carbodyservice (CarBody Service)
adb shell pm path com.mega.car.carbodyservice
adb pull <path> apks/com.mega.car.carbodyservice.apk

# com.mega.hvac (HVAC Service)
adb shell pm path com.mega.hvac
adb pull <path> apks/com.mega.hvac.apk
```

---

### Полное извлечение (все пакеты)
```bash
#!/bin/bash
# collect_all_apks.sh

mkdir -p apks_backup

adb shell pm list packages -f | while read line; do
    apk_path=$(echo $line | cut -d':' -f2)
    pkg_name=$(echo $line | cut -d':' -f1 | cut -d'=' -f2)
    
    echo "Pulling: $pkg_name"
    adb pull "$apk_path" "apks_backup/${pkg_name}.apk" 2>/dev/null
done
```

---

## 🧪 Тест подключения к порту 32960

### test_connection.py
```python
#!/usr/bin/env python3
"""
Тест подключения к порту 32960 через ADB forward
"""

import socket
import time

def test_port_32960():
    """Проверка доступности порта 32960"""
    print("🔍 Проверка порта 32960...")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        result = sock.connect_ex(('127.0.0.1', 32960))
        
        if result == 0:
            print("✅ Порт 32960 ОТКРЫТ")
            
            # Попытаться отправить тестовый пакет (GB32960)
            test_packet = bytes([0x23, 0x23, 0x02] + [0x00] * 509)
            sock.sendall(test_packet)
            print("📤 Тестовый пакет отправлен (512 байт)")
            
            # Ждать ответ
            sock.settimeout(2)
            try:
                response = sock.recv(512)
                if response:
                    print(f"📥 Получен ответ: {len(response)} байт")
                    print(f"   Mark: {response[2]} (2=команда, 3=статус)")
                    print(f"   Byte 355: 0x{response[355]:02X}" if len(response) > 355 else "")
                    print(f"   Byte 363: 0x{response[363]:02X}" if len(response) > 363 else "")
                else:
                    print("⚠️ Пустой ответ")
            except socket.timeout:
                print("⏱️ Таймаут ответа (нормально для некоторых команд)")
            
            sock.close()
            return True
        else:
            print(f"❌ Порт 32960 ЗАКРЫТ (error={result})")
            return False
            
    except Exception as e:
        print(f"❌ Ошибка подключения: {e}")
        return False

if __name__ == '__main__':
    success = test_port_32960()
    exit(0 if success else 1)
```

**Использование:**
```bash
# Настроить forward
adb forward tcp:32960 tcp:32960

# Запустить тест
python test_connection.py

# Удалить forward после теста
adb forward --remove tcp:32960
```

---

## 📁 Структура папок для сбора

```
mazda_diagnostics_YYYYMMDD/
├── logs/
│   ├── full_logcat.txt           # Полные логи системы
│   └── mazda_control_log.txt     # Логи приложения MazdaControl
├── network/
│   ├── netstat_tcp.txt           # TCP порты (требует root)
│   ├── netstat_unix.txt          # Unix сокеты (требует root)
│   ├── proc_net_tcp.txt          # /proc/net/tcp (без root)
│   ├── proc_net_unix.txt         # /proc/net/unix (без root)
│   ├── port_32960.txt            # Информация о порте 32960
│   └── test_connection_output.txt# Результат теста подключения
├── processes/
│   ├── processes.txt             # Список процессов
│   └── packages.txt              # Список пакетов
├── apks/
│   ├── com.mega.rmu.apk          # Remote Management Unit
│   ├── com.mega.gateway.apk      # Gateway Service
│   ├── com.mega.car.tbox.apk     # TBox Service
│   ├── com.mega.car.carbodyservice.apk  # CarBody Service
│   └── com.mega.hvac.apk         # HVAC Service
├── system/
│   └── system_properties.txt     # getprop вывод
└── CHECKLIST.md                  # Чек-лист выполненных шагов
```

---

## ✅ Чек-лист для сбора в автомобиле

### Минимальный (БЕЗ root) — 5 минут
- [ ] `adb logcat -v time > logs/full_logcat.txt`
- [ ] `adb shell ps -A > processes/processes.txt`
- [ ] `adb shell pm list packages -f > processes/packages.txt`
- [ ] `adb shell getprop > system/system_properties.txt`
- [ ] `adb shell cat /proc/net/tcp > network/proc_net_tcp.txt`
- [ ] `adb shell cat /proc/net/unix > network/proc_net_unix.txt`
- [ ] `adb forward tcp:32960 tcp:32960` + `python test_connection.py`
- [ ] APK: com.mega.rmu, com.mega.gateway, com.mega.car.tbox

### Полный (С root) — 15 минут
- [ ] Всё из минимального набора
- [ ] `adb shell su -c "netstat -tuln" > network/netstat_tcp.txt`
- [ ] `adb shell su -c "netstat -xl" > network/netstat_unix.txt`
- [ ] `adb shell su -c "lsof -i :32960" > network/lsof_32960.txt`
- [ ] Полное извлечение всех APK

---

## 🚀 Быстрый старт (копировать и вставить)

### Шаг 1: Создать директорию
```bash
mkdir -p mazda_diagnostics_$(date +%Y%m%d)/{logs,network,processes,apks,system}
cd mazda_diagnostics_$(date +%Y%m%d)
```

### Шаг 2: Сбор данных (БЕЗ root)
```bash
# Очистить логи
adb logcat -c

# Собрать логи (5 минут)
timeout 300 adb logcat -v time > logs/full_logcat.txt &

# Процессы и пакеты
adb shell ps -A > processes/processes.txt
adb shell pm list packages -f > processes/packages.txt
adb shell getprop > system/system_properties.txt

# Сеть (без root)
adb shell cat /proc/net/tcp > network/proc_net_tcp.txt
adb shell cat /proc/net/unix > network/proc_net_unix.txt

# Тест порта
adb forward tcp:32960 tcp:32960
python test_connection.py > network/test_connection_output.txt 2>&1
adb forward --remove tcp:32960

# Извлечь важные APK
for pkg in com.mega.rmu com.mega.gateway com.mega.car.tbox com.mega.car.carbodyservice com.mega.hvac; do
    echo "Extracting: $pkg"
    path=$(adb shell pm path $pkg 2>/dev/null | cut -d':' -f2)
    if [ -n "$path" ]; then
        adb pull "$path" "apks/${pkg}.apk" 2>/dev/null
    fi
done

echo "✅ Сбор данных завершён!"
```

---

## 📊 Анализ собранных данных

После сбора:

1. **Проверить логи:**
   ```bash
   grep -i "32960\|CallServerInterceptor\|MazdaControl" logs/full_logcat.txt
   ```

2. **Проверить процессы:**
   ```bash
   grep "com.mega" processes/processes.txt
   ```

3. **Проверить пакеты:**
   ```bash
   grep "com.mega" processes/packages.txt
   ```

4. **Проверить сеть:**
   ```bash
   grep -i "80E0" network/proc_net_tcp.txt  # 80E0 = 32960 hex
   ```

5. **Проверить тест подключения:**
   ```bash
   cat network/test_connection_output.txt
   ```

---

## 🎯 Ключевые вопросы

После анализа ответим:

1. ✅ **Слушает ли кто-то порт 32960?** → netstat / /proc/net/tcp
2. ✅ **Какой процесс слушает?** → lsof или сопоставление PID
3. ✅ **Есть ли процесс com.mega.rmu?** → ps
4. ✅ **Доступен ли порт из приложения?** → test_connection.py
5. ✅ **Какие ещё пакеты используют GB32960?** → pm list packages

---

## 📞 Контакты для связи

При возникновении проблем:
- Проверьте, что ADB видит устройство: `adb devices`
- Убедитесь, что отладка по USB включена на автомобиле
- Перезапустите ADB: `adb kill-server && adb start-server`

---

**Удачи в сборе данных! 🚗🔧**
