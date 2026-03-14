# CarbodyController & Spoiler Control Analysis

## Обзор

**CarbodyController** — контроллер для управления кузовом автомобиля (двери, замки, аудио при открытии дверей).

**CustomInputEventController** — обработчик кастомных событий ввода, включая **управление спойлером**.

---

## 🎯 Управление спойлером

### Property ID (Сигналы)

| ID | Hex | Название | Описание |
|----|-----|----------|----------|
| `BODYINFO_HU_SPOILERSWITCH` | `0x660000c3` | Переключатель спойлера (HU) | Кнопка управления |
| `BODYINFO_PTS_SPOILERPOSITION` | `0x660000c4` | Позиция спойлера (PTS) | Текущая позиция |
| `BODYINFO_PTS_SPOILERMOVEMENT` | `0x660000c5` | Движение спойлера (PTS) | Статус движения |
| `LIGHT_HU_SPOILERWELCOMESWITCH` | `0x660000d1` | Приветственный режим спойлера | Световое шоу |

### Значения

**Позиция спойлера (`0x660000c4`):**
- `0x65` (101) = Спойлер открыт

**Движение спойлера (`0x660000c5`):**
- `0` = Нет движения
- `1` = В процессе движения
- `2` = Завершил движение

**Команда на спойлер (`0x660000c3`):**
- `1` = Закрыть
- `2` = Открыть

**Приветственный режим (`0x660000d1`):**
- Управление спойлером в режиме приветствия (световое шоу)

---

## 📋 Алгоритм работы (CustomInputEventController)

### 1. Проверка конфигурации

```java
// Проверка наличия электрического спойлера
int hasSpoiler = MegaSystemProperties.getInt("ro.ecu.config.ELECTRIC_TAIL", -1);
if (hasSpoiler != 1) {
    Log.i("Not have SpoilerKey config");
    return;
}
```

**System Property:** `ro.ecu.config.ELECTRIC_TAIL`
- **ECU Property ID:** `ID_CONFIG_ELECTRIC_REAR_SPOILER` = `0x3310025a`
- **Значение:** `1` = спойлер присутствует, `0` = отсутствует

### 2. Проверка питания

```java
if (!isPowerOn()) {
    return; // Питание выключено
}
```

### 3. Проверка валидности сигналов

```java
// Проверка позиции спойлера
if (!isSignalValid(0x660000c4)) {
    Log.i("BODYINFO_PTS_SPOILERPOSITION invalid");
    return;
}

// Проверка статуса движения
if (!isSignalValid(0x660000c5)) {
    Log.i("BODYINFO_PTS_SPOILERMOVEMENT invalid");
    return;
}
```

### 4. Чтение текущего состояния

```java
int position = mMegaCarProperty.getIntProp(0x660000c4);  // Позиция
int moveState = mMegaCarProperty.getIntProp(0x660000c5); // Движение
```

### 5. Логика переключения

```java
// Если спойлер открыт (position=101) И движется (moveState=1)
// ИЛИ движение завершено (moveState=2)
if ((position == 101 && moveState == 1) || moveState == 2) {
    // Закрыть спойлер
    mMegaCarProperty.setIntProp(0x660000c3, 2);
    showToast(R.string.spoiler_close);
} else {
    // Открыть спойлер
    mMegaCarProperty.setIntProp(0x660000c3, 1);
    showToast(R.string.spoiler_open);
}
```

**⚠️ Внимание:** В коде инвертированная логика!
- `setIntProp(0x660000c3, 2)` = **Закрыть** (показывает toast "spoiler_close")
- `setIntProp(0x660000c3, 1)` = **Открыть** (показывает toast "spoiler_open")

---

## 🧩 CarbodyController

### Основные функции

1. **Управление дверьми**
   - Отслеживание состояния дверей (`mDoorsState`)
   - Приглушение медиа при открытии двери (`duckGroupVolume`)

2. **Настройка аудио**
   - `CarAudioManager` для управления звуком
   - Media duck при открытии дверей

3. **Индикация низкого заряда**
   - `BattlowDialog` — диалог низкого заряда батареи
   - Показывает при низком уровне заряда

4. **TV Mode**
   - Управление режимом ТВ
   - Таймаут для возврата в обычный режим

5. **Запуск активностей**
   - `CarControlActivity` — управление автомобилем
   - Проверка запрета на запуск (безопасность)

### Property ID используемые в CarbodyController

```java
// Питание (0x66000003)
int power = getIntProp(0x66000003);
if (power == 0) {
    Log.e("power is off, don't show CarControlActivity");
    return;
}
```

---

## 🔧 MegaCarProperty API для спойлера

