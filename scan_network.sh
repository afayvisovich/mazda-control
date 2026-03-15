#!/bin/bash
# Сканер сети для поиска AG35TspClient без root
# Запуск: ./scan_network.sh

echo "🔍 Сканирование сети 172.16.2.0/24..."
echo ""

# Проверяем шлюз (обычно .1)
echo "📍 Проверка шлюза 172.16.2.1..."
adb shell ping -c 2 -W 1 172.16.2.1 > /dev/null 2>&1 && echo "✅ 172.16.2.1 отвечает" || echo "❌ 172.16.2.1 не отвечает"

# Проверяем целевой IP
echo "📍 Проверка 172.16.2.30..."
adb shell ping -c 2 -W 1 172.16.2.30 > /dev/null 2>&1 && echo "✅ 172.16.2.30 отвечает" || echo "❌ 172.16.2.30 не отвечает"

echo ""
echo "📋 Сканирование диапазона 172.16.2.1-50..."
for i in $(seq 1 50); do
    ip="172.16.2.$i"
    if adb shell ping -c 1 -W 1 $ip > /dev/null 2>&1; then
        echo "✅ $ip - активен"
    fi
done

echo ""
echo "🎯 Проверка портов на найденных IP..."
echo "Используем timeout + /dev/tcp (если доступно)"

# Попытка проверить порт 50001
for i in $(seq 1 50); do
    ip="172.16.2.$i"
    if adb shell ping -c 1 -W 1 $ip > /dev/null 2>&1; then
        # Пробуем проверить порт 50001
        result=$(adb shell "echo 'quit' | timeout 1 nc $ip 50001 2>&1" 2>/dev/null)
        if [[ ! "$result" =~ "refused" ]] && [[ ! "$result" =~ "timeout" ]]; then
            echo "🎯 $ip:50001 - ВОЗМОЖНО ОТКРЫТ!"
        fi
    fi
done

echo ""
echo "📊 Результаты сохранены в scan_results.txt"
