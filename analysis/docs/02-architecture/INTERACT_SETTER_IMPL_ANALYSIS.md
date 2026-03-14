# InteractSetterImpl Analysis

## Overview

**InteractSetterImpl** — это реализация AIDL-интерфейса `InteractSetter`, которая управляет системой взаимодействия (interaction system) между UI-приложением (HuronCarController) и системными сервисами автомобиля.

**Основная задача:** Управление блокировками UI-функций на основе состояния автомобиля (скорость, передача, экраны) и регистрация клиентов с различными флагами функций.

---

## Architecture

### Class Hierarchy

```
InteractSetter (AIDL interface)
    ↑
    └── InteractSetter$Stub (Binder stub)
            ↑
            └── InteractSetterImpl (Implementation)
```

### Key Dependencies

- **MegaCarProperty** — доступ к свойствам автомобиля (скорость, передача)
- **IMegaNexusManager** — управление состоянием экранов
- **InteractClient** — клиенты, регистрирующиеся для получения функций
- **Settings.Global** — настройка блокировки (`com.mega.controller.restrict_switch`)

---

## Core Features

### 1. Speed-Based Restrictions (Ограничения по скорости)

**Логика блокировки:**

```java
private boolean isDriveRestrict() {
    return mIsRestrictOn && mIsOverSpeed && mDrivingInfoGear != 0;
}
```

**Условия:**
- `mIsRestrictOn` — настройка включена в Settings.Global
- `mIsOverSpeed` — скорость > 7.0 м/с (~25 км/ч)
- `mDrivingInfoGear != 0` — передача не в нейтрали (D или R)

**Мониторинг скорости:**
```java
// Property ID: 0x66000001 (Vehicle Speed)
mMegaCarProperty.getFloatProp(0x66000001) > 7.0f
```

### 2. Gear State Tracking (Отслеживание передачи)

```java
// Property ID: 0x66000007 (Gear Position)
mDrivingInfoGear = mMegaCarProperty.getIntProp(0x66000007)
```

**Reverse Detection:**
```java
private boolean isReverseGear() {
    return mDrivingInfoGear == 1; // 1 = Reverse
}
```

### 3. Screen State Management (Управление экранами)

**Мониторинг двух экранов:**
```java
// Primary screen (ID=0)
mPrimaryScreenState = mNexusmanager.getScreenState(0)

// Secondary/Passenger screen (ID=1)
mSecondaryScreenState = mNexusmanager.getScreenState(1)
```

**Screen States:**
- `1` = SCREEN_STATE_ON
- `0` = SCREEN_STATE_OFF

### 4. Feature Flag System (Система флагов функций)

**InteractFeature — битовые флаги:**

| Flag | Value | Description |
|------|-------|-------------|
| `FLAG_PRIMARY_CLEAN` | 0x1 | Очистка основного экрана |
| `FLAG_PASSENGER_CLEAN` | 0x2 | Очистка пассажирского экрана |
| `FLAG_OTA_UPGRADE` | 0x4 | OTA обновление |
| `FLAG_USR_FACING` | 0x8 | Пользовательский режим |
| `FLAG_CAR_EXHIBIT` | 0x10 | Режим автосалона |
| `FLAG_CONST_TEMPERATURE` | 0x20 | Постоянная температура |
| `FLAG_WASH_CAR` | 0x40 | Мойка автомобиля |
| `FLAG_SHORT_SLEEP` | 0x80 | Короткий сон |
| `FLAG_FIRE_WINTER` | 0x100 | Зимний режим |
| `FLAG_EMERGENCY_CALL` | 0x200 | Экстренный вызов |
| `FLAG_LIGTH_SHOW` | 0x400 | Световое шоу |
| `FLAG_MIRROR_MODE` | 0x800 | Режим зеркал |
| `FLAG_UI_APPLIST` | 0x1000 | Список приложений |
| `FLAG_HURON_LIGHT` | 0x4000 | Подсветка Huron |
| `FLAG_DOCK_HVVC` | 0x2000 | Док-станция HVVC |
| `FLAG_AVM_BLOCK` | 0x8000 | Блокировка AVM |
| `FLAG_APA_BLOCK` | 0x10000 | Блокировка APA |
| `FLAG_REQ_CAMERA` | 0x20000 | Запрос камеры |

**Gesture Restrictions (Блокировки жестов):**

| Flag | Description |
|------|-------------|
| `FEATURE_DISABLE_PRIMARY_THREE_GUESTURE` | 3-пальцевый жест (основной) |
| `FEATURE_DISABLE_PRIMARY_FIVE_GUESTURE` | 5-пальцевый жест (основной) |
| `FEATURE_DISABLE_PRIMARY_UI_GUESTURE` | UI жесты (основной) |
| `FEATURE_DISABLE_PASSENGER_THREE_GUESTURE` | 3-пальцевый жест (пассажир) |
| `FEATURE_DISABLE_PASSENGER_FIVE_GUESTURE` | 5-пальцевый жест (пассажир) |
| `FEATURE_DISABLE_PASSENGER_UI_GUESTURE` | UI жесты (пассажир) |