### Чтение состояния

```java
// Позиция спойлера
int position = MegaCarProperty.getInstance().getIntProp(0x660000c4);

// Статус движения
int movement = MegaCarProperty.getInstance().getIntProp(0x660000c5);
```

### Управление

```java
// Открыть спойлер
MegaCarProperty.getInstance().setIntProp(0x660000c3, 1);

// Закрыть спойлер
MegaCarProperty.getInstance().setIntProp(0x660000c3, 2);
```

### Подписка на события

```java
CarPropertyEventCallback callback = new CarPropertyEventCallback() {
    @Override
    public void onEvent(CarProperty carProperty) {
        int propId = carProperty.getPropertyId();
        
        if (propId == 0x660000c4) {
            // Изменилась позиция спойлера
            int position = getIntSignal(carProperty.getData());
        } else if (propId == 0x660000c5) {
            // Изменился статус движения
            int movement = getIntSignal(carProperty.getData());
        }
    }
};

Set<Integer> propertySet = new HashSet<>();
propertySet.add(0x660000c4);
propertySet.add(0x660000c5);

MegaCarProperty.getInstance().registerCallback(callback, propertySet);
```

---

## 📱 UI элементы

### Строковые ресурсы

```smali
.field public static final spoiler_close:I = 0x7f0f00df
.field public static final spoiler_open:I = 0x7f0f00e0
```

### Toast уведомления

```java
// При закрытии
CommonToast.singleToast(context, R.string.spoiler_close, 0);

// При открытии
CommonToast.singleToast(context, R.string.spoiler_open, 0);
```

---

## 🛡️ Проверки безопасности

### 1. Конфигурация автомобиля

```java
// Проверка наличия электрического спойлера
ro.ecu.config.ELECTRIC_TAIL == 1
```

### 2. Питание

```java
// Проверка включения питания
isPowerOn() == true
```

### 3. Валидность сигналов

```java
// Проверка что сигнал доступен
isSignalValid(propId) == true
```

### 4. InteractSetter блокировки

```java
// Блокировка при вождении (из InteractSetterImpl)
if (isDriveRestrict()) {
    // feature |= 0x02
    // Возможно блокирует управление спойлером
}
```

---

## 📊 State Machine спойлера

```
[Покоится закрыт]
    ↓ (команда 0x660000c3=1)
[Движение вверх] (0x660000c5=1)
    ↓
[Открыт] (0x660000c4=101, 0x660000c5=2)
    ↓ (команда 0x660000c3=2)
[Движение вниз] (0x660000c5=1)
    ↓
[Покоится закрыт] (0x660000c5=2)
```

---

## 🔍 Отличия от GB32960 протокола

### ⚠️ CarProperty API (НЕДОСТУПЕН для сторонних приложений)

**Важно:** HuronCarController использует **системные привилегии**:
```xml
android:sharedUserId="android.uid.system"
```

Это даёт доступ к:
- `android.car.hardware.CarPropertyManager`
- `ICarProperty` AIDL сервису
- `MegaCarProperty` (внутренний класс)

**Для стороннего приложения:**
- ❌ Нет доступа к `CarPropertyManager`
- ❌ Нет доступа к `MegaCarProperty`
- ❌ Нет доступа к системным AIDL сервисам

**Код из HuronCarController (НЕ РАБОТАЕТ для нас):**
```java
// Высокоуровневый API (только для системных приложений!)
MegaCarProperty.setIntProp(0x660000c3, 1); // Открыть
MegaCarProperty.setIntProp(0x660000c3, 2); // Закрыть
```

### GB32960 Protocol (ЕДИНСТВЕННЫЙ ДОСТУПНЫЙ способ)

**Механизм:** Отправка пакетов через сокет
```java
// Низкоуровневый протокол (работает для всех)
512-байтный пакет:
- Header: 30 байт
- Function Bytes: BF 11 0D 06 27 21 20 01 (открыть)
- Byte 355: 0x2B
- Byte 363: 0x5A
- Byte 365: 0x00
// Отправка через Socket localhost:32960
```

**Преимущества:**
- ✅ Работает без системных привилегий
- ✅ Полный контроль над пакетом
- ✅ Прямая отправка в CAN-шину (через RMU)
- ✅ Независимо от других сервисов

**Недостатки:**
- ❌ Сложнее реализация
- ❌ Нужно самому отслеживать состояния
- ❌ Нет встроенных проверок безопасности

---

## 💡 Рекомендации для MazdaControl

### ✅ ЕДИНСТВЕННЫЙ ВАРИАНТ: GB32960 Protocol

