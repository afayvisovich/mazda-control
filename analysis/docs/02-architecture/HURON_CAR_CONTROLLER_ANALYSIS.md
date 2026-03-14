# HuronCarController Analysis Report

## 📋 Общее описание

**HuronCarController** (`com.mega.controller`) — это **главное приложение управления автомобилем** Mazda, которое координирует все функции автомобиля через централизованный сервис.

---

## 🎯 Назначение приложения

### Ключевое отличие от HuronCarService

| Приложение | Package | Назначение |
|------------|---------|------------|
| **HuronCarController** | `com.mega.controller` | **UI-приложение** с активностями, сервисами, контроллерами |
| **HuronCarService** | `com.mega.car` | **Системный сервис** (фоновый) для доступа к CAN-шине |

**HuronCarController** использует **HuronCarService** как библиотеку для доступа к автомобилю.

---

## 🔧 Архитектура

### 2.1. Главный сервис

```
MegaControllerApplication (Application)
         ↓
MegaControllerService (Service)
         ↓
┌────────────────────────────────────────────────────────────┐
│  Контроллеры (Controllers)                                │
├────────────────────────────────────────────────────────────┤
│  ├── ApaController (APA - Auto Parking Assist)            │
│  ├── AudioController (Аудиосистема)                       │
│  ├── BackLightController (Подсветка)                      │
│  ├── BtPhoneController (Bluetooth телефон)                │
│  ├── CalibController (Калибровка)                         │
│  ├── CarbodyController (Кузов)                            │
│  ├── ChildrenForgetController (Child Presence Detection)  │
│  ├── DayNightController (День/Ночь режим)                 │
│  ├── ECallController (Экстренный вызов)                   │
│  ├── GestureController (Жесты)                            │
│  ├── InsulationController (Изоляция батареи)              │
│  ├── LiveDetectionController (Детекция жизни)             │
│  ├── NotifyController (Уведомления)                       │
│  ├── OtaController (OTA-обновления)                       │
│  ├── PrivacyAuthController (Авторизация)                  │
│  ├── RemoteLiveController (Удалённый мониторинг)          │
│  ├── RmuMsgController (RMU сообщения)                     │
│  ├── SceneModeController (Сценарии)                       │
│  ├── ScreenOffController (Выключение экрана)              │
│  ├── SentryController (Охранная система)                  │
│  ├── SettingsController (Настройки)                       │
│  ├── TimeController (Время)                               │
│  ├── TrailerController (Прицеп)                           │
│  ├── TtsController (TTS - Text-to-Speech)                 │
│  ├── VehicleHealthController (Диагностика)                │
│  ├── VitalSignsDetectController (Vital Signs)             │
│  ├── WarningNoticeController (Предупреждения)             │
│  ├── WiperController (Стеклоочистители)                   │
│  └── XCallController (X-Call)                             │
└────────────────────────────────────────────────────────────┘
```

---

## 🔐 Разрешения

### Системные разрешения (signature|privileged)

```xml
android.uid.system  ← Системные привилегии
mega.car.permission.KeyInput
mega.car.permission.CLIMATE
mega.car.permission.CAR_DRIVING
mega.car.permission.CAR_WINDOWS
mega.car.permission.LIGHTING
mega.car.permission.ENTRY_LOCK
mega.car.permission.ELEC_POWER
mega.car.permission.VEHICLE_MOTION
mega.car.permission.INPUT_FORWARD_CENTER
mega.car.permission.INFOTAINMENT
mega.car.permission.COMFORTS_SETTINGS
mega.car.permission.ECU
mega.car.permission.DMS
mega.car.permission.NET
mega.car.permission.LOCAL_EVENT
```

### Стандартные разрешения

