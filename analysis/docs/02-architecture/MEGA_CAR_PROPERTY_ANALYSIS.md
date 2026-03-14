# MegaCarProperty - Детальный анализ

## 📋 Общее описание

**MegaCarProperty** (`mega.car.MegaCarProperty`) — это **центральный класс-посредник**, который предоставляет API для доступа ко всем свойствам автомобиля через CarPropertyManager.

Это **основной интерфейс**, через который HuronCarController взаимодействует с HuronCarService.

---

## 🎯 Назначение

### Роль в архитектуре

```
┌─────────────────────────────────────────────────────────────┐
│  HuronCarController (com.mega.controller)                  │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  MegaControllerService                                │ │
│  │  └── Контроллеры (35 штук)                           │ │
│  │       └── Используют MegaCarProperty                │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
                ┌───────────────────────┐
                │  MegaCarProperty      │  ← Посредник
                └───────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  HuronCarService (com.mega.car)                            │
│  ┌───────────────────────────────────────────────────────┐ │
│  │  MegaCar                                              │ │
│  │  └── CarPropertyManager                              │ │
│  │       └── ICarProperty (Binder)                     │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
                    CAN-шина автомобиля
```

### Ключевые функции

1. **Единая точка входа** для всех контроллеров
2. **Управление подключением** к CarService
3. **Кэширование свойств** для быстрого доступа
4. **Событийная модель** для изменений свойств
5. **Типобезопасный API** для разных типов данных

---

## 🔧 Архитектура класса

### Поля класса

```java
public class MegaCarProperty {
    // Константы
    public static final int CAR_PROP_UNKNOWN = -1;
    private static final boolean FORCE_RECONNECT = true;
    private static final String TAG = "MegaCarProperty";
    
    // Singleton
    private static volatile MegaCarProperty sInstance;
    private static final Handler sHandler;
    
    // Основные компоненты
    private CarPropertyManager mManager;        // Менеджер свойств
    private MegaCar mMegaCar;                   // Связь с CarService
    
    // Слушатели
    private OnCarServiceStatusChangeListener mOnCarServiceStatusChangeListener;
    private final MegaCar$CarServiceLifecycleListener mStatusChangeListener;
}
```

### Singleton Pattern

```java
// Инициализация в фоне
public static void initBackground() {
    sHandler.post(() -> initInner());
}

// Получение экземпляра
public static MegaCarProperty getInstance() {
    initInner();
    return sInstance;
}

// Внутренняя инициализация
private static synchronized void initInner() {
    if (sInstance == null) {
        synchronized (MegaCarProperty.class) {
            if (sInstance == null) {
                sInstance = new MegaCarProperty(
                    AppGlobals.getInitialApplication().getApplicationContext(),
                    true  // forceReconnect
                );
            }
        }
    }
}
```

---

## 🔌 Подключение к CarService

### Процесс инициализации

```java
private MegaCarProperty(Context context, boolean forceReconnect) {
    // 1. Создаём слушателя состояния
    mStatusChangeListener = (megaCar, connected) -> {
        if (connected) {
            Log.d(TAG, "CarService connected");
        } else {
            Log.d(TAG, "CarService disconnected");
            mManager = null;
            
            // Авто-переподключение
            if (forceReconnect) {
                ensureCarServiceConnected(context, sHandler);
            }
        }
        
        // Уведомление внешнего слушателя
        if (mOnCarServiceStatusChangeListener != null) {
            mOnCarServiceStatusChangeListener.onCarServiceStatusChanged(connected);
        }
    };
    
    // 2. Подключаемся к CarService
    ensureCarServiceConnected(context, sHandler);
}

private synchronized void ensureCarServiceConnected(
    Context context,
    Handler handler
) {
    // Создаёт MegaCar через статический метод
    mMegaCar = MegaCar.createCar(
        context,
        handler,
        -1,  // timeout (wait forever)
        mStatusChangeListener
    );
}
```

### MegaCar.createCar()

