# Reverse Engineering Report: Mazda GB32960 Protocol

## Summary

Полный анализ протокола управления функциями автомобиля Mazda на основе телематики GB32960.

---

## 1. Архитектура системы

### 1.1. Основные компоненты

```
┌─────────────────────────────────────────────────────────────┐
│  HuronCarService (com.mega.car)                            │
│  └── AG35TspClient                                         │
│      ├── SocketChannel (172.16.2.2 → 172.16.2.30:49969)   │
│      ├── Processor Map                                     │
│      │   ├── TLVProcessor (ID: 0)                          │
│      │   ├── RemoteControlProcessor (ID: 1)                │
│      │   ├── GB32960Processor (ID: 2) ← НАШ ПРОТОКОЛ       │
│      │   ├── StatusProcessor (ID: 3)                       │
│      │   ├── OtaProcessor (ID: 4)                          │
│      │   └── LogProcessor (ID: 5)                          │
│      └── Selector (NIO)                                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  RMU Service (com.mega.rmu) - PID 8895                     │
│  ├── CallServerInterceptor                                 │
│  │   └── Отправляет команды на localhost:32960             │
│  ├── SelectorReader                                        │
│  │   └── Читает ответы от сервера                          │
│  ├── ResponseParserInterceptor                             │
│  │   └── Парсит ответы GB32960                             │
│  └── CanSignalHandler                                      │
│      └── Получает сигналы от CarPropertyService            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  GB32960 Server (порт 32960)                               │
│  └── Встроенный сервер телематики в автомобиле             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2. Сетевое взаимодействие

**HuronCarService → T-Box:**
- **Local IP:** 172.16.2.2 (локальный IP головного устройства)
- **Remote IP:** 172.16.2.30 (T-Box/AG35 модуль)
- **Port:** 0xC351 = 49969
- **Protocol:** TCP (NIO SocketChannel)

**CallServerInterceptor → RMU:**
- **Host:** localhost/127.0.0.1
- **Port:** 32960 (GB32960 protocol ID)
- **Process:** com.mega.rmu (PID 8895)
- **Protocol:** TCP Socket

---

## 2. Структура пакета GB32960

### 2.1. Формат пакета (512 байт)

```
┌─────────────────────────────────────────────────────────────┐
│  Header (30 байт)                                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Bytes 0-20:  VIN (Vehicle Identification Number)     │  │
│  │ Byte 21:     Encryption mark (0x01 = encrypted)      │  │
│  │ Bytes 22-23: Total length (big-endian)               │  │
│  │ Bytes 24-29: Timestamp (BCD format)                  │  │
│  └──────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  RealBody (482 байта)                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Bytes 30-481: Данные команды/ответа                 │  │
│  │   - Function Bytes (байты 38-45)                    │  │
│  │   - Command data                                    │  │
│  │   - Status bytes                                    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2. Детальная структура RealBody

```
Byte 30:  Command type (0x01 = request, 0x02 = response)
Byte 31:  Sequence number
Byte 32:  Sub-sequence
Bytes 33-34: Message ID
Byte 35:  Status code
Bytes 36-37: Reserved
Byte 38:  Function length
Bytes 38-45: Function Bytes (команда управления)
  - Пример OPEN:  BF 11 0D 06 27 21 20 01
  - Пример CLOSE: BF 11 0D 05 27 23 20 01
  
Byte 355: Control byte 1 (0x2B)
Byte 363: Control byte 2 (0x5A)
Byte 365: Control byte 3 (0x00)
```

### 2.3. Transmission Body (TLV-подобный формат)

Для внутренней передачи между AG35TspClient и процессорами используется другой формат:

```
┌─────────────────────────────────────────────────────────────┐
│  Transmission Header (14 байт)                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Byte 0:   Start marker (0x5A)                        │  │
│  │ Bytes 1-3: Reserved (0x00)                           │  │
│  │ Bytes 4-5: Sequence number (big-endian)              │  │
│  │ Bytes 6-7: Reserved (0x00)                           │  │
│  │ Bytes 8-9: Data length (big-endian)                  │  │
│  │ Bytes 10-11: CRC16 checksum                          │  │
│  └──────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  Payload (variable length)                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Протокол GB32960

### 3.1. Идентификаторы сообщений

| ID | Описание | Processor |
|----|----------|-----------|
| 0x30000002 | Remote Control Command | RemoteControlProcessor |
| 0x30000003 | GB32960 Data | GB32960Processor |
| 0x30000004 | GB32960 Connection Status | GB32960Processor |

### 3.2. Команды управления спойлером

#### OPEN (Открыть)
```
Function Bytes: BF 11 0D 06 27 21 20 01
  - BF 11 0D: Protocol header
  - 06: Command (OPEN)
  - 27 21 20 01: Parameters