```xml
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
BLUETOOTH
BLUETOOTH_ADMIN
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
CAMERA
RECORD_AUDIO
READ_PHONE_STATE
WRITE_EXTERNAL_STORAGE
READ_EXTERNAL_STORAGE
RECEIVE_BOOT_COMPLETED
WAKE_LOCK
SYSTEM_ALERT_WINDOW
INTERACT_ACROSS_USERS
CHANGE_NETWORK_STATE
```

---

## 🚗 Ключевые компоненты

### 3.1. MegaControllerService

**Класс:** `com.mega.controller.service.MegaControllerService`

**Назначение:** Главный сервис, инициализирующий все контроллеры

**Поля:**
```java
// Интеграция с HuronCarService
private MegaCarProperty mMegaCarProperty;

// Контроллеры
private ApaController mApaController;
private AudioController mAudioController;
private BackLightController mBackLightController;
private BtPhoneController mBtPhoneController;
private CarbodyController mCarbodyController;
private ChildrenForgetController mChildrenForgetController;
private DayNightController mDayNightController;
private ECallController mEcallController;
private GestureController mGestureController;
private SentryController mSentryController;
private SettingsController mSettingsController;
private WarningNoticeController mWarningNoticeController;
private WiperController mWiperController;
// ... и ещё 20+ контроллеров
```

**Intent Filters:**
```xml
<!-- Основной сервис -->
<action android:name="com.mega.controller.service"/>

<!-- Ночной режим -->
<action android:name="com.mega.controller.night"/>

<!-- Автоматический режим -->
<action android:name="com.mega.controller.mode.auto"/>

<!-- Экстренные вызовы -->
<action android:name="mega.intent.action.BCALL"/>
<action android:name="mega.intent.action.ECALL"/>
```

---

### 3.2. MegaCarProperty (интеграция с HuronCarService)

**Класс:** `mega.car.MegaCarProperty`

**Назначение:** Предоставляет API для доступа к свойствам автомобиля

**Использование:**
```java
// Получение экземпляра
MegaCarProperty property = MegaCarProperty.getInstance();

// Чтение свойства
int value = property.getProperty(PROPERTY_ID);

// Запись свойства
property.setProperty(PROPERTY_ID, value);

// Подписка на изменения
property.registerCallback(PROPERTY_ID, callback);
```

**Важно:** Этот класс находится в **HuronCarService**, но используется из **HuronCarController**.

---

### 3.3. SentryController (Охранная система)

**Класс:** `com.mega.controller.sentry.SentryController`

**Назначение:** Управление охранным режимом автомобиля

**SentryHelper:**
```java
public class SentryHelper {
    // Ключевые константы
    public static final String KEY_SENTRY_ACTIVE_FLAG = "key_sentry_active_flag";
    public static final String KEY_SENTRY_DAY_TIMES = "key_sentry_day_times";
    public static final String KEY_SENTRY_OPEN_FLAG = "key_sentry_open_flag";
    public static final String KEY_REMOTE_LAST_STATUS = "key_remote_last_status";
    
    // Значения по умолчанию
    public static final String DEFAULT_ACTIVE_VALUE = "0_0";
    public static final String DEFAULT_OPEN_VALUE = "0";
    public static final String DEFAULT_REMOTE_VALUE = "0";
}
```

**ISentryOption (Binder API):**
```java
interface ISentryOption {
    void activateSentry();
    void deactivateSentry();
    boolean isSentryActive();
    void setSentryConfig(String config);
}
```

---

### 3.4. ApaManager (Auto Parking Assist)

**Класс:** `mega.controller.apa.ApaManager`

**Назначение:** Управление системой автоматической парковки

**APA Client:**
```java
public class ApaManager {
    private ApaClient mApaClient;
    private IApaCallBack mCallBack;
    
    // Команды парковки
    void startParking();
    void stopParking();
    void pauseParking();
    void selectSlot(int slotId);
}
```

**IApaCallBack:**
```java
interface IApaCallBack {
    void onParkingStarted();
    void onParkingCompleted();
    void onParkingFailed(int errorCode);
    void onSlotSelected(int slotId);
}
```

---