```java
// Из MegaCar.smali
public static MegaCar createCar(
    Context context,
    Handler handler,
    long timeout,
    CarServiceLifecycleListener listener
) {
    // 1. Проверяем наличие сервиса
    IBinder binder = ServiceManager.getService("com.mega.car.CarService");
    
    // 2. Если нет - ждём с polling
    if (binder == null) {
        return waitForCarService(context, handler, timeout, listener);
    }
    
    // 3. Создаём экземпляр
    return new MegaCar(context, ICar.Stub.asInterface(binder), listener, handler);
}
```

### Service Connection

```java
// Из MegaCar.smali
private final ServiceConnection mServiceConnectionListener = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = ICar.Stub.asInterface(service);
        mConnectionState = STATE_CONNECTED;
        mServiceBound = true;
        
        // Уведомляем слушателя
        mStatusChangeListener.onCarServiceConnected(this);
    }
    
    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
        mConnectionState = STATE_DISCONNECTED;
        mServiceBound = false;
        
        // Уведомляем слушателя
        mStatusChangeListener.onCarServiceDisconnected(this);
        
        // Попытка переподключения
        scheduleServiceReconnect();
    }
};

// Попытки переподключения
private void scheduleServiceReconnect() {
    mEventHandler.postDelayed(mConnectionRetryRunnable, 500);  // 500ms
}

private final Runnable mConnectionRetryRunnable = () -> {
    if (mConnectionRetryCount < 100) {  // MAX_RETRY
        mConnectionRetryCount++;
        bindToCarService();  // Пробуем снова
        
        if (mConnectionState != STATE_CONNECTED) {
            scheduleServiceReconnect();  // Ещё раз через 500ms
        }
    } else {
        // Превышено количество попыток
        mStatusChangeListener.onCarServiceConnectFailed(this);
    }
};
```

---

## 📊 API для работы со свойствами

### Получение CarPropertyManager

```java
public synchronized CarPropertyManager getCarPropertyManager() {
    if (mManager != null) {
        return mManager;
    }
    
    if (mMegaCar == null) {
        Log.w(TAG, "Null MegaCar");
        return null;
    }
    
    // Запрашиваем менеджер из MegaCar
    mManager = (CarPropertyManager) mMegaCar.getCarManager("property");
    
    if (mManager == null) {
        Log.w(TAG, "Null CarPropertyManager");
    }
    
    return mManager;
}
```

### Чтение свойств (Getters)

#### 1. getIntProp() - Целочисленные свойства

```java
public int getIntProp(int propertyId) {
    CarPropertyManager manager = getCarPropertyManager();
    
    if (manager != null) {
        return manager.getIntProp(propertyId, 0);  // areaId = 0
    }
    
    return -1;  // Error
}
```

**Пример использования:**
```java
// Скорость автомобиля (0x8000001)
int speed = MegaCarProperty.getInstance().getIntProp(0x8000001);

// Обороты двигателя (0x8000002)
int rpm = MegaCarProperty.getInstance().getIntProp(0x8000002);

// Позиция КПП (0x8000003)
int gear = MegaCarProperty.getInstance().getIntProp(0x8000003);
```

#### 2. getFloatProp() - Свойства с плавающей точкой

```java
public float getFloatProp(int propertyId) {
    try {
        CarPropertyManager manager = getCarPropertyManager();
        
        if (manager != null) {
            Float value = manager.getFloatProp(propertyId, 0);
            return value != null ? value.floatValue() : -1.0f;
        }
    } catch (CarStateErrorException e) {
        e.printStackTrace();
    }
    
    return -1.0f;  // Error
}
```

**Пример использования:**
```java
// Температура салона (0x2000001)
float temp = MegaCarProperty.getInstance().getFloatProp(0x2000001);

// Уровень заряда батареи (0x7000003)
float soc = MegaCarProperty.getInstance().getFloatProp(0x7000003);
```

#### 3. getProperty() - Типобезопасное получение

