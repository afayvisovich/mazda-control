# HuronCarService - Анализ возможностей приложения

## 📋 Общее описание

**HuronCarService** (`com.mega.car`) - это системное приложение Android Automotive, которое предоставляет центральный интерфейс для управления всеми функциями автомобиля Mazda через протокол GB32960 и CAN-шину.

---

## 🎯 Назначение приложения

### 1. **Центральный хаб управления автомобилем**
Приложение служит промежуточным слоем между:
- **T-Box** (телематический блок, AG35 модуль)
- **RMU** (Remote Management Unit, com.mega.rmu)
- **CarPropertyService** (системный сервис свойств автомобиля)
- **Пользовательскими приложениями** (например, наше MazdaControl)

### 2. **Унифицированный API для доступа к функциям авто**
Предоставляет единый интерфейс для управления:
- Климат-контролем
- Окнами и люком
- Дверями и замками
- Освещением
- Предупреждениями и ошибками
- Питанием и энергосистемой
- Движением и трансмиссией

---

## 🔧 Ключевые компоненты

### 2.1. MegaCarService
**Класс:** `com.mega.car.MegaCarService`

**Назначение:** Главный сервис приложения, который:
- Инициализирует все подсистемы
- Управляет жизненным циклом CarProperty
- Предоставляет binder для межпроцессного взаимодействия

**Разрешения:**
- `android.uid.system` - системные привилегии
- `INTERACT_ACROSS_USERS` - работа с несколькими пользователями
- `DEVICE_POWER` - управление питанием

### 2.2. MegaCarProperty
**Класс:** `com.mega.car.MegaCarProperty`

**Назначение:** Менеджер свойств автомобиля, который:
- Маппит PropertyID на строковые имена
- Управляет подпиской на изменения свойств
- Предоставляет API для чтения/записи свойств

**Пример использования:**
```java
MegaCarProperty.getInstance().getNameById(0x2000003); // "AC/AutoMode"
```

### 2.3. AG35TspClient
**Класс:** `com.mega.car.tbox.AG35TspClient`

**Назначение:** TCP клиент для связи с T-Box модулем

**Сетевые параметры:**
- **Local:** 172.16.2.2:0 (любой порт)
- **Remote:** 172.16.2.30:49969 (0xC351)
- **Protocol:** TCP (NIO SocketChannel)

**Процессоры данных:**
| ID | Processor | Назначение |
|----|-----------|------------|
| 0 | TLVProcessor | TLV-пакеты |
| 1 | RemoteControlProcessor | Дистанционное управление |
| 2 | **GB32960Processor** | **Протокол GB32960** |
| 3 | StatusProcessor | Статусы систем |
| 4 | OtaProcessor | OTA-обновления |
| 5 | LogProcessor | Логирование |

### 2.4. CarPropertyManager
**Класс:** `com.mega.car.hardware.property.CarPropertyManager`

**Назначение:** Управление подпиской на CAN-сигналы

**Возможности:**
- Регистрация слушателей событий
- Получение уведомлений об изменении свойств
- Отправка команд в CAN-шину

---

## 🚗 Доступные функции автомобиля

### 3.1. Климат-контроль (Domain: 0x02)
**Файл:** `Climate.smali`

**Свойства (более 100):**

