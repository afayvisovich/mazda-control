# Spoiler Protocol Analysis: Final Findings

## Ключевое открытие: Протокол НЕ TBox!

В ходе глубокого анализа выяснилось, что **HuronCarSettings НЕ использует TBox протокол** (444-байтные пакеты) для управления спойлером!

### Реальная архитектура:

```
HuronCarSettings
  ↓ write(1/2/3/4)
VehSettings (Property ID: 0x38)
  ↓
CarPropertyLiveData.setCarProperty()
  ↓
MegaCarProperty.setRawProp()
  ↓
ICarProperty.setProperty() [Binder IPC]
  ↓
┌────────────────────────────────────────────┐
│ CarPropertyService (HuronCarService)       │
│   ↓ mPropertyDelegate                      │
│ MessageProperty                            │
│   ↓ Converter.serialize()                  │
│   JSON: {"value":1,"valid":true,...}       │
│   ↓ MessageClient.setValue()               │
│   IMessageCenter.sendMsg() [HIDL]          │
└────────────────────────────────────────────┘
  ↓
vendor/mega/message_center/V1_0
  ↓
??? → ECU спойлера
```

### Что это означает:

1. **MessageProperty** использует **Gson JSON сериализацию**, а не бинарные TBox пакеты
2. **Payload** класс содержит:
   ```java
   {
     "value": 1,           // Команда: 1=OPEN, 2=CLOSE, 3=FOLLOW, 4=SPORT
     "valid": true,
     "relative": false,
     "time": 1234567890,
     "extension": null
   }
   ```
3. **IMessageCenter** отправляет JSON строку через HIDL интерфейс

### Где TBox пакеты?

**TBox протокол (444 байта)** используется в **другом месте**:
- Возможно, для **телеметрии** (UploadService в huronRMU)
- Возможно, для **CAN сигналов** (CanSignalHandler)
- **НЕ для управления свойствами автомобиля**

### Ваш TBoxSpoilerController:

**Проблема:** Ваш контроллер отправляет **статические 444-байтные пакеты**, но HuronCarSettings использует **JSON через HIDL**.

**Решение 1 (правильное):** Эмулировать Binder IPC вызов:
```kotlin
// Получить сервис
val binder = ServiceManager.getService("com.mega.car.CarService")
val carProperty = ICarProperty.Stub.asInterface(binder)

// Создать CarPropertyValue
val propertyValue = CarPropertyValue(0x38, 1) // Property ID 0x38, значение 1=OPEN

// Отправить команду
carProperty.setProperty(propertyValue)
```

**Решение 2 (костыль):** Найти, где JSON конвертируется в TBox пакеты.
- Возможно, в **IMessageCenter** реализации
- Возможно, в **EcuProperty** или другом обработчике

### Что делать дальше:

#### Вариант A: Использовать Binder IPC (рекомендуется)

1. Добавить в проект AIDL интерфейсы:
   - `ICarProperty.aidl`
   - `CarPropertyValue.aidl`

2. Получить доступ к сервису:
   ```kotlin
   val binder = ServiceManager.getService("com.mega.car.CarService")
   ```

3. Вызвать setProperty() напрямую

**Преимущества:**
- ✅ Точная эмуляция оригинального приложения
- ✅ Не нужно разбираться с TBox пакетами
- ✅ Работает с любыми свойствами автомобиля

**Недостатки:**
- ❌ Требует root доступа для ServiceManager
- ❌ Нужны AIDL интерфейсы

#### Вариант B: Исследовать IMessageCenter реализацию

Найти, где `IMessageCenter.sendMsg()` реализует отправку:
```bash
grep -r "IMessageCenter\$Stub" analysis/HuronCarService/smali/
grep -r "sendMsg" analysis/HuronCarService/smali/vendor/mega/message_center/
```

Возможно, там JSON конвертируется в бинарный формат.

#### Вариант C: Продолжить с TBox пакетами

Если Fake32960Server принимает TBox пакеты, значит где-то есть конвертер JSON → TBox.

**Где искать:**
- `analysis/huronRMU/smali/com/mega/rmu/tbox/`
- `analysis/huronRMU/smali/com/mega/rmu/tlv/`
- `analysis/HuronCarService/smali/com/mega/car/hal/ecu/`

### Выводы:

**HuronCarSettings использует JSON через HIDL, а не TBox пакеты!**

Ваш **TBoxSpoilerController** может работать, только если:
1. Fake32960Server имеет конвертер JSON → TBox
2. Или вы переключитесь на Binder IPC

### Рекомендуемый следующий шаг:

Проверить, принимает ли **Fake32960Server** JSON или только TBox пакеты:

```bash
# Посмотреть логи Fake32960Server
adb logcat | grep Fake32960Server

# Отправить JSON через TBoxSpoilerController
{"value":1,"valid":true}

# Если не работает → нужен Binder IPC
```

### Файлы для изучения:

- `analysis/HuronCarService/smali/com/mega/car/hal/message/MessageProperty.smali`
- `analysis/HuronCarService/smali/com/mega/car/hal/message/MessageProperty$1.smali`
- `analysis/HuronCarService/smali/com/mega/car/utils/CarPropertyUtil.smali`
- `analysis/HuronCarService/smali/com/mega/car/data/Payload.smali`
- `analysis/HuronCarService/smali/vendor/mega/message_center/V1_0/IMessageCenter.smali`
