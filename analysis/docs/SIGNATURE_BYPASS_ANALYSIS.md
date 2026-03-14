# 🔓 Обход ограничения signature|system для CarProperty API

**Дата:** 2026-03-14  
**Цель:** Анализ возможностей обхода защиты CarProperty API без оригинальной подписи Mazda  
**Статус:** Исследование методов

---

## 📋 Проблема

**CarProperty API требует:**
```xml
<permission android:name="android.car.permission.CAR_CONTROL"
    android:protectionLevel="signature|system" />
```

**Это означает:**
- ❌ Третьи приложения не могут получить доступ
- ✅ Только приложения с **системной подписью** или **подписью OEM**
- ✅ Или приложения в **системном разделе**

---

## 🎯 Метод 1: Размещение в системном разделе

### Суть метода
Приложения в `/system/priv-app/` или `/system/app/` получают системные привилегии.

### Требования
- **Root-доступ** для записи в `/system/`
- **Отключенная верификация** (AVB/dm-verity)
- **Подпись платформы** или совпадение подписи с OEM

### Реализация
```bash
# 1. Переместить APK в системный раздел
adb root
adb remount
adb push MazdaControl.apk /system/priv-app/MazdaControl/

# 2. Установить правильные права
adb shell chmod 644 /system/priv-app/MazdaControl/MazdaControl.apk
adb shell chown root:root /system/priv-app/MazdaControl/MazdaControl.apk

# 3. Перезагрузка
adb reboot
```

### Проблемы
- ❌ Требуется **подпись платформы** (совпадает с `android.uid.system`)
- ❌ Без подписи приложение **не запустится**
- ❌ Риск **brick** устройства
- ❌ **OTA-обновления** удалят модификацию

### Статус
**❌ НЕ работает без подписи платформы**

---

## 🎯 Метод 2: Клонирование системной подписи

### Суть метода
Извлечь системную подпись и подписать приложение тем же ключом.

### Требования
- Доступ к `/system/framework/` или `/etc/security/`
- Извлечение сертификата платформы

### Реализация
```bash
# 1. Найти системный сертификат
adb shell ls /system/etc/security/
adb shell ls /system/framework/

# 2. Извлечь сертификат
adb pull /system/etc/security/otacerts.zip
adb pull /system/framework/framework-res.apk

# 3. Извлечь publicKey из APK
unzip framework-res.apk META-INF/CERT.RSA
openssl pkcs7 -inform DER -in CERT.RSA -out cert.pem -text -noout
```

### Проблемы
- ❌ Нужен **приватный ключ** для подписи (только сертификат ≠ ключ)
- ❌ Приватный ключ **никогда не хранится** на устройстве
- ❌ Ключ хранится на **серверах OEM**

### Статус
**❌ НЕВОЗМОЖНО** — приватный ключ недоступен

---

## 🎯 Метод 3: Использование уже подписанного системного приложения

### Суть метода
Внедрить код в уже подписанное системное приложение (inject).

### Требования
- Декompiled системного APK
- Возможность модификации smali-кода
- Обратная сборка и подпись

### Реализация
```bash
# 1. Декompiled системного приложения
apktool d HuronCarService.apk

# 2. Добавить наш код в smali
# Изменить HuronCarService.smali для вызова нашего кода

# 3. Обратная сборка
apktool b HuronCarService -o HuronCarService-mod.apk

# 4. Подпись (сломает оригинальную подпись!)
jarsigner HuronCarService-mod.apk my-keystore.jks
```

### Проблемы
- ❌ **Сломает оригинальную подпись** — приложение не запустится
- ❌ Система проверит **целостность** при загрузке
- ❌ Требуется **отключенная верификация**

### Статус
**❌ НЕ работает** — ломает подпись системы

---

## 🎯 Метод 4: Magisk-модуль для подмены подписи

### Суть метода
Magisk может подменять проверку подписи на лету.

### Требования
- **Root + Magisk**
- Модуль для подмены подписи

### Реализация
```bash
# 1. Установить Magisk
# 2. Установить модуль (например, "Signature Spoofing")
# 3. Включить подмену подписи
# 4. Установить приложение
```

### Пример модуля
```bash
# /system/etc/permissions/privapp-permissions-mazda.xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.mazda.control">
        <permission name="android.car.permission.CAR_CONTROL"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
    </privapp-permissions>
</permissions>
```

### Проблемы
- ❌ Требует **Magisk** (root)
- ❌ Не все системы поддерживают
- ❌ Может быть **нестабильно**
- ❌ SafetyNet не пройдёт

### Статус
**⚠️ ВОЗМОЖНО, но сложно** — требует Magisk и настройки

---

## 🎯 Метод 5: Эксплойт уязвимостей