**Реализация:**
```kotlin
class SpoilerController {
    private val packetGenerator = PacketGenerator()

    fun openSpoiler() {
        val packet = packetGenerator.generateSpoilerOpen()
        sendViaSocket(packet) // localhost:32960
    }
    
    fun closeSpoiler() {
        val packet = packetGenerator.generateSpoilerClose()
        sendViaSocket(packet) // localhost:32960
    }
}
```

**Преимущества:**
- ✅ Работает без системных привилегий
- ✅ Полный контроль над таймингами
- ✅ Логирование всех пакетов
- ✅ Mock-режим для отладки

**Что нужно проверить:**
- ⏳ Стабильность соединения с localhost:32960
- ⏳ Корректность пакетов (byte 355, 363, 365)
- ⏳ Время движения спойлера (~2.2 сек)
- ⏳ Обработку ошибок соединения

---

## 📁 Ключевые файлы

| Файл | Описание |
|------|----------|
| `CarbodyController.smali` | Управление кузовом (1216 строк) |
| `CustomInputEventController.smali` | Обработка событий ввода (3610 строк) |
| `Signal.smali` | Определение Property ID (8577 строк) |
| `EcuProperties.smali` | Конфигурация ECUs |
| `Ecu.smali` | ECU конфигурация (ID_CONFIG_ELECTRIC_REAR_SPOILER) |
| `R$string.smali` | Строковые ресурсы |

---

## 🔍 Найдено в HuronCarService (Signal.smali)

### Все Property ID для спойлера:

```java
// Команда на спойлер (Head Unit)
.field public static final BODYINFO_HU_SPOILERSWITCH:I = 0x660000c3
    .annotation runtime Lmega/car/annotation/PropertyDefine;
        publish = "BodyInfo/HU_SpoilerSwitch/Set"
        subscribe = "BodyInfo/HU_SpoilerSwitch"
        type = Ljava/lang/Integer;
    .end annotation
.end field

// Позиция спойлера (Power Tailgate System)
.field public static final BODYINFO_PTS_SPOILERPOSITION:I = 0x660000c4
    .annotation runtime Lmega/car/annotation/PropertyDefine;
        publish = "BodyInfo/PTS_SpoilerPosition/Set"
        subscribe = "BodyInfo/PTS_SpoilerPosition"
        type = Ljava/lang/Integer;
    .end annotation
.end field

// Движение спойлера (Power Tailgate System)
.field public static final BODYINFO_PTS_SPOILERMOVEMENT:I = 0x660000c5
    .annotation runtime Lmega/car/annotation/PropertyDefine;
        publish = "BodyInfo/PTS_SpoilerMovement/Set"
        subscribe = "BodyInfo/PTS_SpoilerMovement"
        type = Ljava/lang/Integer;
    .end annotation
.end field

// Приветственный режим (Light System)
.field public static final LIGHT_HU_SPOILERWELCOMESWITCH:I = 0x660000d1
    .annotation runtime Lmega/car/annotation/PropertyDefine;
        publish = "Light/HU_SpoilerWelcomeSwitch/Set"
        subscribe = "Light/HU_SpoilerWelcomeSwitch"
        type = Ljava/lang/Integer;
    .end annotation
.end field
```

### Конфигурация в Ecu.smali:

```java
// ID конфигурации электрического спойлера
.field public static final ID_CONFIG_ELECTRIC_REAR_SPOILER:I = 0x3310025a

// System property name
.field public static final CONFIG_ELECTRIC_REAR_SPOILER:Ljava/lang/String;
    = "ro.ecu.config.ELECTRIC_REAR_SPOILER"
```

**Архитектура:**
- **HU (Head Unit)** → отправляет команду (`0x660000c3`)
- **PTS (Power Tailgate System)** → возвращает позицию (`0x660000c4`) и движение (`0x660000c5`)
- **Light System** → приветственный режим (`0x660000d1`)

---

## 📝 Summary

**CarbodyController** управляет кузовом, но **НЕ управляет спойлером напрямую**.

**CustomInputEventController** обрабатывает нажатия кнопок на руле/интерфейсе и вызывает:
```java
MegaCarProperty.setIntProp(0x660000c3, 1); // Открыть
MegaCarProperty.setIntProp(0x660000c3, 2); // Закрыть
```

**Для вашей задачи:**
- Используйте те же Property ID: `0x660000c3`, `0x660000c4`, `0x660000c5`
- Логика: `1` = открыть, `2` = закрыть
- Отслеживайте: `position=101` (открыт), `movement=1` (движется), `movement=2` (завершено)
- Проверяйте: `ro.ecu.config.ELECTRIC_TAIL == 1` и `isPowerOn() == true`