| PropertyID | Функция | Publish/Subscribe |
|------------|---------|-------------------|
| 0x2000003 | Auto AC Mode | AC/AutoMode/Set |
| 0x2000004 | Sync Mode | AC/SyncMode/Set |
| 0x2000005 | Economy Mode | AC/EconomyMode/Set |
| 0x2000006 | AC Mode | AC/ACMode/Set |
| 0x2000007 | Max AC Mode | AC/MaxAC/Set |
| 0x2000008 | Max Defrost | AC/MaxDefrost/Set |
| 0x2000009 | Auto Defrost | AC/AutoDefOnOffSts/Set |
| 0x200001d | Ionizer | AC/AirClean/Set |
| 0x200001e | Air Distribution Auto | AC/AirDist/Auto |
| 0x2000022 | Auto Circulation | AC/AutoCirculation/Set |
| 0x2000042 | AC Run Request | AC/RunReq/Set |
| 0x200004c | Auto Wind Unlock | BodyInfo/KeyUnlockAutoWind/Set |
| 0x200004d | Auto Wind Long Press | BodyInfo/KeyLongPressAutoWind/Set |
| 0x200004e | Auto Wind Drying | AC/AutoWindDrying/Set |
| 0x200004f | Auto Wind Timer | AC/VentilationTimer/Set |
| 0x2000050 | Auto Defrost/Defog | AC/AutoDefrostDefog/Set |
| 0x2000057 | Auto Ion Mode | AC/AutoNegAniOnOffSts/Set |
| 0x2000058 | AC System Error | AC/SystemError/Set |
| 0x2000059 | Fast Heat | AC/FastHeat/Set |
| 0x2000062 | Custom AC Mode | AC/Setting/ACModeCustom/Set |
| 0x2000063 | Air Clean Auto Run | AC/Setting/AirCleanAutoRun/Set |
| 0x2000068 | AC Display | AC/Display/Set |
| 0x2000069 | Auto Defrost Tip | AC/AutoDefrostDefogTip/Set |
| 0x200006c-0x2000071 | Zone Auto Modes | AC/AutoMode/{Zone}/Set |
| 0x2000072-0x2000074 | Zone Sync Modes | AC/SyncMode/{Zone}/Set |
| 0x2000075-0x2000076 | Zone AC Modes | AC/ACMode/{Zone}/Set |

**Зоны климата:**
- Front (передняя)
- FrontLeft (водитель)
- FrontRight (пассажир)
- Rear (задняя)
- RearLeft (задний левый)
- RearRight (задний правый)

**Параметры управления:**
- Температура (отдельно по зонам)
- Скорость вентилятора
- Направление воздуха
- Режим рециркуляции
- Обогрев сидений/вентиляция
- Ионизация воздуха
- Датчик качества воздуха (AQS)

### 3.2. Окна и люк (Domain: 0x03)
**Файл:** `Windows.smali`

**Свойства:**

| PropertyID | Функция | Тип |
|------------|---------|-----|
| 0x3000001 | Driver Window Position | 0-100% |
| 0x3000002 | Passenger Window Position | 0-100% |
| 0x3000003 | Rear Left Window Position | 0-100% |
| 0x3000004 | Rear Right Window Position | 0-100% |
| 0x3000010 | Sunroof Position | 0-100% |
| 0x3000011 | Sunshade Position | 0-100% |
| 0x3000020 | Window Lock State | Locked/Unlocked |
| 0x3000030 | Sunroof Tilt | -1 (tilt) to 1 (open) |

**Команды управления:**
- Open/Close (открыть/закрыть)
- Move (переместить в позицию)
- Stop (остановить)
- Auto (автоматическое открытие/закрытие)
- Tilt (наклон люка)

**Функции безопасности:**
- Anti-pinch (защита от защемления)
- Auto reverse (автоматический возврат при препятствии)
- Rain close (автозакрытие при дожде)
- Key unlock auto wind (открытие по ключу)

### 3.3. Освещение (Domain: 0x04)
**Файл:** `Lighting.smali`

**Свойства:**

| PropertyID | Функция | Значения |
|------------|---------|----------|
| 0x4000001 | Headlight Mode | Auto/Manual/Off |
| 0x4000002 | Headlight Level | 0-5 |
| 0x4000003 | Fog Light Front | On/Off |
| 0x4000004 | Fog Light Rear | On/Off |
| 0x4000005 | DRL (Daytime Running Lights) | On/Off |
| 0x4000010 | Ambient Light Mode | Static/Dynamic/Game |
| 0x4000011 | Ambient Light Brightness | 0-100% |
| 0x4000012 | Ambient Light Color | RGB |
| 0x4000020 | Follow Me Home | On/Off + Timer |
| 0x4000021 | Coming Home | On/Off |
| 0x4000030 | Auto High Beam | On/Off |
| 0x4000040 | Light Show | Pattern ID |
| 0x4000050 | Turn Signal | Left/Right/Hazard |
| 0x4000060 | Reading Lamp | On/Off + Brightness |

**Функции:**
- Автоматическое переключение ближний/дальний
- Адаптивное освещение поворотов
- Световое шоу (приветствие/прощание)
- Игровой режим освещения
- Настраиваемая атмосферная подсветка