### 3.5. VehicleHealthController (Диагностика)

**Класс:** `com.mega.controller.vehiclehealth.VehicleHealthController`

**Назначение:** Мониторинг состояния систем автомобиля

**Проверки:**
```java
// Диагностика систем
class DiagnosisProtocol {
    enum DiagnosisStatus {
        OK,
        WARNING,
        ERROR,
        UNKNOWN
    }
    
    // Проверки по системам
    MotorCheck      // Двигатель
    AbsCheck        // ABS
    EscCheck        // ESP
    EpsCheck        // Электроусилитель руля
    AirbagCheck     // Подушки безопасности
    TpmsCheck       // Давление в шинах
    DiagnosisCheck  // Общая диагностика
}
```

**Использование:**
```java
VehicleHealthController controller = new VehicleHealthController();
List<DiagnosisProtocol> checks = controller.getAllChecks();

for (DiagnosisProtocol check : checks) {
    DiagnosisStatus status = check.getStatus();
    if (status != DiagnosisStatus.OK) {
        showWarning(check.getName(), status);
    }
}
```

---

### 3.6. DataCollectorController (Сбор данных)

**Класс:** `com.mega.controller.vmp.report.DataCollectorController`

**Назначение:** Сбор и отправка телеметрии

**Собираемые данные:**
```java
class DataCollectorController {
    enum CarPowerState {
        OFF,
        ACC,
        ON,
        START
    }
    
    enum NetworkState {
        CONNECTED,
        DISCONNECTED,
        UNKNOWN
    }
    
    // Сбор данных
    void collectVehicleData();
    void collectGpsData();
    void collectDriverBehavior();
    void sendToServer();
}
```

**JourneyEventSender:**
```java
// Отправка данных о поездке
class JourneyEventSender {
    void startJourney();
    void endJourney();
    void reportEvent(JourneyEvent event);
}
```

---

### 3.7. ChildrenForgetController (Child Presence Detection)

**Класс:** `com.mega.controller.childrenforget.ChildrenForgetController`

**Назначение:** Обнаружение детей в автомобиле

**ChildrenForgetSettings:**
```java
class ChildrenForgetSettings {
    // Настройки
    boolean enabled;
    int sensitivity;
    int timeout;
    
    // События
    void onChildDetected();
    void onChildLeft();
    void sendAlert();
}
```

**Алгоритм работы:**
1. Мониторинг салона (камеры/датчики)
2. Обнаружение движения после парковки
3. Уведомление владельца
4. Экстренный вызов при необходимости

---

### 3.8. DayNightController (День/Ночь режим)

**Класс:** `com.mega.controller.daynight.DayNightController`

**Назначение:** Автоматическое переключение дневного/ночного режима

**MegaControllerApplication:**
```java
public class MegaControllerApplication extends Application {
    private boolean mIsNightOfUIModeLast;
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int uiMode = newConfig.uiMode & 0x30;
        boolean isNight = (uiMode == 0x20);
        
        if (isNight != mIsNightOfUIModeLast) {
            mIsNightOfUIModeLast = isNight;
            BackLightController.nightModeChange(context, isNight);
        }
    }
}
```

---

### 3.9. ECallController / XCallController (Экстренные вызовы)

**ECallController:**
```java
class ECallController {
    // Экстренный вызов при ДТП
    void triggerECall();
    void cancelECall();
    
    // Данные для экстренных служб
    void sendLocation();
    void sendVehicleData();
    void establishVoiceCall();
}
```

**XCallController:**
```java
class XCallController {
    // Расширенные вызовы
    void remoteStart();
    void remoteClimate();
    void remoteLock();
}
```

---

### 3.10. TtsController (Text-to-Speech)

**Класс:** `com.mega.controller.tts.TtsController`

**Назначение:** Синтез речи для уведомлений

