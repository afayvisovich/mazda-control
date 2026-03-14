# 🔐 Root-доступ для управления спойлером

**Дата:** 2026-03-14  
**Вопрос:** Можно ли обойти системный барьер с root?  
**Статус:** ✅ **АНАЛИЗ ЗАВЕРШЁН**

---

## 🎯 Что даёт root-доступ

### Без root:
- ❌ Нет доступа к `android.car.hardware.CarPropertyManager`
- ❌ Нет доступа к `ICarProperty` AIDL сервису
- ❌ Нет доступа к системным свойствам
- ✅ Работает только GB32960 Protocol

### С root:
- ✅ Полный доступ к файловой системе
- ✅ Возможность запускать процессы от root
- ✅ Прямой доступ к Binder сервисам
- ✅ Модификация системных файлов

---

## 🔬 Способы использования root

### Способ 1: Прямой вызов ICarProperty через Binder

**Механизм:**
```kotlin
// 1. Получить IBinder от сервиса
val serviceManager = Class.forName("android.os.ServiceManager")
val getService = serviceManager.getMethod("getService", String::class.java)
val binder = getService.invoke(null, "car_property") as IBinder

// 2. Создать прокси к ICarProperty
val carProperty = ICarProperty.Stub.asInterface(binder)

// 3. Вызвать setProperty напрямую
val propertyValue = CarPropertyValue(0x660000c3, 0, 1) // Открыть
carProperty.setProperty(propertyValue, null)
```

**Что нужно:**
- ✅ Root для доступа к ServiceManager
- ✅ Знать точное имя сервиса ("car_property")
- ✅ Иметь классы ICarProperty в classpath

**Проблемы:**
- ⚠️ `ServiceManager` — hidden API (нужен reflection)
- ⚠️ Проверка UID может быть на уровне сервиса
- ⚠️ Нужны все зависимости (CarPropertyValue и т.д.)

---

### Способ 2: Внедрение в системный процесс

**Механизм:**
```bash
# 1. Найти процесс com.mega.rmu (PID 8895)
adb shell su -c "ps -A | grep com.mega.rmu"

# 2. Внедриться через ptrace
adb shell su -c "gdb -p <PID>"

# 3. Вызвать функцию в контексте процесса
call MegaCarProperty.setIntProp(0x660000c3, 1)
```

**Что нужно:**
- ✅ Root с поддержкой ptrace
- ✅ Отладочные инструменты (gdb, strace)
- ✅ Знание адресов функций

**Проблемы:**
- ❌ Сложно реализовать
- ❌ Нестабильно (краш процесса)
- ❌ Требует глубоких знаний

---

### Способ 3: Модификация системных файлов

**Механизм:**
```bash
# 1. Смонтировать /system в读写模式
adb shell su -c "mount -o rw,remount /system"

# 2. Добавить наше приложение в /system/priv-app/
adb shell su -c "cp /sdcard/MazdaControl.apk /system/priv-app/"

# 3. Добавить android.uid.system в манифест
# (требуется модификация APK)

# 4. Перезагрузиться
adb reboot
```

**Что нужно:**
- ✅ Root с доступом к /system
- ✅ Подпись системным ключом (или отключение проверки)
- ✅ Знание структуры прошивки

**Проблемы:**
- ❌ Требует системный ключ (недоступен)
- ❌ Можно "окирпичить" устройство
- ❌ Сложно с современными verified boot

---

### Способ 4: Создание прокси-сервиса

**Механизм:**
```kotlin
// Системное приложение-прокси (устанавливается с root)
class SpoilerProxyService : Service() {
    private val carProperty = MegaCarProperty.getInstance()
    
    override fun onBind(intent: Intent): IBinder {
        return SpoilerProxyStub(this)
    }
    
    fun openSpoiler() {
        carProperty.setIntProp(0x660000c3, 1)
    }
}

// Наше приложение вызывает через сокет
socket.getOutputStream().write("OPEN".toByteArray())
// Прокси-сервис получает и вызывает CarProperty API
```

**Что нужно:**
- ✅ Установить прокси в /system/priv-app/
- ✅ Наше приложение общается через сокет/IPC
- ✅ Прокси имеет системные права

**Преимущества:**
- ✅ Работает стабильно
- ✅ Наше приложение без изменений
- ✅ Использует легальный CarProperty API

**Проблемы:**
- ⚠️ Требует модификации прошивки
- ⚠️ Нужна правильная подпись

---

### Способ 5: Magisk-модуль