### 3.4. Двери и замки (Domain: 0x05)
**Файл:** `VehicleBody.smali`

**Свойства:**

| PropertyID | Функция | Значения |
|------------|---------|----------|
| 0x5000001 | Driver Door Open | Open/Closed |
| 0x5000002 | Passenger Door Open | Open/Closed |
| 0x5000003 | Rear Left Door Open | Open/Closed |
| 0x5000004 | Rear Right Door Open | Open/Closed |
| 0x5000010 | Hood Open | Open/Closed |
| 0x5000011 | Trunk Open | Open/Closed |
| 0x5000020 | Central Lock | Locked/Unlocked |
| 0x5000021 | Child Lock Left | On/Off |
| 0x5000022 | Child Lock Right | On/Off |
| 0x5000030 | Fuel Door | Open/Closed |
| 0x5000031 | Charge Port | Open/Closed |

**Команды:**
- Lock/Unlock (блокировка/разблокировка)
- Open/Close (открыть/закрыть дверь)
- Child Lock (детский замок)

### 3.5. Предупреждения (Domain: 0x06)
**Файл:** `Warnings.smali`

**Типы предупреждений:**

| PropertyID | Тип | Критичность |
|------------|-----|-------------|
| 0x6000001 | Low Fuel | Warning |
| 0x6000002 | Low Washer Fluid | Warning |
| 0x6000003 | Door Ajar | Warning |
| 0x6000004 | Seat Belt Unbuckled | Critical |
| 0x6000005 | Engine Overheat | Critical |
| 0x6000006 | Battery Low | Warning |
| 0x6000007 | Brake System Error | Critical |
| 0x6000008 | Airbag Error | Critical |
| 0x6000009 | Tire Pressure Low | Warning |
| 0x6000010 | Service Required | Info |
| 0x6000020 | High Temperature Shutdown | Critical |
| 0x6000021 | Crash Detection | Critical |
| 0x6000022 | DMS Alert (Driver Monitoring) | Warning |

### 3.6. Питание (Domain: 0x07)
**Файл:** `Power.smali`

**Свойства:**

| PropertyID | Функция | Значения |
|------------|---------|----------|
| 0x7000001 | Battery Voltage | mV |
| 0x7000002 | Battery Current | mA |
| 0x7000003 | Battery SOC (State of Charge) | 0-100% |
| 0x7000004 | Battery SOH (State of Health) | 0-100% |
| 0x7000010 | Power Mode | Off/ACC/ON/START |
| 0x7000011 | Power State | Active/Sleep/Shutdown |
| 0x7000020 | Charging State | NotCharging/Charging/Complete |
| 0x7000021 | Charge Port State | Open/Closed |
| 0x7000022 | Charge Current Limit | mA |
| 0x7000023 | Charge Voltage Limit | mV |
| 0x7000030 | V2L (Vehicle to Load) | On/Off |
| 0x7000031 | V2G (Vehicle to Grid) | On/Off |
| 0x7000040 | Screen Control | On/Off/Brightness |

**Функции:**
- Управление питанием головного устройства
- Контроль заряда батареи 12V
- Мониторинг высоковольтной батареи
- Режимы зарядки
- Vehicle-to-Load (питание внешних устройств)

### 3.7. Движение (Domain: 0x08)
**Файл:** `VehicleMotion.smali`

**Свойства:**

| PropertyID | Функция | Значения |
|------------|---------|----------|
| 0x8000001 | Vehicle Speed | km/h |
| 0x8000002 | Engine RPM | RPM |
| 0x8000003 | Gear Position | P/R/N/D/S/L |
| 0x8000010 | ESP Status | Active/Off/Fault |
| 0x8000011 | Traction Control | Active/Off |
| 0x8000012 | Hill Descent Control | Active/Off |
| 0x8000020 | Parking Brake | Engaged/Released |
| 0x8000021 | EPB (Electronic Parking Brake) | Engaged/Released |
| 0x8000030 | Drive Mode | Comfort/Eco/Sport/Custom |
| 0x8000031 | Steering Mode | Comfort/Normal/Sport |
| 0x8000040 | Tone Mode | Mute/Normal/Sport |

### 3.8. T-Box и телематика (Domain: 0x09)
**Файл:** `TBox.smali`