Byte 355: 0x2B
Byte 363: 0x5A
Byte 365: 0x00

Время выполнения: ~2.2 секунды
```

#### CLOSE (Закрыть)
```
Function Bytes: BF 11 0D 05 27 23 20 01
  - BF 11 0D: Protocol header
  - 05: Command (CLOSE)
  - 27 23 20 01: Parameters

Byte 355: 0x2B
Byte 363: 0x5A
Byte 365: 0x00

Время выполнения: ~2.2 секунды
```

### 3.3. Другие команды (из логов)

| Function Bytes | Команда | Описание |
|---------------|---------|----------|
| BF 11 0D 03 27 24 20 01 | 0x03 | Неизвестно (возможно STOP) |
| BF 11 0D 04 27 23 20 01 | 0x04 | Неизвестно |
| BF 11 0D 05 27 23 20 01 | 0x05 | CLOSE |
| BF 11 0D 06 27 21 20 01 | 0x06 | OPEN |

---

## 4. Разрешения Android

### 4.1. Protected Permissions

HuronCarService использует `android.uid.sharedUserId`, что даёт доступ к защищённым разрешениям:

```xml
<permission android:name="mega.car.permission.CAR_WINDOWS" ... />
<permission android:name="mega.car.permission.CARCABIN" ... />
<permission android:name="mega.car.permission.CAR_DRIVING" ... />
<permission android:name="mega.car.permission.LIGHTING" ... />
<permission android:name="mega.car.permission.CAR_WARNING" ... />
<permission android:name="mega.car.permission.ENTRY_LOCK" ... />
<permission android:name="mega.car.permission.CLIMATE" ... />
<permission android:name="mega.car.permission.ELEC_POWER" ... />
<permission android:name="mega.car.permission.VEHICLE_BODY" ... />
<permission android:name="mega.car.permission.VEHICLE_MOTION" ... />
<permission android:name="mega.car.permission.COMFORTS_SETTINGS" ... />
<permission android:name="mega.car.permission.INFOTAINMENT" ... />
<permission android:name="mega.car.permission.NET" ... />
<permission android:name="mega.car.permission.VirtualBluetoothKey" ... />
<permission android:name="mega.car.permission.ADAS" ... />
<permission android:name="mega.car.permission.APA" ... />
<permission android:name="mega.car.permission.ECU" ... />
<permission android:name="mega.car.permission.Navigation" ... />
<permission android:name="mega.car.permission.EXTRA" ... />
<permission android:name="mega.car.permission.DMS" ... />
<permission android:name="mega.car.permission.KeyInput" ... />
<permission android:name="mega.car.permission.ACCOUNT" ... />
<permission android:name="mega.car.permission.SYSTEM_MONITOR" ... />
<permission android:name="mega.car.permission.SETTINGS" ... />
<permission android:name="mega.car.permission.INPUT_FORWARD_CENTER" ... />
<permission android:name="mega.car.permission.CAR_POWER" ... />
<permission android:name="mega.car.permission.LOCAL_EVENT" ... />
<permission android:name="mega.car.permission.FACTORY_TEST" ... />
<permission android:name="mega.car.permission.SEQUOIA" ... />
<permission android:name="mega.car.permission.VEHICLE_CONTROL" ... />
```

### 4.2. Стандартные разрешения

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS" />
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS_FULL" />
<uses-permission android:name="android.permission.DEVICE_POWER" />
```

---

## 5. Ключевые классы

### 5.1. AG35TspClient

**Путь:** `com/mega/car/tbox/AG35TspClient.smali`

**Основные методы:**
- `open()` - создание SocketChannel и подключение к T-Box
- `close()` - закрытие соединения
- `send2AG35([B)` - отправка данных
- `processRecvData([B)` - обработка полученных данных
- `makeTransmissionBody([B)` - создание TLV-пакета

**Поля:**
```java
private static final String HOST = "172.16.2.30";
private static final String LOCAL_HOST = "172.16.2.2";
private static final int PORT = 0xC351; // 49969
private SocketChannel mSocketChannel;
private Selector mSelector;
private SparseArray<DataProcessor> mProcessorMap;
```

### 5.2. GB32960Processor