```java
public <E> CarPropertyValue<E> getProperty(Class<E> type, int propertyId) {
    CarPropertyManager manager = getCarPropertyManager();
    
    if (manager != null) {
        return manager.getProperty(type, propertyId, 0);
    }
    
    return null;
}
```

**Пример использования:**
```java
// Получение Boolean свойства
CarPropertyValue<Boolean> value = MegaCarProperty.getInstance()
    .getProperty(Boolean.class, 0x2000003);  // AC Auto Mode

if (value != null) {
    boolean isEnabled = value.getValue();
}
```

#### 4. getPropertyRaw() - Сырые данные

```java
public CarPropertyValue<?> getPropertyRaw(int propertyId) {
    try {
        CarPropertyManager manager = getCarPropertyManager();
        
        if (manager != null) {
            return manager.getPropertyRaw(propertyId, 0);
        }
    } catch (CarStateErrorException e) {
        e.printStackTrace();
    }
    
    return null;
}
```

#### 5. getNameById() - Получение имени свойства

```java
public String getNameById(int propertyId) {
    CarPropertyManager manager = getCarPropertyManager();
    
    if (manager != null) {
        CarPropertyConfig config = manager.getConfigById(propertyId);
        
        if (config != null) {
            return config.getName();
        }
    }
    
    // Fallback: шестнадцатеричное представление
    return "0x" + Integer.toHexString(propertyId);
}
```

**Пример:**
```java
String name = MegaCarProperty.getInstance().getNameById(0x2000003);
// Вернёт: "AC/AutoMode"
```

#### 6. getDefValueOfCarProperty() - Значение по умолчанию

```java
public Object getDefValueOfCarProperty(int propertyId) {
    CarPropertyManager manager = getCarPropertyManager();
    CarPropertyConfig config = manager.getConfigById(propertyId);
    
    if (config != null) {
        Class<?> type = config.getPropertyType();
        
        // Возвращаем дефолтное значение для типа
        if (type == Integer.class) return 0;
        if (type == Long.class) return 0L;
        if (type == Boolean.class) return false;
        if (type == Float.class) return 0.0f;
        if (type == int[].class) return new int[0];
        if (type == String.class) return "";
        if (type == byte[].class) return new byte[0];
        // ... и т.д.
    }
    
    return null;
}
```

---

### Запись свойств (Setters)

#### 1. setIntProp() - Запись целочисленных свойств

```java
public void setIntProp(int propertyId, int value) {
    try {
        CarPropertyManager manager = getCarPropertyManager();
        
        if (manager != null) {
            manager.setIntProp(propertyId, 0, value);  // areaId, value
        }
    } catch (CarStateErrorException e) {
        e.printStackTrace();
    }
}
```

**Пример использования:**
```java
// Включить Auto AC Mode (0x2000003)
MegaCarProperty.getInstance().setIntProp(0x2000003, 1);

// Установить температуру (0x2000010)
MegaCarProperty.getInstance().setIntProp(0x2000010, 22);  // 22°C
```

#### 2. setFloatProp() - Запись float свойств

```java
public void setFloatProp(int propertyId, float value) {
    try {
        CarPropertyManager manager = getCarPropertyManager();
        
        if (manager != null) {
            manager.setFloatProp(propertyId, 0, value);
        }
    } catch (CarStateErrorException e) {
        e.printStackTrace();
    }
}
```

#### 3. setRawProp() - Запись сырых данных

```java
public void setRawProp(CarPropertyValue<?> value) {
    try {
        CarPropertyManager manager = getCarPropertyManager();
        
        if (manager != null) {
            manager.setPropertyRaw(value);
        }
    } catch (CarStateErrorException e) {
        e.printStackTrace();
    }
}
```

---

## 📡 Событийная модель

### CarPropertyEventCallback

```java
// Интерфейс обратного вызова
public interface CarPropertyEventCallback {
    void onCarPropertyEvent(CarPropertyEvent event);
}
```

### CarPropertyEvent