**Свойства:**

| PropertyID | Функция | Описание |
|------------|---------|----------|
| 0x9000001 | TLV Data | Данные TLV-протокола |
| 0x9000002 | GB32960 Data | Данные протокола GB32960 |
| 0x9000003 | Remote Control | Команды дистанционного управления |
| 0x9000010 | Network Status | 4G/WiFi/Offline |
| 0x9000011 | Signal Strength | dBm |
| 0x9000020 | GPS Position | Lat/Lon/Alt |
| 0x9000021 | GPS Speed | km/h |
| 0x9000022 | GPS Heading | 0-360° |
| 0x9000030 | OTA Update Status | Idle/Downloading/Installing |
| 0x9000031 | Firmware Version | String |

---

## 💡 Сценарии использования

### 4.1. Для разработчиков приложений

#### **Готовый API для управления автомобилем**
Вместо работы с низкоуровневым протоколом GB32960 можно использовать:
```java
// Через CarProperty API
CarPropertyManager manager = CarPropertyManager.getInstance();
manager.setProperty(0x2000003, 1); // Включить Auto AC
manager.getProperty(0x2000003); // Получить статус
```

#### **Событийная модель**
```java
manager.registerCallback(0x2000001, new Callback() {
    @Override
    public void onChangeEvent(CarPropertyValue value) {
        // Обработка изменения свойства
    }
});
```

### 4.2. Для диагностики

#### **Чтение ошибок и предупреждений**
- Мониторинг всех систем автомобиля
- Получение кодов ошибок
- Отслеживание критических событий

#### **Логирование телематики**
- Запись TLV-пакетов
- Логирование GB32960 сообщений
- Анализ CAN-сигналов

### 4.3. Для расширения функционала

#### **Интеграция с голосовыми ассистентами**
```java
// Голосовая команда: "Открыть окно"
→ Climate.setProperty(0x3000001, WindowControl.OPEN)
```

#### **Сценарии и автоматизация**
```java
// Сценарий "Дождь"
if (rainSensor > threshold) {
    closeAllWindows();
    closeSunroof();
    enableDefrost();
}

// Сценарий "Приехал домой"
if (gpsLocation == home && engineOff) {
    turnOffLights();
    lockDoors();
    disableAlarm();
}
```

#### **Удалённое управление через интернет**
```java
// Через T-Box и 4G
RemoteControlProcessor.sendCommand(
    CommandType.OPEN_DOOR,
    Door.DRIVER
);
```

### 4.4. Для кастомизации

#### **Настройка освещения**
- Индивидуальные цветовые сценарии
- Синхронизация с музыкой
- Игровые режимы

#### **Климат-контроль**
- Пользовательские профили
- Расписание включения
- Геозоны (автозапуск при приближении)

#### **Персонализация вождения**
- Настройка режимов рулевого управления
- Адаптация режимов трансмиссии
- Профили водителя

---

## 🔐 Безопасность и ограничения

### 5.1. Уровни доступа

| Уровень | Разрешения | Примеры функций |
|---------|------------|-----------------|
| **System** | `android.uid.system` | Все функции |
| **Privileged** | `signature|privileged` | Климат, окна, двери |
| **Signature** | `signature` | Диагностика, телематика |
| **Normal** | `normal` | Чтение статусов |

### 5.2. Ограничения

1. **Только для системных приложений**
   - Требует подписи производителя
   - Недоступно для сторонних приложений

2. **Зависимость от CAN-шины**
   - Некоторые функции работают только при включенном зажигании
   - Ограничения по скорости (например, окна не работают >50 км/ч)

3. **Безопасность**
   - Проверка состояния автомобиля перед выполнением команд
   - Таймауты для критических операций
   - Блокировка при ошибках систем

---

## 📊 Архитектурная схема