### Суть метода
Использовать уязвимости в системе для получения привилегий.

### Типы уязвимостей
- **CVE в Android Car**
- **Уязвимости в OEM-приложениях**
- **Эскалация привилегий через ядро**

### Примеры
```bash
# Эксплойт через уязвимость в Binder
# Эксплойт через переполнение в нативной библиотеке
# Эксплойт через неправильную проверку разрешений
```

### Проблемы
- ❌ **Нестабильно** — зависит от версии ПО
- ❌ **Опасно** — может brick устройство
- ❌ **Закрывается** в обновлениях
- ❌ **Требует глубоких знаний**

### Статус
**⚠️ ТЕОРЕТИЧЕСКИ возможно** — но сложно и рискованно

---

## 🎯 Метод 6: Proxy-сервис с системными правами

### Суть метода
Создать сервис, который:
1. Запускается как **системное приложение** (через Magisk/системный раздел)
2. Принимает команды от нашего приложения
3. Вызывает CarProperty API от своего имени

### Архитектура
```
MazdaControl (app) ←→ ProxyService (system) ←→ CarProperty API
     (без прав)          (с правами system)      (доступно)
```

### Реализация
```java
// ProxyService (в системном разделе)
public class CarPropertyProxyService extends Service {
    private CarPropertyManager carPropertyMgr;
    
    @Override
    public void onCreate() {
        carPropertyMgr = (CarPropertyManager) getSystemService(CAR_PROPERTY_SERVICE);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return new IProxyService.Stub() {
            @Override
            public void setProperty(int propId, int value) {
                // Вызов от имени системы!
                carPropertyMgr.setIntProperty(propId, value, 0);
            }
        };
    }
}
```

### Проблемы
- ❌ Требует **системного компонента**
- ❌ Нужна **межпроцессная коммуникация** (AIDL)
- ❌ **Сложность реализации**

### Статус
**✅ РАБОТАЕТ** — если удастся разместить сервис в системе

---

## 🎯 Метод 7: Модификация system-image

### Суть метода
Создать кастомный system-image с нашим приложением в `/system/priv-app/`.

### Требования
- **Разблокированный bootloader**
- **Кастомный recovery** (TWRP)
- **Распакованный system-image**

### Реализация
```bash
# 1. Распаковать system.img
simg2img system.img system.raw
mount -o loop system.raw /mnt/system

# 2. Добавить приложение
cp MazdaControl.apk /mnt/system/priv-app/MazdaControl/

# 3. Упаковать обратно
umount /mnt/system
img2simg system.raw system-mod.img

# 4. Прошить
fastboot flash system system-mod.img
```

### Проблемы
- ❌ **Сломает OTA**
- ❌ **Требуется разблокированный bootloader**
- ❌ **Риск brick**
- ❌ **Долго и сложно**

### Статус
**✅ РАБОТАЕТ** — но очень сложно

---

## 🎯 Метод 8: Использование OEM-сертификатов

### Суть метода
Некоторые OEM оставляют сертификаты в доступных местах.

### Где искать
```bash
# Проверить наличие сертификатов
adb shell ls /system/etc/security/
adb shell ls /vendor/etc/security/

# Извлечь
adb pull /system/etc/security/otacerts.zip
```

### Анализ
```bash
# Распаковать
unzip otacerts.zip

# Проверить
openssl x509 -in cert.der -inform DER -text -noout
```

### Проблемы
- ❌ Только **сертификат** (публичный ключ)
- ❌ **Приватный ключ** недоступен
- ❌ Нельзя **подписать** приложение

### Статус
**❌ НЕ работает** — только для верификации, не для подписи

---

## 🎯 Метод 9: Reflection + скрытый API

### Суть метода
Использовать reflection для доступа к скрытым методам CarPropertyManager.

### Реализация
```java
// Попытка доступа через reflection
Class<?> carPropertyMgrClass = Class.forName("android.car.hardware.CarPropertyManager");
Method setIntProp = carPropertyMgrClass.getDeclaredMethod(
    "setIntProperty", 
    int.class, int.class, int.class
);
setIntProp.setAccessible(true);

// Вызов без проверки разрешений
setIntProp.invoke(carPropertyMgr, propId, value, 0);
```

### Проблемы
- ❌ Проверка разрешений **внутри метода**
- ❌ Выбросит **SecurityException**
- ❌ Не обходит защиту

### Статус
**❌ НЕ работает** — проверка на уровне системы

---

## 🎯 Метод 10: Подмена CarPropertyManager

### Суть метода
Создать свой CarPropertyManager и внедрить через reflection.