```java
// Структура события
public class CarPropertyEvent {
    public int propertyId;      // ID свойства
    public int areaId;          // Зона (0 = все)
    public Object value;        // Новое значение
    public long timestamp;      // Время события
    public int status;          // Статус (0 = OK)
}
```

### Регистрация слушателя

```java
public synchronized void registerCallback(
    CarPropertyEventCallback callback,
    Set<Integer> propertyIds
) {
    if (propertyIds == null || propertyIds.isEmpty()) {
        return;
    }
    
    // Создаём SparseArray для rate limiting
    SparseArray<Float> rateLimits = new SparseArray<>(propertyIds.size());
    
    for (int propertyId : propertyIds) {
        rateLimits.put(propertyId, 0.0f);  // 0 = без ограничений
    }
    
    // Регистрируем в CarPropertyManager
    CarPropertyManager manager = getCarPropertyManager();
    if (manager != null) {
        manager.registerCallback(callback, rateLimits);
    }
}
```

### Пример использования

```java
// Создаём слушателя
CarPropertyEventCallback callback = new CarPropertyEventCallback() {
    @Override
    public void onCarPropertyEvent(CarPropertyEvent event) {
        switch (event.propertyId) {
            case 0x8000001:  // Vehicle Speed
                int speed = (Integer) event.value;
                updateSpeedUI(speed);
                break;
                
            case 0x2000001:  // Interior Temperature
                float temp = (Float) event.value;
                updateTempUI(temp);
                break;
                
            case 0x5000001:  // Driver Door
                boolean isOpen = (Boolean) event.value;
                updateDoorUI(isOpen);
                break;
        }
    }
};

// Подписка на свойства
Set<Integer> properties = new HashSet<>();
properties.add(0x8000001);  // Speed
properties.add(0x8000002);  // RPM
properties.add(0x2000001);  // Temperature
properties.add(0x5000001);  // Driver Door

MegaCarProperty.getInstance().registerCallback(callback, properties);
```

### Отписка

```java
public synchronized void unregisterCallback(
    CarPropertyEventCallback callback,
    Set<Integer> propertyIds
) {
    CarPropertyManager manager = getCarPropertyManager();
    
    if (manager != null) {
        manager.unregisterCallback(callback, propertyIds);
    }
}
```

---

## 🎛️ OnCarServiceStatusChangeListener

### Интерфейс

```java
public interface OnCarServiceStatusChangeListener {
    void onCarServiceStatusChanged(boolean connected);
}
```

### Использование

```java
MegaCarProperty property = MegaCarProperty.getInstance();

property.setOnCarServiceStatusChangeListener(new OnCarServiceStatusChangeListener() {
    @Override
    public void onCarServiceStatusChanged(boolean connected) {
        if (connected) {
            // CarService доступен - включаем функции
            enableAllFeatures();
        } else {
            // CarService недоступен - отключаем функции
            disableAllFeatures();
            showConnectionError();
        }
    }
});
```

---

## 📁 Структура файлов

### Связанные классы

```
mega.car/
├── MegaCar.smali                      # Основной класс подключения
├── MegaCarProperty.smali              # Посредник для свойств
├── MegaCarPropertyInitializer.smali   # Инициализатор
├── ICar.smali                         # Binder интерфейс
│   ├── ICar$Stub.smali
│   └── ICar$Stub$Proxy.smali
└── hardware/property/
    ├── CarPropertyManager.smali       # Менеджер свойств
    ├── CarPropertyEvent.smali         # Событие свойства
    ├── CarPropertyConfig.smali        # Конфигурация свойства
    ├── CarPropertyValue.smali         # Значение свойства
    ├── ICarProperty.smali             # Binder интерфейс
    ├── ICarPropertyEventListener.smali # Слушатель событий
    └── CarPropertyManager$CarPropertyEventCallback.smali
```

---

## 💡 Примеры использования в контроллерах

### 1. CarbodyController (Кузов)