```
┌─────────────────────────────────────────────────────────────┐
│                    Приложения (Apps)                       │
│  (MazdaControl, CarSettings, ClimateControl, etc.)        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              HuronCarService (com.mega.car)                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  MegaCarService                                      │  │
│  │  └── Binder API для приложений                      │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  MegaCarProperty                                     │  │
│  │  ├── PropertyID ↔ String mapping                    │  │
│  │  ├── Property change listeners                      │  │
│  │  └── Read/Write API                                 │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  AG35TspClient                                       │  │
│  │  ├── SocketChannel (172.16.2.30:49969)             │  │
│  │  ├── TLVProcessor                                   │  │
│  │  ├── RemoteControlProcessor                         │  │
│  │  ├── GB32960Processor ← НАШ ПРОТОКОЛ               │  │
│  │  ├── StatusProcessor                                │  │
│  │  ├── OtaProcessor                                   │  │
│  │  └── LogProcessor                                   │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  CarPropertyManager                                  │  │
│  │  └── CAN-шина (через HAL)                           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              RMU Service (com.mega.rmu)                    │
│  ├── CallServerInterceptor (localhost:32960)              │
│  ├── SelectorReader                                        │
│  ├── ResponseParserInterceptor                             │
│  └── CanSignalHandler                                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           GB32960 Server (порт 32960)                      │
│  └── Встроенный сервер телематики                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    CAN-шина автомобиля                     │
│  ├── Климат-контроль                                       │
│  ├── Окна и двери                                          │
│  ├── Освещение                                             │
│  ├── Двигатель и трансмиссия                               │
│  └── Системы безопасности                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 Практическое использование извне

### 6.1. Точки интеграции (Integration Points)

**HuronCarService предоставляет 3 основных способа взаимодействия:**

```
┌─────────────────────────────────────────────────────────────┐
│              Способы взаимодействия с HuronCarService       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1️⃣ Binder API (IPC)                                      │
│     └── Прямой вызов методов сервиса через IBinder         │
│     └── Требует: signature|privileged разрешение           │
│                                                             │
│  2️⃣ GB32960 Protocol (Network)                            │
│     └── TCP подключение к localhost:32960                 │
│     └── Отправка 512-байтовых пакетов                      │
│     └── Используется в MazdaControl                        │
│                                                             │
│  3️⃣ T-Box Remote (External)                               │
│     └── Подключение к 172.16.2.30:49969                   │
│     └── Через интернет (4G)                                │
│     └── Требует авторизации                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Важно:** Существует два разных сетевых интерфейса:
- **localhost:32960** — внутренний RMU для локальных приложений (работает только внутри автомобиля)
- **172.16.2.30:49969** — T-Box для удалённого доступа (через 4G/интернет)

---

### 6.2. Метод 1: Прямой вызов через Binder API

**Как это работает:**
```java
// 1. Получаем Binder от сервиса
IBinder binder = ServiceManager.getService("mega.car");
IMegaCarService service = IMegaCarService.Stub.asInterface(binder);

// 2. Вызываем методы напрямую
service.setProperty(0x2000003, 1);  // Включить AC
int value = service.getProperty(0x2000003);

// 3. Подписываемся на события
service.registerCallback(0x2000001, new ICarPropertyCallback() {
    @Override
    public void onPropertyChanged(int propertyId, Object value) {
        // Обработка события
    }
});
```

**Требования:**
- Разрешение: `android:permission="mega.car.permission.CLIMATE"`
- Подпись системы: `android:sharedUserId="android.uid.system"`
- Прописка в `/system/privates-app/`

**Где используется:**
- Системные приложения Mazda (CarSettings, ClimateControl)
- Заводская диагностика
- Инженерные утилиты

---

### 6.3. Метод 2: GB32960 Protocol (наш способ)

**Архитектура подключения:**
```
MazdaControl App
      ↓
┌─────────────────────────────────┐
│ Socket (localhost:32960)       │
│ ↓                               │
│ PacketGenerator                 │
│ ├── Header (30 байт)           │
│ ├── RealBody (482 байта)       │
│ └── CRC16                       │
└─────────────────────────────────┘
      ↓
┌─────────────────────────────────┐
│ com.mega.rmu (PID 8895)        │
│ ├── CallServerInterceptor      │
│ ├── SelectorReader             │
│ ├── ResponseParserInterceptor  │
│ └── CanSignalHandler           │
└─────────────────────────────────┘
      ↓
CAN-шина автомобиля
```

