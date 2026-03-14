# 🎛️ MegaControllerService Analysis

**Дата:** 2026-03-14  
**Файл:** `smali_classes2/com/mega/controller/service/MegaControllerService.smali`  
**Статус:** ✅ **АНАЛИЗ ЗАВЕРШЁН**

---

## 📋 Содержание

1. [Назначение](#назначение)
2. [Архитектура](#архитектура)
3. [Контроллеры (28 штук)](#контроллеры-28-штук)
4. [Жизненный цикл](#жизненный-цикл)
5. [AIDL Binders](#aidl-binders)
6. [TLV Protocol Integration](#tlv-protocol-integration)
7. [Выводы](#выводы)

---

## Назначение

**MegaControllerService** — центральный Android Service, который:
- Инициализирует все 28 контроллеров приложения
- Управляет жизненным циклом контроллеров
- Предоставляет AIDL интерфейсы для межпроцессного взаимодействия
- Координирует работу с MegaCarProperty

**Тип:** `android.app.Service`  
**Процесс:** `com.mega.controller`

---

## Архитектура

```
┌─────────────────────────────────────────────────┐
│           MegaControllerService                 │
│  (extends android.app.Service)                  │
├─────────────────────────────────────────────────┤
│  mMegaCarProperty (singleton)                   │
│  mHandlerThread (background)                    │
│  mWorkHandler (worker)                          │
│  mTboxCallback (TLV events)                     │
├─────────────────────────────────────────────────┤
│  28 Controllers (initialized in onCreate)       │
│  - CarbodyController (спойлер, двери)           │
│  - CustomInputEventController (кнопки)          │
│  - SentryController (охрана)                    │
│  - RemoteLiveController (удалённый доступ)      │
│  - ...                                          │
└─────────────────────────────────────────────────┘
           ↓
    MegaCarProperty
           ↓
    HuronCarService (CAN-шина)
```

---

## Контроллеры (28 штук)

### Инициализация в `onCreate()`:

| # | Контроллер | Переменная | Приоритет |
|---|------------|------------|-----------|
| 1 | **TspController** | mTspController | ⭐⭐⭐ |
| 2 | **CustomInputEventController** | mCustomInputEventController | ⭐⭐⭐ |
| 3 | **GestureController** | mGestureController | ⭐⭐ |
| 4 | **LiveDetectionController** | mLiveDetectionController | ⭐⭐ |
| 5 | **SettingsController** | mSettingsController | ⭐⭐ |
| 6 | **AudioController** | mAudioController | ⭐ |
| 7 | **AtmoController** | mAtmoController | ⭐ |
| 8 | **TtsController** | mTtsController | ⭐ |
| 9 | **ScreenOffController** | mScreenOffController | ⭐⭐ |
| 10 | **RestrictedController** | mRestrictedController | ⭐ |
| 11 | **CarbodyController** | mCarbodyController | ⭐⭐⭐ |
| 12 | **CalibController** | mCalibController | ⭐ |
| 13 | **NotifyController** | mNotifyController | ⭐ |
| 14 | **SceneModeController** | mSceneModeController | ⭐⭐ |
| 15 | **ECallController** | mEcallController | ⭐⭐ |
| 16 | **WiperController** | mWiperController | ⭐ |
| 17 | **BtPhoneController** | mBtPhoneController | ⭐ |
| 18 | **PrivacyAuthController** | mPrivacyAuthController | ⭐ |
| 19 | **DayNightController** | mDayNightController | ⭐⭐ |
| 20 | **VehicleHealthController** | mVehicleHealthController | ⭐⭐ |
| 21 | **OtaController** | mOtaController | ⭐⭐ |
| 22 | **ChildrenForgetController** | mChildrenForgetController | ⭐⭐ |
| 23 | **DataTrackController** | mDataTrackController | ⭐ |
| 24 | **SecondBluetoothController** | mSecondBluetoothController | ⭐ |
| 25 | **InteractSetterImpl** | mInteractSetter | ⭐⭐⭐ |
| 26 | **LiveObserverImpl** | mLiveObserverImpl | ⭐⭐ |
| 27 | **ApaClientImpl** | mApaClient | ⭐ |
| 28 | **SentryController** | mSentryController | ⭐⭐⭐ |
| 29 | **InsulationController** | mInsulationController | ⭐ |
| 30 | **RmuMsgController** | mRmuMsgController | ⭐ |
| 31 | **XCallController** | mXCallController | ⭐⭐ |
| 32 | **TimeController** | mTimeController | ⭐ |
| 33 | **TrailerController** | mTrailerController | ⭐ |
| 34 | **VitalSignsDetectController** | mVitalSignsDetectController | ⭐ |
| 35 | **WarningNoticeController** | mWarningNoticeController | ⭐⭐ |

**Примечание:** Некоторые контроллеры инициализируются условно (по конфигурации).

---

## Жизненный цикл

### onCreate()

```java
1. Проверка: isSingleUserOrSystem() → если false, выход
2. MegaCarProperty.getInstance() → singleton
3. HandlerThread.start() → background поток
4. TspController.init() → телематика
5. Регистрация mTboxCallback (TLV события)
6. Инициализация 28 контроллеров
7. TboxOperator.addCallback() → обработка звонков
8. initUsbStateListener() → USB события
```

### onDestroy()

```java
Вызов release() для всех контроллеров:
- CustomInputEventController.release()
- GestureController.release()
- LiveDetectionController.release()
- ... (все 28)
```

### onConfigurationChanged()

```java
uiModeChanged() вызывается для:
- XCallController
- CustomInputEventController
```

---

## AIDL Binders

### onBind() возвращает 3 типа binder'ов:

**1. InteractManager (UI блокировки):**
```java
ACTION: "mega.controller.interact.InteractManager.ACTION"
Binder: mInteractSetter (InteractSetterImpl)
```

**2. ApaClient (Автопарковка):**
```java
ACTION: "mega.controller.apa.ApaManager.ACTION"
Binder: mApaClient (ApaClientImpl)
```

**3. AliveManager (Мониторинг процесса):**
```java
ACTION: "mega.controller.proc.AliveManager.ACTION"
Binder: mLiveObserverImpl (LiveObserverImpl)
```

---

## TLV Protocol Integration

### Регистрация на TLV события:

```java
mTboxCallback = new CarPropertyEventCallback()

mMegaCarProperty.registerCallback(
  mTboxCallback,
  Collections.singleton(0x30000001)  // TLV Message ID
)
```

**Обработка событий:**
- Получение TLV-данных от T-Box
- Маршрутизация по контроллерам
- Обновление состояния автомобиля

### Константы TLV:

```java
TLV_CONTENT_TYPE = 0x27D8   // Type ID для контента
TLV_CONTENT_VALUE = 0x01    // Значение контента
```

---

## Условная инициализация

### RemoteLiveController:
```java
if (isSupportConfig("ro.ecu.config.REMOTE_VIDEO_MONITOR")) {
  mRemoteLiveController = new RemoteLiveController()
} else {
  HttpController.getInstance() // Fallback
}
```

### AtmoController (Ambient Lighting):
```java
if (getInt("ro.ecu.config.AMB_LAMP") == 2) {
  mAtmoController = new AtmoController()
}
```

### SecondBluetoothController:
```java
if (get("ro.ecu.config.PASSENGER_SCREEN") == "1") {
  mSecondBluetoothController = new SecondBluetoothController()
}
```

### SentryController:
```java
if (isJ90A() && isSupportEcuByConfig("ro.ecu.config.CAMERA_SCHEME")) {
  mSentryController = new SentryController()
}
```

### TrailerController + VitalSignsDetectController:
```java
if (IS_EU) {  // Только для EU рынка
  mTrailerController = new TrailerController()
  mVitalSignsDetectController = new VitalSignsDetectController()
}
```

---

## Выводы

### Роль в архитектуре:

**MegaControllerService** — "оркестратор" всех контроллеров:

1. ✅ **Централизованная инициализация** — все контроллеры создаются в одном месте
2. ✅ **Управление жизненным циклом** — release() при уничтожении сервиса
3. ✅ **AIDL интерфейсы** — 3 binder'а для других процессов
4. ✅ **TLV интеграция** — регистрация на события T-Box
5. ✅ **Background поток** — HandlerThread для неблокирующих операций

### Связь со спойлером:

```
MegaControllerService.onCreate()
  ↓
mCarbodyController = new CarbodyController()
mCustomInputEventController = new CustomInputEventController()
  ↓
Эти контроллеры управляют спойлером через MegaCarProperty
```

### Ключевые зависимости:

- **MegaCarProperty** — singleton для доступа к свойствам
- **TspController** — телематика (инициализируется первым)
- **InteractSetterImpl** — система блокировок UI
- **TboxOperator** — обработка событий T-Box

### Масштаб:

- **28 контроллеров** в одном сервисе
- **3 AIDL интерфейса** для IPC
- **1 HandlerThread** для фоновой работы
- **~500 строк** только в onCreate()

---

**📍 Следующий шаг:** Изучить TspController (телематика) или SentryController (охранная система) для понимания TLV-коммуникации.
