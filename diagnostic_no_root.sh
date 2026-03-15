#!/bin/bash
# Диагностика сети без root - упрощённая версия

LOGFILE="network_diagnostic_$(date +%Y%m%d_%H%M%S).txt"

echo "🔍 Диагностика сети AG35TspClient" | tee -a $LOGFILE
echo "Дата: $(date)" | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 1. IP адреса
echo "📍 1. IP адреса интерфейсов:" | tee -a $LOGFILE
adb shell ip addr show 2>/dev/null | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 2. Таблица маршрутизации
echo "📍 2. Таблица маршрутизации:" | tee -a $LOGFILE
adb shell ip route 2>/dev/null | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 3. ARP таблица
echo "📍 3. ARP таблица (соседи):" | tee -a $LOGFILE
adb shell ip neigh 2>/dev/null | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 4. Ping шлюза
echo "📍 4. Проверка шлюза 172.16.2.1:" | tee -a $LOGFILE
adb shell ping -c 3 -W 1 172.16.2.1 2>/dev/null | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 5. Ping целевого сервера
echo "📍 5. Проверка 172.16.2.30:" | tee -a $LOGFILE
adb shell ping -c 3 -W 1 172.16.2.30 2>/dev/null | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 6. Netstat без PID
echo "📍 6. Активные TCP подключения:" | tee -a $LOGFILE
adb shell netstat -an 2>/dev/null | grep ESTABLISHED | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 7. Listening порты
echo "📍 7. Слушающие порты:" | tee -a $LOGFILE
adb shell netstat -tln 2>/dev/null | head -30 | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

# 8. DNS информация
echo "📍 8. DNS конфигурация:" | tee -a $LOGFILE
adb shell getprop | grep dns | tee -a $LOGFILE
echo "" | tee -a $LOGFILE

echo "✅ Диагностика завершена. Лог: $LOGFILE"