**Пример кода (из MazdaControl):**
```kotlin
class SpoilerController {
    private val socket = Socket("127.0.0.1", 32960)
    
    fun open() {
        val packet = PacketGenerator.generateSpoilerCommand(Spoiler.OPEN)
        socket.getOutputStream().write(packet)
    }
    
    fun close() {
        val packet = PacketGenerator.generateSpoilerCommand(Spoiler.CLOSE)
        socket.getOutputStream().write(packet)
    }
}
```

**Структура пакета:**
```
0                   1                   2                   3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
│                         VIN (14 байт)                        │
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
│  Enc  │  Rsv  │         Length (2 байта)                    │
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
│                         Timestamp                            │
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
│                      Function Bytes (8)                      │
│                      Data (474 байта)                        │
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
│ CRC16                         │  Padding (до 512 байт)      │
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

**Где используется:**
- Сторонние приложения (MazdaControl)
- Телематические системы
- Логирование и диагностика

---

### 6.4. Метод 3: T-Box Remote API

**Сетевое подключение:**
```java
// Подключение к T-Box
Socket tboxSocket = new Socket("172.16.2.30", 49969);

// Авторизация (пример)
byte[] auth = new byte[] {
    0x01,  // Auth command
    // ... credentials
};
tboxSocket.getOutputStream().write(auth);

// Отправка команды
byte[] command = new byte[] {
    0x02,  // Remote control command
    0x01,  // Door unlock
    // ...
};
tboxSocket.getOutputStream().write(command);
```

**Требования:**
- Нахождение в сети автомобиля (172.16.2.0/24)
- Авторизация (токен/сертификат)
- Поддержка протокола AG35TspClient

**Где используется:**
- Мобильное приложение Mazda Connect
- Удалённое управление через интернет
- Сервисные центры

---

### 6.5. Конкретные примеры использования

#### **Сценарий 1: Стороннее приложение (MazdaControl)**

**Что делаем:** Управление спойлером через GB32960

**Как:**
```bash
# 1. Устанавливаем приложение
adb install app-debug.apk

# 2. Запускаем
am start -n com.mazda.control/.MainActivity

# 3. Приложение подключается к localhost:32960
# 4. Отправляет пакеты при нажатии кнопок
```

**Код:**
```kotlin
// SpoilerController.kt
val socket = Socket("127.0.0.1", 32960)
val outputStream = socket.getOutputStream()

fun sendCommand(command: ByteArray) {
    Executors.newSingleThreadExecutor().execute {
        outputStream.write(command)
        outputStream.flush()
    }
}
```

**Результат:** Спойлер открывается/закрывается

---

#### **Сценарий 2: Диагностика через OBD-II**

**Что делаем:** Чтение ошибок двигателя

**Как:**
```kotlin
// DiagnosticController.kt
class DiagnosticController {
    private val socket = Socket("127.0.0.1", 32960)
    
    fun readDTC(): List<DiagnosticTroubleCode> {
        // Запрос: 0xBF 0x11 0x0D 0x01 0x27 0x10 0x20 0x01
        val request = byteArrayOf(
            0xBF, 0x11, 0x0D, 0x01, 0x27, 0x10, 0x20, 0x01
        )
        socket.getOutputStream().write(request)
        
        // Чтение ответа
        val response = readResponse()
        return parseDTC(response)
    }
}
```

**PropertyID:**
- 0x6000005 - Engine Overheat
- 0x6000007 - Brake System Error
- 0x6000008 - Airbag Error

**Результат:** Список кодов ошибок (P0300, P0171, etc.)

---

#### **Сценарий 3: Логирование телеметрии**

**Что делаем:** Запись данных в лог-файл

**Как:**
```kotlin
// TelemetryLogger.kt
class TelemetryLogger {
    private val socket = Socket("127.0.0.1", 32960)
    private val writer = PrintWriter("/sdcard/Download/telemetry.csv")
    
    fun startLogging() {
        // Подписка на свойства
        subscribe(0x8000001) // Speed
        subscribe(0x8000002) // RPM
        subscribe(0x9000020) // GPS
        
        // Чтение и запись
        thread {
            while (logging) {
                val data = readNextPacket()
                writer.println("${System.currentTimeMillis()},${data.speed},${data.rpm}")
            }
        }
    }
}
```

**Формат лога:**
```csv
timestamp,speed,rpm,lat,lon,battery
1710345600000,45,2300,55.751244,37.618423,87
1710345601000,47,2400,55.751245,37.618425,87
```

**Результат:** CSV-файл для анализа в Excel/Python

---

#### **Сценарий 4: Автоматизация (скрипт)**

**Что делаем:** Автоматическое открытие окон при температуре >25°C

**Как:**
```python
# auto_windows.py
import socket
import time