**Механизм:**
```bash
# Magisk-модуль для добавления нашего приложения в whitelist
# /system/etc/permissions/privapp-permissions-mazda.xml

<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.mazda.control">
        <permission name="android.car.permission.CAR_INFO"/>
        <permission name="mega.car.property.ACCESS"/>
    </privapp-permissions>
</permissions>
```

**Что нужно:**
- ✅ Root с Magisk
- ✅ Знание точных permission'ов
- ✅ Системная подпись или отключение проверок

**Проблемы:**
- ⚠️ Permission'ы могут требовать системную подпись
- ⚠️ Сложно найти все зависимости

---

## 📊 Сравнение способов с root

| Способ | Сложность | Стабильность | Риск | Работоспособность |
|--------|-----------|--------------|------|-------------------|
| **1. Binder** | Средняя | Средняя | Низкий | ⭐⭐⭐⭐ |
| **2. Ptrace** | Высокая | Низкая | Высокий | ⭐⭐ |
| **3. Модификация /system/** | Высокая | Низкая | Критичный | ⭐⭐ |
| **4. Прокси-сервис** | Средняя | Высокая | Низкий | ⭐⭐⭐⭐⭐ |
| **5. Magisk-модуль** | Низкая | Высокая | Низкий | ⭐⭐⭐⭐ |

---

## 💡 Рекомендуемый подход с root

### **Прокси-сервис (Способ 4)** — лучший вариант

**Почему:**
1. ✅ Использует легальный CarProperty API
2. ✅ Стабильная работа
3. ✅ Минимальные изменения
4. ✅ Наше приложение не требует модификаций

**Реализация:**

**Шаг 1: Создать прокси-сервис**
```kotlin
// SpoilerProxyService.kt (устанавливается в /system/priv-app/)
package com.mega.proxy

import mega.car.MegaCarProperty

class SpoilerProxyService : Service() {
    private val carProperty = MegaCarProperty.getInstance()
    
    private val binder = object : IBinder.Stub() {
        fun openSpoiler() {
            carProperty.setIntProp(0x660000c3, 1)
        }
        
        fun closeSpoiler() {
            carProperty.setIntProp(0x660000c3, 2)
        }
    }
    
    override fun onBind(intent: Intent): IBinder = binder
}
```

**Шаг 2: Установить с root**
```bash
adb root
adb remount
adb push SpoilerProxyService.apk /system/priv-app/
adb reboot
```

**Шаг 3: Наше приложение вызывает прокси**
```kotlin
// В нашем приложении (без изменений)
val proxy = SpoilerProxyBinder.getInterface()
proxy.openSpoiler()
```

---

## 🚫 Почему GB32960 всё ещё лучше

Даже с root, **GB32960 Protocol** остаётся предпочтительнее:

### Преимущества GB32960:
1. ✅ **Не требует root** — работает на любом устройстве
2. ✅ **Простая установка** — обычный APK
3. ✅ **Безопасно** — нет риска "окирпичить"
4. ✅ **Легально** — не нарушает гарантии
5. ✅ **Документировано** — полная спецификация

### Недостатки root-подхода:
1. ❌ **Требует root** — не у всех пользователей
2. ❌ **Сложная установка** — модификация системы
3. ❌ **Риск** — можно сломать прошивку
4. ❌ **Гарантия** — потеря гарантии
5. ❌ **Обновления** — слетит при обновлении

---

## 🎯 Итоговая рекомендация

### **Без root:**
- ✅ **Только GB32960 Protocol**
- ✅ Работает на всех устройствах
- ✅ Безопасно и легально

### **С root (если очень нужно):**
- ✅ **Прокси-сервис** в /system/priv-app/
- ✅ Использует CarProperty API легально
- ✅ Наше приложение без изменений

### **Оптимально:**
> **Использовать GB32960 Protocol** — это работает без root, безопасно и документировано!

---

## 📁 Что можно попробовать (с root)

### 1. **Проверить доступность ICarProperty:**
```bash
adb shell
su
service check car_property
```

### 2. **Попробовать вызвать через sm (Service Manager):**
```bash
adb shell
su
service call car_property 5 i32 0x660000c3 i32 0 i32 1
```

### 3. **Установить прокси-сервис:**
```bash
# Создать APK с SpoilerProxyService
# Установить в /system/priv-app/
adb root && adb remount
adb push SpoilerProxy.apk /system/priv-app/
adb reboot
```

---

**📍 Вывод:** С root возможности есть, но **GB32960 Protocol** остаётся лучшим выбором! 🎯