```java
public class CarbodyController {
    private final MegaCarProperty mProperty;
    
    public CarbodyController() {
        mProperty = MegaCarProperty.getInstance();
    }
    
    // Проверить статус двери
    public boolean isDriverDoorOpen() {
        return mProperty.getIntProp(0x5000001) == 1;
    }
    
    // Заблокировать двери
    public void lockDoors() {
        mProperty.setIntProp(0x5000020, 1);  // Central Lock
    }
    
    // Разблокировать двери
    public void unlockDoors() {
        mProperty.setIntProp(0x5000020, 0);
    }
}
```

### 2. DayNightController (День/Ночь)

```java
public class DayNightController {
    private final MegaCarProperty mProperty;
    
    public boolean isNightMode() {
        // Проверяем режим освещения
        return mProperty.getIntProp(0x4000001) == 1;  // Headlight Auto
    }
    
    public void setNightMode(boolean isNight) {
        // Включаем/выключаем подсветку
        mProperty.setIntProp(0x4000011, isNight ? 80 : 50);  // Brightness
    }
}
```

### 3. VehicleHealthController (Диагностика)

```java
public class VehicleHealthController {
    private final MegaCarProperty mProperty;
    
    public List<DiagnosticCode> readDiagnostics() {
        List<DiagnosticCode> codes = new ArrayList<>();
        
        // Проверяем все системы
        checkSystem(0x6000005, "Engine", codes);  // Engine Overheat
        checkSystem(0x6000007, "Brake", codes);   // Brake System
        checkSystem(0x6000008, "Airbag", codes);  // Airbag
        checkSystem(0x6000009, "TPMS", codes);    // Tire Pressure
        
        return codes;
    }
    
    private void checkSystem(int propertyId, String system, List<DiagnosticCode> codes) {
        int status = mProperty.getIntProp(propertyId);
        
        if (status != 0) {
            codes.add(new DiagnosticCode(system, status));
        }
    }
}
```

### 4. SettingsController (Настройки)

```java
public class SettingsController {
    private final MegaCarProperty mProperty;
    
    // Сохранить настройку
    public void saveSetting(String key, int value) {
        int propertyId = getPropertyIdForSetting(key);
        mProperty.setIntProp(propertyId, value);
    }
    
    // Загрузить настройку
    public int loadSetting(String key) {
        int propertyId = getPropertyIdForSetting(key);
        return mProperty.getIntProp(propertyId);
    }
    
    private int getPropertyIdForSetting(String key) {
        // Маппинг имён на PropertyID
        switch (key) {
            case "ac_auto_mode": return 0x2000003;
            case "light_auto": return 0x4000001;
            case "window_lock": return 0x3000020;
            default: return -1;
        }
    }
}
```

---

## 🔍 Отладка и логирование

### Включение отладки

```java
// В MegaCarProperty.smali
private static final boolean DBG = true;  // Изменить на true

// Логирование подключения
private void logConnectionState(String state) {
    if (DBG) {
        Log.d(TAG, "Connection state: " + state);
        Log.d(TAG, "mMegaCar: " + mMegaCar);
        Log.d(TAG, "mManager: " + mManager);
    }
}
```

### Логирование свойств

```java
public void logProperty(int propertyId) {
    String name = getNameById(propertyId);
    Object value = getPropertyRaw(propertyId);
    
    Log.d(TAG, "Property: " + name + " (0x" + Integer.toHexString(propertyId) + ")");
    Log.d(TAG, "Value: " + value);
    Log.d(TAG, "Timestamp: " + (value != null ? ((CarPropertyValue)value).timestamp : "N/A"));
}
```

---

## ⚠️ Обработка ошибок

### CarStateErrorException

```java
// Исключение при недоступности свойства
public class CarStateErrorException extends Exception {
    public CarStateErrorException(String message) {
        super(message);
    }
    
    public CarStateErrorException(int errorCode) {
        super("Error code: " + errorCode);
    }
}
```

### Обработка в контроллерах