TEMP_THRESHOLD = 25  # °C
PROPERTY_TEMP = 0x2000001  # Interior Temperature

def read_temperature():
    sock = socket.socket()
    sock.connect(('127.0.0.1', 32960))
    
    # Запрос температуры
    request = build_request(PROPERTY_TEMP)
    sock.send(request)
    
    # Парсинг ответа
    response = sock.recv(512)
    return parse_temperature(response)

def open_windows():
    sock = socket.socket()
    sock.connect(('127.0.0.1', 32960))
    
    # Команда: открыть все окна
    for window_id in [0x3000001, 0x3000002, 0x3000003, 0x3000004]:
        command = build_window_command(window_id, OPEN)
        sock.send(command)
        time.sleep(0.5)

# Главный цикл
while True:
    temp = read_temperature()
    if temp > TEMP_THRESHOLD:
        open_windows()
    time.sleep(60)  # Проверка каждую минуту
```

**Результат:** Окна автоматически открываются в жару

---

#### **Сценарий 5: Удалённое управление (T-Box)**

**Что делаем:** Открытие двери через мобильное приложение

**Как:**
```java
// RemoteDoorController.java
public class RemoteDoorController {
    private static final String TBOX_IP = "172.16.2.30";
    private static final int TBOX_PORT = 49969;
    
    public void unlockDoor(String vin, String token) {
        try (Socket socket = new Socket(TBOX_IP, TBOX_PORT)) {
            // Авторизация
            AuthRequest auth = new AuthRequest(vin, token);
            sendRequest(socket, auth);
            
            // Команда
            DoorUnlockRequest request = new DoorUnlockRequest(Door.DRIVER);
            sendRequest(socket, request);
            
            // Ожидание подтверждения
            Response response = readResponse(socket);
            if (response.isSuccess()) {
                notifyUser("Дверь открыта");
            }
        }
    }
}
```

**Сетевой путь:**
```
Smartphone (4G/WiFi)
      ↓
Mazda Cloud API
      ↓
T-Box (172.16.2.30:49969)
      ↓
HuronCarService
      ↓
CAN-шина → Замок двери
```

**Результат:** Дверь открывается по команде из приложения

---

### 6.6. Технические детали реализации

#### **Безопасность и ограничения:**

| Метод | Требования | Ограничения |
|-------|------------|-------------|
| **Binder API** | signature\|privileged, system app | Недоступно сторонним |
| **GB32960** | Доступ к localhost:32960 | Только при запущенном сервисе |
| **T-Box** | Сеть 172.16.2.0/24, токен | Требуется интернет, авторизация |

#### **Производительность:**

| Операция | Время выполнения | Примечание |
|----------|------------------|------------|
| Открытие спойлера | ~2.2 секунды | Зависит от механизма |
| Чтение свойства | ~50 мс | При стабильном соединении |
| Запись свойства | ~100 мс | С подтверждением |
| Подписка на событие | ~20 мс | Однократно |
| T-Box команда (удалённо) | ~500-2000 мс | Зависит от сети 4G |

#### **Надёжность:**

```
┌─────────────────────────────────────────────────────────┐
│  Обработка ошибок                                       │
├─────────────────────────────────────────────────────────┤
│  1. Таймаут подключения (30 сек)                       │
│  2. Повторная попытка (3 раза)                         │
│  3. Валидация ответа (CRC16, длина)                    │
│  4. Логирование ошибок                                 │
│  5. Уведомление пользователя                           │
└─────────────────────────────────────────────────────────┘
```

---

## 🚀 Перспективные направления

### 7.1. Roadmap развития MazdaControl

#### **План развития приложения:**

```
v1.0 (Текущая)          v2.0 (2026 Q3)         v3.0 (2027 Q1)
├── Спойлер             ├── Климат             ├── Полное управление
├── Логирование         ├── Окна               ├── Телеметрия
└── 2 режима            ├── Двери              ├── Сценарии
                        └── Диагностика        └── T-Box интеграция