**TTSSpeaker:**
```java
class TTSSpeaker {
    interface TTSStatusListener {
        void onSpeakingStarted();
        void onSpeakingCompleted();
        void onSpeakingFailed();
    }
    
    void speak(String text);
    void stop();
    void setLanguage(Locale locale);
}
```

**AdsVoiceConfig:**
```java
// Конфигурация голосовых подсказок
class AdsVoiceConfig {
    String welcomeMessage;
    String warningMessage;
    String infoMessage;
}
```

**Файлы конфигурации:**
- `assets/adsvoiceconfig.json` - настройки голосовых подсказок
- `assets/welcome.wav` - приветствие
- `assets/farewell.wav` - прощание

---

## 📊 Взаимодействие с другими компонентами

### 4.1. С HuronCarService

```
┌─────────────────────────────────────────────────────────────┐
│  HuronCarController                                         │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  MegaControllerService                                │ │
│  │  └── Контроллеры                                      │ │
│  └───────────────────────────────────────────────────────┘ │
│                            ↓                                │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  MegaCarProperty (из HuronCarService)                │ │
│  │  └── Binder API к CarPropertyService                 │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  HuronCarService (com.mega.car)                            │
│  └── CarPropertyService                                    │
└─────────────────────────────────────────────────────────────┘
```

### 4.2. С RMU (com.mega.rmu)

```
HuronCarController
        ↓
MegaCarProperty
        ↓
HuronCarService (AG35TspClient)
        ↓
RMU Service (com.mega.rmu)
        ↓
GB32960 Server (localhost:32960)
```

### 4.3. С T-Box

```
HuronCarController
        ↓
TspClient (com.mega.controller.tsp)
        ↓
T-Box (172.16.2.30:49969)
        ↓
4G / Интернет
```

---

## 🔍 Уникальные возможности

### 5.1. Охранная система (Sentry Mode)

**Функции:**
- Активация/деактивация охранного режима
- Мониторинг перемещений
- Уведомления владельца
- Автоматическая запись с камер
- Отправка данных на сервер

**Конфигурация:**
```java
SentryHelper helper = SentryHelper.getInstance();
helper.bindSentry();

// Проверка статуса
boolean isActive = helper.isSentryActive();

// Настройки
helper.setConfig("sentry_sensitivity", "high");
helper.setConfig("sentry_timeout", "300");  // 5 минут
```

---

### 5.2. Автоматическая парковка (APA)

**Функции:**
- Поиск парковочного места
- Автоматическая парковка
- Контроль окружения
- Уведомления о процессе

**Использование:**
```java
ApaManager apaManager = ApaManager.getInstance();
apaManager.setCallBack(new IApaCallBack() {
    @Override
    public void onParkingStarted() {
        showParkingUI();
    }
    
    @Override
    public void onParkingCompleted() {
        hideParkingUI();
        showSuccess();
    }
    
    @Override
    public void onParkingFailed(int errorCode) {
        hideParkingUI();
        showError(errorCode);
    }
});

// Запуск парковки
apaManager.startParking();
```

---

### 5.3. Обнаружение детей (CPD)

**Функции:**
- Мониторинг салона после парковки
- Обнаружение движения
- Температурный мониторинг
- Экстренное уведомление

**Алгоритм:**
```
1. Двигатель выключен
2. Все двери закрыты
3. Запуск таймера (10 минут)
4. Мониторинг камер/датчиков
5. При обнаружении движения → Уведомление
6. При высокой температуре → Экстренный вызов
```

---

### 5.4. Диагностика систем

**Функции:**
- Проверка всех систем автомобиля
- Коды ошибок
- Рекомендации по ТО
- История диагностики

**Пример использования:**
```java
VehicleHealthController health = new VehicleHealthController();

// Проверка двигателя
MotorCheck motorCheck = health.getMotorCheck();
if (motorCheck.getStatus() != DiagnosisStatus.OK) {
    List<DTC> codes = motorCheck.getTroubleCodes();
    for (DTC code : codes) {
        logError(code);
    }
}

// Проверка давления в шинах
TpmsCheck tpms = health.getTpmsCheck();
for (WheelPressure pressure : tpms.getPressures()) {
    if (pressure.getValue() < 2.0) {
        showLowPressureWarning(pressure.getWheel());
    }
}
```