```java
public void safeSetProperty(int propertyId, int value) {
    try {
        MegaCarProperty.getInstance().setIntProp(propertyId, value);
    } catch (CarStateErrorException e) {
        Log.e(TAG, "Failed to set property 0x" + Integer.toHexString(propertyId), e);
        
        // Уведомить пользователя
        showPropertyUnavailable(propertyId);
    }
}
```

---

## 📊 Производительность

### Кэширование

```java
// CarPropertyManager кэширует конфигурации
private final ConcurrentHashMap<Integer, CarPropertyConfig> mConfigCache;

public CarPropertyConfig getConfigById(int propertyId) {
    // Проверяем кэш
    CarPropertyConfig config = mConfigCache.get(propertyId);
    
    if (config == null) {
        // Запрашиваем из сервиса
        config = mService.getConfigById(propertyId);
        
        if (config != null) {
            mConfigCache.put(propertyId, config);  // Кэшируем
        }
    }
    
    return config;
}
```

### Rate Limiting

```java
// При регистрации можно указать ограничения
SparseArray<Float> rateLimits = new SparseArray<>();

// Speed - 10 Hz
rateLimits.put(0x8000001, 10.0f);

// Temperature - 1 Hz
rateLimits.put(0x2000001, 1.0f);

// Door status - без ограничений (события)
rateLimits.put(0x5000001, 0.0f);

registerCallback(callback, rateLimits);
```

---

## 🔐 Безопасность

### Проверка разрешений

```java
// В CarPropertyManager
public void setIntProp(int propertyId, int areaId, int value) 
    throws CarStateErrorException {
    
    // Проверка разрешения
    if (!hasPermission(propertyId)) {
        throw new SecurityException(
            "Permission denied for property 0x" + Integer.toHexString(propertyId)
        );
    }
    
    // Проверка состояния автомобиля
    if (!isPropertyAvailable(propertyId)) {
        throw new CarStateErrorException(STATE_UNAVAILABLE);
    }
    
    // Запись свойства
    mService.setIntProp(propertyId, areaId, value);
}

private boolean hasPermission(int propertyId) {
    // Проверка по таблице разрешений
    int requiredPermission = getRequiredPermission(propertyId);
    return mContext.checkCallingPermission(requiredPermission) 
        == PackageManager.PERMISSION_GRANTED;
}
```

---

## 💡 Выводы

### MegaCarProperty — это:

1. **Единая точка входа** для доступа ко всем свойствам автомобиля
2. **Посредник** между контроллерами и CarService
3. **Типобезопасный API** для разных типов данных
4. **Событийная модель** для отслеживания изменений
5. **Авто-переподключение** при потере связи
6. **Кэширование** для производительности
7. **Rate limiting** для контроля частоты событий

### Ключевые методы:

| Метод | Назначение |
|-------|------------|
| `getInstance()` | Получение singleton экземпляра |
| `getIntProp(id)` | Чтение int свойства |
| `getFloatProp(id)` | Чтение float свойства |
| `getProperty(type, id)` | Типобезопасное чтение |
| `setIntProp(id, value)` | Запись int свойства |
| `setFloatProp(id, value)` | Запись float свойства |
| `registerCallback(callback, ids)` | Подписка на события |
| `getNameById(id)` | Получение имени свойства |
| `getDefValueOfCarProperty(id)` | Значение по умолчанию |

### Использование в MazdaControl:

```kotlin
// Аналог для Kotlin
object CarPropertyAccess {
    private val property by lazy { 
        MegaCarProperty.getInstance() 
    }
    
    fun getSpoilerPosition(): Int {
        return property.getIntProp(0x3000010)  // Sunroof Position
    }
    
    fun openSpoiler() {
        property.setIntProp(0x3000010, 100)  // 100% open
    }
    
    fun closeSpoiler() {
        property.setIntProp(0x3000010, 0)  // 0% closed
    }
}
```

---

**Дата анализа:** 2026-03-14
**Статус:** Analysis Complete