### 5. Feature Calculation Algorithm

**Метод `calculateFeature()`:**

```java
private int calculateFeature() {
    int feature = 0;
    
    // No passenger? Add 0x2B
    if (!mHasPassenger) {
        feature = 0x2B;
    }
    
    // Either screen ON? Add 0x03
    if (mPrimaryScreenState == 1 || mSecondaryScreenState == 1) {
        feature |= 0x03;
    }
    
    // Reverse gear? Add 0x10
    if (isReverseGear()) {
        feature |= 0x10;
    }
    
    // Drive restrict active? Add 0x02
    if (isDriveRestrict()) {
        feature |= 0x02;
    }
    
    // Primary screen OFF? Add 0x14
    if (mPrimaryScreenState != 1) {
        feature |= 0x14;
    }
    
    // Secondary screen OFF? Add 0x28
    if (mSecondaryScreenState != 1) {
        feature |= 0x28;
    }
    
    // OR with all registered client features
    for (RecordData record : mRecordList) {
        feature |= record.mFeature;
    }
    
    return feature;
}
```

**Binary representation example:**
```
Feature = 0x2B = 00101011 (binary)
```

### 6. Client Registration (Регистрация клиентов)

**AIDL метод:**
```java
void observeClient(InteractClient client)
```

**Process:**
1. Link client's binder to death recipient
2. Create RecordData with process ID, feature flags, client, death recipient
3. Add to mRecordList
4. Recalculate total feature flags
5. Call `udpateFeature()` on all clients

**RecordData Structure:**
```java
class RecordData {
    int mProcess;              // Process ID
    int mFeature;              // Feature flags
    InteractClient mInteractClient;
    IBinder.DeathRecipient mDeathRecipient;
}
```

### 7. Feature Update Propagation

**Метод `udpateFeature(int feature)`:**

```java
public void udpateFeature(int feature) throws RemoteException {
    mFeature = feature;
    
    // Notify all registered clients
    for (RecordData record : mRecordList) {
        record.mInteractClient.onFeatureUpdate(feature);
    }
    
    // Log feature in binary
    String binary = InteractFeature.toBinaryString(feature);
    MLog.d("InteractTag", "Feature: " + binary);
}
```

---

## Property Callbacks

### Registered Properties

```java
mPropertySet = {
    0x66000007,  // Gear Position
    0x66000001   // Vehicle Speed
}
```

### Callback Implementation

```java
CarPropertyEventCallback mPropertyCallback = new CarPropertyEventCallback() {
    @Override
    public void onEvent(CarProperty carProperty) {
        int propId = carProperty.getPropertyId();
        
        if (propId == 0x66000007) { // Gear
            mDrivingInfoGear = getIntSignal(carProperty.getData());
            onFeatureUpdate();
        } else if (propId == 0x66000001) { // Speed
            float speed = getFloatSignal(carProperty.getData());
            mIsOverSpeed = (speed > 7.0f);
            onFeatureUpdate();
        }
    }
};
```

---

## Settings Integration

### Restrict Switch

**Key:** `com.mega.controller.restrict_switch`

**URI:** `Settings.Global.getUriFor("com.mega.controller.restrict_switch")`

**Values:**
- `"0"` = Restrictions OFF
- `"1"` = Restrictions ON

**ContentObserver:**
```java
mRestrictListener = new ContentObserver(mHandler) {
    @Override
    public void onChange(boolean selfChange) {
        mIsRestrictOn = isRestrictOn();
        onFeatureUpdate();
    }
};
```

---

## Passenger Configuration

**System Property:** `ro.ecu.config.PASSENGER_SCREEN`

**Values:**
- `"1"` = Passenger screen exists
- `""` or other = No passenger screen

**Impact:**
- If no passenger screen: `feature = 0x2B`
- If passenger screen: `feature = 0x00` (initially)

---

## Life Cycle Management

### Initialization

```java
public InteractSetterImpl(Context context, MegaCarProperty megaCarProperty) {
    mContext = context;
    mMegaCarProperty = megaCarProperty;
    
    // 1. Read initial gear and speed
    mDrivingInfoGear = getIntProp(0x66000007);
    mIsOverSpeed = (getFloatProp(0x66000001) > 7.0f);
    
    // 2. Check passenger configuration
    mHasPassenger = SystemProperties.get("ro.ecu.config.PASSENGER_SCREEN").equals("1");
    
    // 3. Register property callbacks
    mMegaCarProperty.registerCallback(mPropertyCallback, mPropertySet);
    
    // 4. Check restrict setting
    mIsRestrictOn = isRestrictOn();
    
    // 5. Register settings observer
    context.getContentResolver().registerContentObserver(
        KEY_SETTINGS_URI, true, mRestrictListener
    );
    
    // 6. Initialize Nexus service (screen management)
    initNexusService();
}
```

### Service Connection