---

### 5.5. Сбор телеметрии

**Функции:**
- Запись данных о поездке
- Стиль вождения
- Расход энергии
- GPS-треки

**JourneyEventSender:**
```java
JourneyEventSender sender = new JourneyEventSender();

// Начало поездки
sender.startJourney();

// События в пути
sender.reportEvent(new JourneyEvent(
    EventType.HARD_BRAKING,
    timestamp,
    location,
    severity
));

// Конец поездки
sender.endJourney();
sender.sendToServer();
```

---

## 📁 Структура файлов

### Assets

```
assets/
├── adsvoiceconfig.json      # Голосовые подсказки
├── atmo_color_config.json   # Настройки атмосферы
├── backlight_config.conf    # Подсветка
├── welcome.wav              # Приветствие
├── farewell.wav             # Прощание
├── icwarning_config.json    # Предупреждения
├── blur_cache/              # Кэш размытия
└── masking/                 # Маскирование
```

### Библиотеки

```
lib/arm64-v8a/
├── libnative-lib.so
├── libaudio-engine.so
└── libcamera-hal.so
```

### Resources

```
res/
├── layout/      # UI макеты
├── drawable/    # Графика
├── values/      # Строки, цвета
├── raw/         # Аудиофайлы
└── xml/         # Конфигурации
```

---

## 🔐 Безопасность

### Protected Broadcasts

```xml
<!-- Ночной режим -->
<protected-broadcast android:name="com.mega.controller.night.app"/>

<!-- Принудительный сон -->
<protected-broadcast android:name="mega.intent.action.PREPARE_FORCE_SLEEP"/>
<protected-broadcast android:name="mega.intent.action.AWAKE_FORCE_SLEEP"/>
```

### Binder API

**Интерфейсы:**
- `ISentryOption` - охранный режим
- `IApaCallBack` - автоматическая парковка
- `IInteractSetter` - взаимодействие
- `IProcessClient` - управление процессами
- `ILiveObserver` - мониторинг жизни

---

## 💡 Выводы

### Назначение приложения

**HuronCarController** — это **основное UI-приложение** для управления автомобилем Mazda, которое:

1. **Координирует все функции** через систему контроллеров
2. **Использует HuronCarService** как библиотеку для доступа к CAN-шине
3. **Предоставляет пользовательский интерфейс** для всех функций
4. **Собирает телеметрию** и отправляет на сервер
5. **Управляет охранными функциями** (Sentry Mode)
6. **Поддерживает автоматическую парковку** (APA)
7. **Контролирует наличие детей** (CPD)
8. **Выполняет диагностику** систем автомобиля

### Ключевые отличия от HuronCarService

| Характеристика | HuronCarController | HuronCarService |
|----------------|-------------------|-----------------|
| **Тип** | UI-приложение | Системный сервис |
| **Package** | `com.mega.controller` | `com.mega.car` |
| **Компоненты** | Контроллеры, активности | Processors, интерсепторы |
| **Назначение** | Управление функциями | Доступ к CAN-шине |
| **Связь** | Использует MegaCarProperty | Предоставляет MegaCarProperty |

### Потенциал для MazdaControl

Из HuronCarController можно заимствовать:

1. **Структуру контроллеров** - модульная архитектура
2. **Sentry Mode** - охранные функции
3. **Vehicle Health** - диагностика систем
4. **Data Collection** - сбор телеметрии
5. **TTS Integration** - голосовые уведомления
6. **APA Framework** - для будущих функций автопарковки

---

**Дата анализа:** 2026-03-14
**Статус:** Analysis Complete
**Следующий шаг:** Интеграция полезных компонентов в MazdaControl