### Реализация
```java
// Создать фейковый менеджер
class FakeCarPropertyManager {
    public void setIntProperty(int propId, int value, int area) {
        // Отправить через GB32960 socket!
        sendViaSocket(propId, value);
    }
}

// Внедрить через reflection
Field field = ContextImpl.class.getDeclaredField("CAR_PROPERTY_SERVICE");
field.setAccessible(true);
field.set(null, new FakeCarPropertyManager());
```

### Проблемы
- ❌ **Сложно** — много зависимостей
- ❌ Может вызвать **краш системы**
- ❌ Не везде сработает

### Статус
**⚠️ ТЕОРЕТИЧЕСКИ** — очень сложно

---

## 📊 Сравнение методов

| Метод | Сложность | Эффективность | Риск | Требуется root |
|-------|-----------|---------------|------|----------------|
| 1. Системный раздел | Средняя | ❌ (нужна подпись) | Высокий | ✅ |
| 2. Клонирование подписи | Высокая | ❌ (нет ключа) | Низкий | ✅ |
| 3. Inject в системное APK | Высокая | ❌ (ломает подпись) | Высокий | ✅ |
| 4. Magisk-модуль | Средняя | ⚠️ (нестабильно) | Средний | ✅ |
| 5. Эксплойты | Очень высокая | ⚠️ (зависит) | Очень высокий | ✅ |
| 6. Proxy-сервис | Средняя | ✅ (работает) | Средний | ✅ |
| 7. Кастомный system-image | Очень высокая | ✅ (работает) | Высокий | ✅ |
| 8. OEM-сертификаты | Низкая | ❌ (только cert) | Низкий | ✅ |
| 9. Reflection | Низкая | ❌ (SecurityException) | Низкий | ❌ |
| 10. Подмена менеджера | Очень высокая | ⚠️ (сложно) | Высокий | ✅ |

---

## 🏆 Реалистичные варианты

### ✅ Вариант A: Magisk + Proxy-сервис

**Что нужно:**
1. Установить Magisk (root)
2. Создать Magisk-модуль с Proxy-сервисом
3. Сервис размещается в `/system/priv-app/`
4. Сервис имеет права `android.uid.system`
5. Приложение общается с сервисом через AIDL

**Преимущества:**
- ✅ Работает без перепрошивки
- ✅ Относительно безопасно
- ✅ Можно обновлять

**Недостатки:**
- ⚠️ Требует Magisk
- ⚠️ Сложность настройки
- ⚠️ SafetyNet не пройдёт

---

### ✅ Вариант B: Кастомный system-image

**Что нужно:**
1. Разблокировать bootloader
2. Распаковать system.img
3. Добавить приложение в `/system/priv-app/`
4. Подписать системной подписью (если есть ключи)
5. Прошить обратно

**Преимущества:**
- ✅ Полная интеграция
- ✅ Работает "из коробки"

**Недостатки:**
- ❌ Очень сложно
- ❌ Ломает OTA
- ❌ Риск brick

---

### ✅ Вариант C: GB32960 socket (уже работает!)

**Что нужно:**
- Ничего! Уже работает без CarProperty API

**Преимущества:**
- ✅ Не требует root
- ✅ Не требует системной подписи
- ✅ Прямой доступ к CAN-шине
- ✅ Официальный протокол

**Недостатки:**
- ⚠️ Только для команд (не для чтения всех свойств)

---

## 🎯 Рекомендация

**Используйте GB32960 socket!**

**Почему:**
1. ✅ **Уже работает** — не нужно обходить защиту
2. ✅ **Без root** — не требует модификации системы
3. ✅ **Официальный протокол** — использует штатный механизм
4. ✅ **Безопасно** — не ломает систему
5. ✅ **Быстро** — прямая отправка команд

**CarProperty API НЕ НУЖЕН** — это избыточный путь!

---

## 🔮 Если очень нужен CarProperty API

**Единственный рабочий способ:**

1. **Получить root** (Magisk)
2. **Создать Proxy-сервис** как Magisk-модуль
3. **Разместить в `/system/priv-app/`**
4. **Подписать системной подписью** (если есть ключи)
5. **Общаться через AIDL**

**Но это:**
- Сложно
- Требует root
- Ломает SafetyNet
- Нестабильно

**Вывод:** **НЕ СТОИТ УСИЛИЙ** — GB32960 работает лучше!

---

## 📚 Выводы

| Цель | Решение |
|------|---------|
| Управление спойлером | ✅ **GB32960 socket** (уже работает) |
| Чтение свойств автомобиля | ⚠️ **Только через root + proxy** |
| Полный доступ к CAN | ✅ **GB32960 socket** |
| Интеграция с системой | ❌ **Требуется системная подпись** |

**Итог:** **GB32960 — оптимальный путь**, обход защиты CarProperty API не нужен! 🎯