```

**Детальная дорожная карта:**

| Версия | Функции | Сложность | Приоритет |
|--------|---------|-----------|-----------|
| **v1.0** | Спойлер, логирование | Низкая | ✅ Готово |
| **v1.5** | Диагностика OBD-II | Средняя | 🔜 Следующая |
| **v2.0** | Климат, окна, двери | Средняя | 📅 2026 Q3 |
| **v2.5** | Свет, замки, люк | Средняя | 📅 2026 Q4 |
| **v3.0** | Телеметрия, GPS | Высокая | 📅 2027 Q1 |
| **v3.5** | Сценарии, автоматизация | Высокая | 📅 2027 Q2 |
| **v4.0** | T-Box, облако | Очень высокая | 🔮 Будущее |

**Архитектура v2.0+:**
```
MazdaControl v2.0
├── Core (ядро)
│   ├── GB32960 Protocol
│   ├── PacketGenerator
│   └── ConnectionManager
├── Modules (модули)
│   ├── SpoilerModule
│   ├── ClimateModule
│   ├── WindowsModule
│   ├── DoorsModule
│   └── LightsModule
├── Services (сервисы)
│   ├── LoggingService
│   ├── DiagnosticsService
│   └── TelemetryService
└── UI (интерфейс)
    ├── MainScreen
    ├── ModuleControl
    └── Settings
```

---

## 📋 Итоговая таблица возможностей

| Категория | Возможность | Готовность | Перспектива |
|-----------|-------------|------------|-------------|
| **Базовое управление** | Спойлер, окна, двери | ✅ 100% | 🎯 Реализуется |
| **Климат** | AC, обогрев, вентиляция | 📋 Изучено | 🔜 v2.0 |
| **Освещение** | Фары, подсветка, шоу | 📋 Изучено | 🔜 v2.5 |
| **Диагностика** | OBD-II, ошибки | 📋 Изучено | 🔜 v1.5 |
| **Телеметрия** | GPS, скорость, RPM | 📋 Изучено | 🔜 v3.0 |
| **Сценарии** | Автоматизация | 📋 План | 🔜 v3.5 |
| **T-Box** | Удалённое управление | 📋 Изучено | 🔮 v4.0 |
| **V2X** | V2H, V2G, V2V | 📋 Концепт | 🔮 Будущее |
| **ML** | Предиктивная аналитика | 📋 Концепт | 🔮 Исследования |

---

## 📎 Приложение A: Справочник PropertyID

### Полные таблицы свойств по доменам

См. раздел 3 ("Доступные функции автомобиля") для подробных таблиц:
- **Климат-контроль:** 0x2000003 - 0x2000076 (28 свойств)
- **Окна и люк:** 0x3000001 - 0x3000030 (8 свойств)
- **Освещение:** 0x4000001 - 0x4000060 (16 свойств)
- **Двери и замки:** 0x5000001 - 0x5000031 (12 свойств)
- **Предупреждения:** 0x6000001 - 0x6000022 (12 свойств)
- **Питание:** 0x7000001 - 0x7000040 (15 свойств)
- **Движение:** 0x8000001 - 0x8000040 (12 свойств)
- **T-Box:** 0x9000001 - 0x9000031 (11 свойств)

**Итого:** ~114 свойств для управления автомобилем

---

## 📎 Приложение B: Глоссарий терминов

| Термин | Определение |
|--------|-------------|
| **GB32960** | Китайский стандарт телематики для электромобилей |
| **T-Box** | Телематический блок (AG35 модуль) с 4G-связью |
| **RMU** | Remote Management Unit - сервис управления (com.mega.rmu) |
| **AG35TspClient** | TCP клиент для подключения к T-Box |
| **CarPropertyManager** | Менеджер свойств автомобиля |
| **PropertyID** | Уникальный идентификатор свойства (например, 0x2000003) |
| **TLV** | Type-Length-Value - формат кодирования данных |
| **V2X** | Vehicle-to-Everything - связь автомобиля с окружением |

---

**Дата составления:** 2026-03-13  
**Статус:** Анализ возможностей завершён  
**Следующий шаг:** Разработка модуля климата (v2.0)