**Путь:** `com/mega/car/tbox/processor/GB32960Processor.smali`

**Назначение:** Обработка входящих сообщений GB32960

**Методы:**
```java
public void processRecv([B data) {
    if (data[1] == 0x00) {
        // Передача данных CarPropertyService
        mCallBack.onRecvData(0x30000003, data);
    } else if (data[1] == 0x01) {
        if (data[2] == 0x01) {
            // Статус подключения
            mCallBack.onRecvData(0x30000004, true);
        }
    }
}

public void send(int id, Object data) {
    if (data instanceof byte[]) {
        mWriter.accept((byte[]) data);
    }
}
```

### 5.3. RemoteControlProcessor

**Путь:** `com/mega/car/tbox/processor/RemoteControlProcessor.smali`

**Назначение:** Обработка команд дистанционного управления

**Метод processRecv:**
```java
public void processRecv([B data) {
    // Проверка типа команды
    if ((data[0] & 0x0F) != 0x01) {
        Log.i("cmd type error!");
        return;
    }
    // Передача команды дальше
    mCallBack.onRecvData(0x30000002, data);
}
```

### 5.4. DataProcessor (базовый класс)

**Путь:** `com/mega/car/tbox/processor/DataProcessor.smali`

```java
public abstract class DataProcessor {
    protected DataProcessor.CallBack mCallBack;
    protected Consumer<byte[]> mWriter;
    
    public abstract void processRecv(byte[] data);
    public abstract void send(int id, Object data);
    public void setCallBack(CallBack callBack);
    public void setWriter(Consumer<byte[]> writer);
}
```

---

## 6. Логирование

### 6.1. Формат логов CallServerInterceptor

```
<timestamp> <pid> <tid> I CallServerInterceptor: 
    [ (CallServerInterceptor.java:<line>)#<method> ] 
    request/response <command_mark ip = <ip> port = <port> > 
    raw data >>>>>>>> / <<<<<<<<
    Header: 
    <0>: <byte>, <1>: <byte>, ...
    Encrpy-mark: 
    <21>: <byte>
    Length: 
    <22>: <byte>, <23>: <byte>
    Time: 
    <24>: <byte>, ...
    RealBody: 
    <30>: <byte>, ...
```

### 6.2. Формат логов SelectorReader

```
<timestamp> <pid> <tid> I SelectorReader: 
    [ (SelectorReader.java:<line>)#<method> ] 
    read data from server / command mark = <mark>
    Header: ...
    RealBody: ...
```

---

## 7. Выводы для реализации

### 7.1. Что подтверждено

1. ✅ **Порт 32960** - правильный порт для отправки команд
2. ✅ **Сервер com.mega.rmu** - процесс, который слушает порт
3. ✅ **512-байтные пакеты** - правильный размер пакета
4. ✅ **Function Bytes** - правильные команды для OPEN/CLOSE
5. ✅ **Control bytes** (355, 363, 365) - подтверждены в логах
6. ✅ **Время выполнения** - ~2.2 секунды на операцию

### 7.2. Что требует тестирования

1. ⚠️ **Реальное подключение** - только на реальном устройстве
2. ⚠️ **Полные 512 байт** - нужны все байты пакета (особенно VIN)
3. ⚠️ **CRC16 checksum** - алгоритм расчёта
4. ⚠️ **Шифрование** - если encryption mark = 0x01
5. ⚠️ **Таймауты** - обработка ошибок подключения

### 7.3. Рекомендации

1. **Использовать TEST MODE** для эмуляции без автомобиля
2. **Добавить логирование** всех отправляемых/получаемых пакетов
3. **Реализовать повторные попытки** подключения при ошибке
4. **Добавить индикатор подключения** для пользователя
5. **Сохранять логи** в файл для отладки

---

## 8. Ссылки на файлы

- **HuronCarService APK:** `/analysis/HuronCarService/`
- **Логи протокола:** `/analysis/logs/`
  - `sp_op2_cl_op_cl_op3.txt` - полные логи управления
  - `cl_off_on_off_on.txt` - логи команд
  - `psa.txt` - список процессов
- **Исходники:** `/app/src/main/java/com/mazda/control/`
  - `PacketGenerator.kt` - генерация пакетов
  - `SpoilerController.kt` - управление подключением
  - `MockSpoilerController.kt` - тестовый режим
  - `MainActivity.kt` - UI

---

**Дата составления:** 2026-03-13  
**Статус:** Reverse Engineering Complete  
**Следующий шаг:** Тестирование на реальном устройстве