```java
private void initNexusService() {
    mNexusmanager = MegaNexusManager.getNexusService();
    
    if (mNexusmanager != null) {
        // Get initial screen states
        mPrimaryScreenState = mNexusmanager.getScreenState(0);
        mSecondaryScreenState = mNexusmanager.getScreenState(1);
        
        // Register screen state listener
        mNexusmanager.registerScreenStateChangeListener(mScreenListener);
        
        // Register flying screen listener
        MegaActivityManager.getInstance()
            .registFlyingScreenStateListener(mFlyingScreenListener);
        
        // Calculate and send initial feature
        setStartFeature();
    } else {
        // Retry after delay (300ms)
        initServiceDelay();
    }
}
```

### Client Death Handling

```java
IBinder.DeathRecipient mDeathRecipient = () -> {
    // Remove dead client from list
    mRecordList.removeIf(record -> 
        record.mDeathRecipient == deathRecipient
    );
    
    // Recalculate features
    onFeatureUpdate();
};
```

---

## Logging

**Tag:** `"InteractTag"`

**Key Log Messages:**

```java
// Initialization
"init mHasPassenger=X mDrivingInfoGear=X mIsRestrictOn=X mIsOverSpeed=X"

// Feature calculation
"calculateFeature RecordData: <process> <binary>"

// Feature update
"Feature: <binary>"

// Nexus service
"mNexusmanager initialize"
"mNexusmanager delayInit"
```

---

## Use Cases

### 1. Driving Restriction (Вождение)

**Scenario:** Car moving > 25 km/h in Drive

```
Speed: 8.0 m/s (> 7.0)
Gear: D (not 0)
Restrict: ON

Result: isDriveRestrict() = true
        feature |= 0x02
        → Disable video, keyboard, complex UI
```

### 2. Reverse Parking

**Scenario:** Car in reverse gear

```
Gear: R (1)

Result: isReverseGear() = true
        feature |= 0x10
        → Enable backup camera, parking assist
```

### 3. Car Wash Mode

**Scenario:** User enables car wash mode

```
Client registers with FLAG_WASH_CAR (0x40)

Result: feature |= 0x40
        → Disable automatic wipers, auto-lock, etc.
```

### 4. Passenger UI Control

**Scenario:** No passenger screen configured

```
ro.ecu.config.PASSENGER_SCREEN = ""

Result: feature = 0x2B (initial)
        → Disable passenger-side controls
```

---

## Key Files

| File | Purpose |
|------|---------|
| `InteractSetterImpl.smali` | Main implementation (2189 lines) |
| `InteractSetter.smali` | AIDL interface definition |
| `InteractFeature.smali` | Feature flag utilities |
| `InteractManager.smali` | Client-side manager |
| `InteractClient.smali` | Client interface |

---

## Dependencies

```
InteractSetterImpl
├── MegaCarProperty (vehicle properties)
├── IMegaNexusManager (screen management)
├── MegaActivityManager (activity lifecycle)
├── Settings.Global (restrict switch)
├── ContentResolver (settings observer)
└── InteractClient (registered clients)
```

---

## Security & Safety

### Driving Restrictions

**Purpose:** Prevent driver distraction

**Enforcement:**
- Speed threshold: 7.0 m/s (~25 km/h)
- Gear-based: Only when not in Neutral/Park
- Configurable: Can be disabled via Settings.Global

### Death Recipient

**Purpose:** Clean up resources when client dies

**Process:**
1. Link binder to death recipient
2. On death: remove from list, recalculate features
3. Prevent memory leaks

---

## Performance Considerations

### Thread Safety

```java
private final ReentrantLock mUpdateLock = new ReentrantLock(true);
```

**Usage:**
- Lock acquired before modifying mRecordList
- Prevents concurrent modification exceptions

### Handler Usage

```java
mHandler = new Handler(Looper.getMainLooper());
```

**Purpose:** Post callbacks to main thread for UI updates

### Delayed Initialization

```java
private static final long DELAY_MS = 300; // 0x12c

mHandler.postDelayed(mInitServiceTask, DELAY_MS);
```

**Reason:** Wait for Nexus service to start

---

## Testing Points

### Unit Tests Needed

1. **Speed threshold:** Verify 7.0 m/s cutoff
2. **Gear detection:** Test all gear positions
3. **Screen states:** Test all combinations
4. **Feature calculation:** Verify bit operations
5. **Client registration:** Test add/remove
6. **Death recipient:** Test cleanup
7. **Settings change:** Test restrict toggle

### Integration Tests

1. **Property callbacks:** Verify real-time updates
2. **Screen listener:** Test state changes
3. **Feature propagation:** Verify client notifications

---

## Related Classes

- **MegaCarProperty** — Vehicle property access
- **InteractManager** — Client-side singleton
- **InteractClient** — Client callback interface
- **MegaNexusManager** — Screen/window management
- **MegaActivityManager** — Activity lifecycle

---

## Summary

**InteractSetterImpl** — это центральный компонент системы безопасности UI, который:

1. **Мониторит** состояние автомобиля (скорость, передача)
2. **Управляет** блокировками UI на основе условий
3. **Регистрирует** клиентов с флагами функций
4. **Распространяет** обновления флагов между клиентами
5. **Интегрируется** с системными настройками и сервисами

**Критично для:** Безопасности вождения, управления функциями автомобиля, координации между приложениями.
