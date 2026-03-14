# 🔋 RmuMsgController Analysis

**Дата:** 2026-03-14  
**Файл:** `smali_classes2/com/mega/controller/notify/RmuMsgController.smali`  
**Статус:** ✅ **АНАЛИЗ ЗАВЕРШЁН**

---

## 📋 Содержание

1. [Назначение](#назначение)
2. [Архитектура](#архитектура)
3. [Property ID](#property-id)
4. [Алгоритм работы](#алгоритм-работы)
5. [TLV Protocol](#tlv-protocol)
6. [Кодирование данных](#кодирование-данных)
7. [Выводы](#выводы)

---

## Назначение

**RmuMsgController** — контроллер уведомлений о статусе зарядки (RMU = Remote Management Unit).

**Основная функция:**
- Мониторинг статуса зарядки (`0x66000108`)
- Отправка уведомлений в T-Box через TLV-протокол
- Передача данных: SOC (State of Charge) + PUC (Power Usage Control)

---

## Архитектура

### Наследование:
```
RmuMsgController → CarSignalController → Base Controller
```

### Подписки:
```java
initSignalSet():
  HashSet.add(0x66000108)  // ENERGYINFO_CHARGESTATUS
```

### Обработка событий:
```java
handleSignalChange(id, status):
  if (id == 0x66000108):
    sendMessageToApp(status)
```

---

## Property ID

| Property ID | Название | Тип | Описание |
|-------------|----------|-----|----------|
| `0x66000108` | ENERGYINFO_CHARGESTATUS | Integer | Статус зарядки (триггер) |
| `0x66000133` | (неизвестно) | Integer | SOC (State of Charge) |
| `0x66000137` | (неизвестно) | Integer | PUC (Power Usage Control) |

### Статусы зарядки:
```java
5 → 0x48 (72)   // Зарядка 5?
6 → 0x49 (73)   // Зарядка 6?
7 → 0x4A (74)   // Зарядка 7?
```

---

## Алгоритм работы

### 1. Получение статуса зарядки:
```
handleSignalChange(0x66000108, status)
  ↓
sendMessageToApp(status)
```

### 2. Проверка статуса:
```java
if (status == 5) notice = 0x48
else if (status == 6) notice = 0x49
else if (status == 7) notice = 0x4A
else notice = 0  // Игнорировать
```

### 3. Чтение данных:
```java
socProp = MegaCarProperty.getPropertyRaw(0x66000133)
pucProp = MegaCarProperty.getPropertyRaw(0x66000137)

if (socProp == null || pucProp == null) return

socValue = socProp.getValue()  // Integer
pucValue = pucProp.getValue()  // Integer
```

### 4. Формирование TLV-пакета:
```java
List<TLVEntry> content = new ArrayList<>()

// TLV 1: SOC (type=0x299B, value=socValue)
content.add(new TLVEntry(0x299B, convertIntToByteArray(socValue, 1)))

// TLV 2: PUC (type=0x4E30, value=pucValue)
content.add(new TLVEntry(0x4E30, convertIntToByteArray(pucValue, 4)))

// TLV 3: Notice (type=0x278B, value=notice)
content.add(new TLVEntry(0x278B, convertIntToByteArray(notice, 1)))
```

### 5. Отправка в T-Box:
```java
TLVData data = new TLVData(
  ackFlag=1,
  statusCode=0,
  sid=5,
  mid=0x22,
  requestId=0,
  content
)

CarPropertyValue value = new CarPropertyValue(0x30000001, data)
MegaCarProperty.setRawProp(value)
```

---

## TLV Protocol

### Структура TLVEntry:
```java
class TLVEntry {
  int type;      // TLV Type ID
  byte[] value;  // Значение (байтовый массив)
}
```

### Структура TLVData:
```java
class TLVData {
  int ackFlag;        // Флаг подтверждения
  int statusCode;     // Код статуса
  int sid;            // Service ID
  int mid;            // Message ID
  long requestId;     // ID запроса
  List<TLVEntry> content;  // Данные
}
```

### TLV Type IDs:
| Type ID | Название | Размер | Описание |
|---------|----------|--------|----------|
| `0x299B` | SOC | 1 байт | State of Charge (уровень заряда) |
| `0x4E30` | PUC | 4 байта | Power Usage Control |
| `0x278B` | Notice | 1 байт | Уведомление о статусе (0x48/0x49/0x4A) |

---

## Кодирование данных

### Метод convertIntToByteArray():
```java
// Преобразует int в byte[] заданной длины
// Примеры:
convertIntToByteArray(75, 1) → [0x4B]
convertIntToByteArray(10000, 4) → [0x00, 0x00, 0x27, 0x10]
```

### Алгоритм:
1. Выделить ByteBuffer.allocate(len)
2. Записать байты от старшего к младшему
3. Обрезать до целевой длины через adjustBytesToTargetLen()

### Метод adjustBytesToTargetLen():
```java
// Если исходный массив больше target → обрезать спереди
// Если меньше → дополнить нулями спереди

// Примеры:
[0x00, 0x00, 0x27, 0x10], len=2 → [0x27, 0x10]
[0x4B], len=4 → [0x00, 0x00, 0x00, 0x4B]
```

---

## Message ID 0x30000001

### Назначение:
**`0x30000001`** — специальный Property ID для отправки TLV-данных в T-Box.

### Использование:
```java
// RmuMsgController отправляет статус зарядки
MegaCarProperty.setRawProp(
  new CarPropertyValue(0x30000001, tlvData)
)

// Аналогично в других контроллерах:
// - SentryStatusSyncManager
// - MegaControllerService
// - ChildrenForgetController
// - RemoteLiveController
// - DiagnosisProtocol
// - TspController
// - VitalSignsDetectController
// - InsulationController
// - XCallController
```

---

## Выводы

### Назначение RmuMsgController:
- **Мониторинг зарядки:** Подписка на `0x66000108`
- **Сбор данных:** SOC (`0x66000133`) + PUC (`0x66000137`)
- **Уведомление T-Box:** Отправка через TLV-протокол (`0x30000001`)

### Ключевые находки:
1. ✅ **TLV-протокол** для обмена с T-Box
2. ✅ **3 TLV-параметра:** SOC, PUC, Notice
3. ✅ **Message ID 0x30000001** — транспорт для TLV-данных
4. ✅ **9+ контроллеров** используют тот же механизм

### Связь с другими контроллерами:
```
RmuMsgController (зарядка)
SentryController (охрана)
ChildrenForgetController (забытые дети)
RemoteLiveController (удалённый доступ)
DiagnosisProtocol (диагностика)
TspController (телематика)
VitalSignsDetectController (жизненные показатели)
InsulationController (изоляция)
XCallController (экстренный вызов)
     ↓
MegaCarProperty.setRawProp(0x30000001, tlvData)
     ↓
T-Box (172.16.2.30:49960)
```

### Отличие от GB32960:
| Характеристика | GB32960 | TLV (RmuMsgController) |
|----------------|---------|------------------------|
| Формат | 512 байт | Переменная длина |
| Транспорт | localhost:32960 | 0x30000001 → T-Box |
| Назначение | Управление (команды) | Телеметрия (данные) |
| Структура | Заголовок + RealBody | TLV (Type-Length-Value) |

---

**📍 Следующий шаг:** Изучить SentryController для понимания полного механизма TLV-коммуникации.
