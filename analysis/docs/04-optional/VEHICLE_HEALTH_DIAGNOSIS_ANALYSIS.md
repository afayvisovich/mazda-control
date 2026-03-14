# 🏥 Vehicle Health & Diagnosis Protocol Analysis

**Дата:** 2026-03-14  
**Файлы:** 
- `smali_classes2/com/mega/controller/vehiclehealth/VehicleHealthController.smali`
- `smali_classes2/com/mega/controller/vehiclehealth/DiagnosisProtocol.smali`  
**Статус:** ✅ **АНАЛИЗ ЗАВЕРШЁН**

---

## 📋 Содержание

1. [Назначение](#назначение)
2. [Архитектура](#архитектура)
3. [DiagnosisProtocol](#diagnosisprotocol)
4. [VehicleHealthController](#vehiclehealthcontroller)
5. [DiagnosisCheck (7 типов)](#diagnosischeck-7-типов)
6. [TLV Protocol](#tlv-protocol)
7. [Выводы](#выводы)

---

## Назначение

**VehicleHealthController** — система мониторинга состояния автомобиля:

- **Диагностика систем:** TPMS, Airbag, EPS, ESC, Motor, ABS
- **Remote Diagnosis:** Отправка статусов в T-Box через TLV
- **Мониторинг двигателя:** Проверка состояния IGN/ACC
- **State Machine:** Обработка состояний для каждой системы

**DiagnosisProtocol** — протокол удалённой диагностики:

- Сбор статусов от всех систем
- Формирование TLV-пакетов
- Отправка в T-Box (через `0x30000001`)

---

## Архитектура

```
┌─────────────────────────────────────┐
│   VehicleHealthController           │
│   (extends CarSignalController)     │
├─────────────────────────────────────┤
│   mDiagnosisProtocol                │
│   mStateMachines[SparseIntArray]    │
│   mDiagnosisChecks[7]               │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│   DiagnosisProtocol                 │
│   (отдельный класс)                 │
├─────────────────────────────────────┤
│   mAllStatus (SparseIntArray)       │
│   mHandler (HandlerThread)          │
│   mSendRunnable                     │
└─────────────────────────────────────┘
           ↓
    TLV-протокол → T-Box
```

---

## DiagnosisProtocol

### Поля:

```java
mAllStatus: SparseIntArray          // Все статусы диагностики
mHandler: Handler                   // Обработчик (background поток)
mSendRunnable: Runnable             // Задача отправки
mSharedPreferencesUtil                // Сохранение в SharedPreferences
mMegaCarProperty: MegaCarProperty   // Доступ к свойствам
```

### Метод remoteDiagnosis(type, status):

```java
public void remoteDiagnosis(int type, int status) {
  // 1. Сохранить статус
  mAllStatus.append(type, status)
  
  // 2. Отложить отправку на 50ms
  mHandler.removeCallbacks(mSendRunnable)
  mHandler.postDelayed(mSendRunnable, 50)
}
```

**Дизайн:**
- **Debounce 50ms** — пакетная отправка всех изменений
- **SparseIntArray** — эффективное хранение (type → status)

---

### Метод send2Tsp():

```java
private void send2Tsp() {
  // 1. Создать TLV-контент из всех статусов
  List<TLVEntry> content = createContent()
  
  // 2. Сформировать TLVData
  TLVData data = new TLVData(
    ackFlag=1,
    statusCode=0,
    sid=5,
    mid=0x1F,        // 31 = диагностика
    requestId=0,
    content
  )
  
  // 3. Отправить через MegaCarProperty
  CarPropertyValue value = new CarPropertyValue(0x30000001, data)
  mMegaCarProperty.setRawProp(value)
}
```

---

### Метод createContent():

```java
private List<TLVEntry> createContent() {
  List<TLVEntry> entries = new ArrayList<>()
  
  for (i = 0; i < mAllStatus.size(); i++) {
    int type = mAllStatus.keyAt(i)      // Diagnosis Type
    int status = mAllStatus.valueAt(i)  // Diagnosis Status
    
    // Создать TLVEntry: type → 1-byte status
    byte[] value = new byte[]{ (byte)status }
    TLVEntry entry = new TLVEntry(type, value)
    
    entries.add(entry)
    
    // Логирование
    Log.d("add type=" + type + ",status=" + status)
  }
  
  return entries
}
```

**Формат:**
- **Type:** Diagnosis Type ID (int)
- **Value:** 1 байт (статус системы)

---

### SharedPreferences:

```java
// Сохранение статуса
setStorageStatus(type, status):
  SharedPreferences.putInt("diagnosis" + type, status)

// Чтение статуса
getStorageStatus(type):
  return SharedPreferences.getInt("diagnosis" + type, -1)
```

**Назначение:** Сохранение последнего статуса для каждой системы

---

## VehicleHealthController

### Наследование:
```java
extends CarSignalController
```

### Поля:
```java
mDiagnosisProtocol: DiagnosisProtocol          // Протокол
mStateMachines: SparseArray<DiagnosisStateMachine>  // State machines
mDiagnosisChecks: DiagnosisCheck[7]            // 7 проверок
mEngineRunning: boolean                        // Двигатель запущен
mDiagnosisRunning: boolean                     // Диагностика активна
```

### Проверка двигателя checkRunning():

```java
private void checkRunning() {
  // Проверка IGN
  int ign = getIntProp(0x6600000B)  // Ignition status
  boolean engineOn = (ign == 2)     // 2 = ON
  
  // Или проверка ACC
  int acc = getIntProp(0x6600013C)  // ACC status
  if (acc == 2) engineOn = true
  
  mEngineRunning = engineOn
}
```

**Property ID:**
- `0x6600000B` — Ignition status
- `0x6600013C` — ACC status

---

### Запуск диагностики:

```java
if (mEngineRunning && !mDiagnosisRunning) {
  mDiagnosisRunning = true
  mHandler.postDelayed(mRunningRunnable, RUNNING_DELAY)
}
```

**RUNNING_DELAY:** `0xEA60` = 60000ms = 1 минута

**Логика:**
- Диагностика запускается через 1 минуту после старта двигателя
- Периодическая проверка всех систем

---

### 7 DiagnosisCheck:

```java
mDiagnosisChecks = new DiagnosisCheck[] {
  new TpmsCheck(),      // Давление в шинах
  new AirbagCheck(),    // Подушки безопасности
  new EpsCheck(),       // Электроусилитель руля
  new EscCheck(),       // Система курсовой устойчивости
  new MotorCheck(),     // Двигатель
  new AbsCheck(),       // ABS
  // ...
}
```

---

## DiagnosisCheck (7 типов)

### Базовый класс:

```java
abstract class DiagnosisCheck {
  protected int mType              // Тип диагностики
  protected ToIntFunction<Integer> mGetIntSignal  // Чтение свойства
  
  abstract boolean check()         // Проверка системы
  abstract Set<Integer> getSignals()  // Property IDs
  int getType()                    // Получить тип
}
```

### Реализации:

| Класс | Система | Property ID |
|-------|---------|-------------|
| **TpmsCheck** | Давление в шинах | `0x66000xxx` |
| **AirbagCheck** | Подушки безопасности | `0x66000xxx` |
| **EpsCheck** | Электроусилитель руля | `0x66000xxx` |
| **EscCheck** | Система курсовой устойчивости | `0x66000xxx` |
| **MotorCheck** | Двигатель | `0x66000xxx` |
| **AbsCheck** | ABS | `0x66000xxx` |

**Примечание:** Конкретные Property ID не найдены в декомпилированном коде

---

### Метод check():

```java
boolean check() {
  // 1. Прочитать свойство
  int status = mGetIntSignal.applyAsInt(signalId)
  
  // 2. Проверить статус
  if (status != NORMAL) {
    // 3. Отправить в DiagnosisProtocol
    mDiagnosisProtocol.remoteDiagnosis(mType, status)
    return false
  }
  
  return true
}
```

---

## TLV Protocol

### Формат TLVData для диагностики:

```java
TLVData(
  ackFlag=1,      // Требуется подтверждение
  statusCode=0,   // Без ошибок
  sid=5,          // Service ID = 5
  mid=0x1F,       // Message ID = 31 (диагностика)
  requestId=0,
  content=[       // Список TLVEntry
    TLVEntry(type=0xXXXX, value=[status]),
    TLVEntry(type=0xYYYY, value=[status]),
    ...
  ]
)
```

### Отправка:

```java
CarPropertyValue(0x30000001, tlvData)
MegaCarProperty.setRawProp(value)
```

**Message ID:** `0x30000001` — транспорт для TLV-данных в T-Box

---

### Сравнение с RmuMsgController:

| Характеристика | RmuMsgController | DiagnosisProtocol |
|----------------|------------------|-------------------|
| **Данные** | SOC, PUC, Notice | Диагностика (7 систем) |
| **TLV Types** | 0x299B, 0x4E30, 0x278B | Diagnosis Type IDs |
| **Message ID** | 0x22 (34) | 0x1F (31) |
| **Service ID** | 5 | 5 |
| **ackFlag** | 1 | 1 |
| **Формат** | 3 TLVEntry | N TLVEntry (по количеству систем) |

---

## State Machine

### DiagnosisStateMachine:

```java
class DiagnosisStateMachine {
  // Обработка состояний для каждой системы
  // Переходы между состояниями
  // Отправка уведомлений
}
```

**Назначение:**
- Управление состояниями системы (OK, WARNING, ERROR)
- Фильтрация ложных срабатываний
- Отправка только значимых изменений

---

## Выводы

### Роль в архитектуре:

**VehicleHealthController** — система мониторинга здоровья автомобиля:

1. ✅ **7 диагностических проверок** — TPMS, Airbag, EPS, ESC, Motor, ABS
2. ✅ **Remote Diagnosis** — отправка статусов в T-Box
3. ✅ **State Machine** — управление состояниями
4. ✅ **Задержка 1 мин** — запуск после старта двигателя
5. ✅ **Debounce 50ms** — пакетная отправка изменений

### DiagnosisProtocol:

**Протокол удалённой диагностики:**

- **SparseIntArray** — хранение всех статусов
- **createContent()** — формирование TLV-пакета
- **send2Tsp()** — отправка через `0x30000001`
- **SharedPreferences** — сохранение последнего статуса

### TLV-интеграция:

```
VehicleHealthController
  ↓
DiagnosisProtocol.remoteDiagnosis(type, status)
  ↓
DiagnosisProtocol.send2Tsp()
  ↓
TLVData(mid=0x1F, content=[TLVEntry...])
  ↓
CarPropertyValue(0x30000001, tlvData)
  ↓
MegaCarProperty.setRawProp()
  ↓
T-Box (172.16.2.30:49960)
```

### Связь со спойлером:

**Прямой связи нет**, но:
- Оба используют **MegaCarProperty.setRawProp(0x30000001)**
- Оба используют **TLV-протокол** для обмена с T-Box
- Общий механизм доставки данных

---

**📍 Следующий шаг:** Изучить SentryController для понимания полной картины TLV-коммуникации.
