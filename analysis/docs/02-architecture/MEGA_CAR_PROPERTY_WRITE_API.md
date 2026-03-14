# MegaCarProperty — Методы записи (Write API)

## 🚨 ВНИМАНИЕ: НЕДОСТУПНО ДЛЯ СТОРОННИХ ПРИЛОЖЕНИЙ

**MegaCarProperty** — внутренний класс системного приложения `com.mega.controller`.

**Почему недоступен:**
- Требует `android.uid.system` (системная подпись)
- Использует `android.car.hardware.CarPropertyManager` (системный API)
- Вызывает AIDL сервис `ICarProperty` (системный сервис)

**Для стороннего приложения:**
- ❌ Нет доступа к `MegaCarProperty`
- ❌ Нет доступа к `CarPropertyManager`
- ❌ Нет доступа к `ICarProperty`

**Единственный рабочий способ:** GB32960 Protocol через сокет (localhost:32960)

---

## Обзор (для понимания архитектуры)

**MegaCarProperty** предоставляет типобезопасный API для записи свойств автомобиля через `CarPropertyManager`.

---

## 📋 Публичные методы записи

### 1. setIntProp(propertyId, value)

**Назначение:** Записать целочисленное свойство

**Сигнатура:**
```java
public void setIntProp(int propertyId, int value)
```

**Параметры:**
- `propertyId` — ID свойства (например, 0x660000c3 для спойлера)
- `value` — Значение (например, 1=открыть, 2=закрыть)

**Исключения:**
- `CarStateErrorException` — если сервис автомобиля недоступен

**Пример:**
```java
// Открыть спойлер
MegaCarProperty.getInstance().setIntProp(0x660000c3, 1);

// Закрыть спойлер
MegaCarProperty.getInstance().setIntProp(0x660000c3, 2);
```

**Реализация:**
```java
.method public setIntProp(II)V
    .locals 2

    :try_start_0
    invoke-virtual {p0}, Lmega/car/MegaCarProperty;->getCarPropertyManager()Lmega/car/hardware/property/CarPropertyManager;

    move-result-object v0

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    # Вызов CarPropertyManager.setIntProp(propertyId, areaId=0, value)
    invoke-virtual {v0, p1, v1, p2}, Lmega/car/hardware/property/CarPropertyManager;->setIntProp(III)V
    :try_end_0
    .catch Lmega/car/utils/CarStateErrorException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1
    invoke-virtual {p1}, Lmega/car/utils/CarStateErrorException;->printStackTrace()V

    :cond_0
    :goto_0
    return-void
.end method
```

---

### 2. setFloatProp(propertyId, value)

**Назначение:** Записать свойство с плавающей точкой

**Сигнатура:**
```java
public void setFloatProp(int propertyId, float value)
```

**Параметры:**
- `propertyId` — ID свойства
- `value` — Значение float

**Пример:**
```java
// Установить температуру климата (если property поддерживает float)
MegaCarProperty.getInstance().setFloatProp(0x66000010, 22.5f);
```

**Реализация:**
```java
.method public setFloatProp(IF)V
    :try_start_0
    invoke-virtual {p0}, Lmega/car/MegaCarProperty;->getCarPropertyManager()Lmega/car/hardware/property/CarPropertyManager;

    move-result-object v0

    if-eqz v0, :cond_0

    const/4 v1, 0x0

    # Вызов CarPropertyManager.setFloatProp(propertyId, areaId=0, value)
    invoke-virtual {v0, p1, v1, p2}, Lmega/car/hardware/property/CarPropertyManager;->setFloatProp(IIF)V
    :try_end_0
    .catch Lmega/car/utils/CarStateErrorException; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void
.end method
```

---

### 3. setRawProp(CarPropertyValue)

**Назначение:** Записать свойство напрямую через CarPropertyValue

**Сигнатура:**
```java
public void setRawProp(CarPropertyValue<T> value)
```

**Параметры:**
- `value` — Объект CarPropertyValue с типизированным значением

**Пример:**
```java
// Создать CarPropertyValue для спойлера
CarPropertyValue<Integer> spoilerCmd = new CarPropertyValue<>(0x660000c3, 1);

// Записать напрямую
MegaCarProperty.getInstance().setRawProp(spoilerCmd);
```

**Реализация:**
```java
.method public setRawProp(Lmega/car/hardware/CarPropertyValue;)V
    :try_start_0
    invoke-virtual {p0}, Lmega/car/MegaCarProperty;->getCarPropertyManager()Lmega/car/hardware/property/CarPropertyManager;

    move-result-object v0

    if-eqz v0, :cond_0

    # Прямой вызов setPropertyRaw
    invoke-virtual {v0, p1}, Lmega/car/hardware/property/CarPropertyManager;->setPropertyRaw(Lmega/car/hardware/CarPropertyValue;)V
    :try_end_0
    .catch Lmega/car/utils/CarStateErrorException; {:try_start_0 .. :try_end_0} :catch_0

    :cond_0
    return-void
.end method
```

---

## 🔧 CarPropertyManager — низкоуровневый API

### setIntProp(propertyId, areaId, value)

```java
public void setIntProp(int propertyId, int areaId, int value)
    throws CarStateErrorException
```

**Параметры:**
- `propertyId` — ID свойства
- `areaId` — ID области (обычно 0 для глобальных свойств)
- `value` — Значение

**Реализация:**
```java
.method public setIntProp(III)V
    const-class v0, Ljava/lang/Integer;
    invoke-static {p3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object p3

    # Вызов универсального setProperty
    invoke-virtual {p0, v0, p1, p2, p3}, Lmega/car/hardware/property/CarPropertyManager;->setProperty(Ljava/lang/Class;IILjava/lang/Object;)V

    return-void
.end method
```

---

### setFloatProp(propertyId, areaId, value)

```java
public void setFloatProp(int propertyId, int areaId, float value)
    throws CarStateErrorException
```

**Реализация:**
```java
.method public setFloatProp(IIF)V
    const-class v0, Ljava/lang/Float;
    invoke-static {p3}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object p3

    invoke-virtual {p0, v0, p1, p2, p3}, Lmega/car/hardware/property/CarPropertyManager;->setProperty(Ljava/lang/Class;IILjava/lang/Object;)V

    return-void
.end method
```

---

### setProperty(Class, propertyId, areaId, value)

**Назначение:** Универсальный метод записи для любого типа

**Сигнатура:**
```java
public <E> void setProperty(Class<E> clazz, int propertyId, int areaId, E value)
    throws CarStateErrorException
```

**Реализация:**
```java
.method public setProperty(Ljava/lang/Class;IILjava/lang/Object;)V
    :try_start_0
    # Получить ICarProperty (AIDL интерфейс)
    iget-object p1, p0, Lmega/car/hardware/property/CarPropertyManager;->mService:Lmega/car/hardware/property/ICarProperty;

    # Создать CarPropertyValue
    new-instance v0, Lmega/car/hardware/CarPropertyValue;
    invoke-direct {v0, p2, p3, p4}, Lmega/car/hardware/CarPropertyValue;-><init>(IILjava/lang/Object;)V

    # Вызов AIDL метода
    invoke-interface {p1, v0}, Lmega/car/hardware/property/ICarProperty;->setProperty(Lmega/car/hardware/CarPropertyValue;)V
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1
    invoke-virtual {p0, p1}, Lmega/car/hardware/property/CarPropertyManager;->handleRemoteExceptionFromCarService(Landroid/os/RemoteException;)V

    :goto_0
    return-void
.end method
```

---

### setPropertyRaw(CarPropertyValue)

**Назначение:** Записать CarPropertyValue напрямую

**Сигнатура:**
```java
public void setPropertyRaw(CarPropertyValue value)
    throws CarStateErrorException
```

**Реализация:**
```java
.method public setPropertyRaw(Lmega/car/hardware/CarPropertyValue;)V
    :try_start_0
    iget-object v0, p0, Lmega/car/hardware/property/CarPropertyManager;->mService:Lmega/car/hardware/property/ICarProperty;

    invoke-interface {v0, p1}, Lmega/car/hardware/property/ICarProperty;->setProperty(Lmega/car/hardware/CarPropertyValue;)V
    :try_end_0
    .catch Landroid/os/RemoteException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1
    invoke-virtual {p0, p1}, Lmega/car/hardware/property/CarPropertyManager;->handleRemoteExceptionFromCarService(Landroid/os/RemoteException;)V

    :goto_0
    return-void
.end method
```

---

## 🏗️ CarPropertyValue — Класс значения

### Конструкторы

```java
// Полный конструктор
public CarPropertyValue(int propertyId, int areaId, int status, long timestamp, Object value)

// Стандартный (status=0, timestamp=now)
public CarPropertyValue(int propertyId, int areaId, Object value)

// Минимальный (areaId=0, status=0, timestamp=now)
public CarPropertyValue(int propertyId, Object value)
```

**Параметры:**
- `propertyId` — ID свойства
- `areaId` — ID области (0 для глобальных)
- `status` — Статус (0=available, 1=unavailable, 2=error, 3=no_valid)
- `timestamp` — Временная метка (SystemClock.uptimeMillis())
- `value` — Значение (Integer, Float, Boolean, String, byte[], etc.)

### Поля

```java
private final int mPropertyId;      // ID свойства
private final int mAreaId;          // ID области
private int mStatus;                // Статус
private final long mTimestamp;      // Временная метка
private final Object mValue;        // Значение
private boolean mRelative;          // Относительное изменение
private Object mExtension;          // Расширенные данные
```

### Методы доступа

```java
int getPropertyId()          // Получить ID свойства
int getAreaId()              // Получить ID области
int getStatus()              // Получить статус
long getTimestamp()          // Получить временную метку
Object getValue()            // Получить значение
boolean getRelative()        // Получить флаг относительности
Object getExtension()        // Получить расширенные данные
```

---

## ⚠️ Обработка ошибок

### CarStateErrorException

**Назначение:** Исключение при недоступности сервиса автомобиля

**Иерархия:**
```
CarStateErrorException extends Exception
```

**Когда возникает:**
- Сервис автомобиля не подключён
- AIDL интерфейс недоступен
- Ошибка связи с CarPropertyManager

**Обработка в MegaCarProperty:**
```java
try {
    getCarPropertyManager().setIntProp(propertyId, 0, value);
} catch (CarStateErrorException e) {
    e.printStackTrace();
    // Логирование ошибки, но не падение приложения
}
```

---

## 🔄 Асинхронность

### Важные замечания

1. **Методы записи блокирующие** — выполняются в вызывающем потоке
2. **NetworkOnMainThreadException** — может возникнуть при вызове в UI потоке
3. **Рекомендация** — вызывать в background потоке (AsyncTask, Executor, Thread)

### Пример безопасного вызова

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
Handler handler = new Handler(Looper.getMainLooper());

executor.execute(() -> {
    try {
        // Запись в background потоке
        MegaCarProperty.getInstance().setIntProp(0x660000c3, 1);
        
        handler.post(() -> {
            // Обновление UI в main потоке
            updateSpoilerButtonState(true);
        });
    } catch (CarStateErrorException e) {
        handler.post(() -> {
            showError("Failed to control spoiler");
        });
    }
});
```

---

## 📊 Сравнение методов чтения и записи

| Аспект | Чтение | Запись |
|--------|--------|--------|
| Методы | `getIntProp()`, `getFloatProp()`, `getProperty()` | `setIntProp()`, `setFloatProp()`, `setRawProp()` |
| Возврат | Значение | void |
| Исключения | CarStateErrorException | CarStateErrorException |
| Асинхронность | Может быть в UI потоке | Только background поток |
| Callback | Нет | Нет (но можно подписаться на изменения) |

---

## 💡 Примеры использования

### 1. Управление спойлером

```java
public class SpoilerController {
    private final MegaCarProperty carProperty = MegaCarProperty.getInstance();
    private boolean isOpening = false;
    
    public void toggleSpoiler() {
        if (isOpening) return;
        
        int position = carProperty.getIntProp(0x660000c4);
        int movement = carProperty.getIntProp(0x660000c5);
        
        // Если спойлер открыт (position=101) или движется (movement=1)
        if (position == 101 || movement == 1) {
            // Закрыть
            carProperty.setIntProp(0x660000c3, 2);
            isOpening = false;
        } else {
            // Открыть
            carProperty.setIntProp(0x660000c3, 1);
            isOpening = true;
        }
    }
    
    public void openSpoiler() {
        carProperty.setIntProp(0x660000c3, 1);
        isOpening = true;
    }
    
    public void closeSpoiler() {
        carProperty.setIntProp(0x660000c3, 2);
        isOpening = false;
    }
}
```

### 2. Подписка на изменения после записи

```java
CarPropertyEventCallback callback = new CarPropertyEventCallback() {
    @Override
    public void onEvent(CarProperty carProperty) {
        int propId = carProperty.getPropertyId();
        
        if (propId == 0x660000c4) {
            // Получаем обновлённую позицию после записи команды
            int position = (Integer) carProperty.getValue();
            Log.d("Spoiler", "New position: " + position);
        }
    }
};

Set<Integer> properties = new HashSet<>();
properties.add(0x660000c4); // Позиция спойлера

MegaCarProperty.getInstance().registerCallback(callback, properties);

// Теперь записываем команду
MegaCarProperty.getInstance().setIntProp(0x660000c3, 1);
```

### 3. Использование setRawProp для сложных типов

```java
// Для строковых свойств
CarPropertyValue<String> stringProp = new CarPropertyValue<>(
    0x66000100,  // Property ID
    "Custom Value" // String value
);
MegaCarProperty.getInstance().setRawProp(stringProp);

// Для byte[] свойств (например, VIN)
byte[] vinData = new byte[]{0x56, 0x49, 0x4E, 0x31, 0x32, 0x33};
CarPropertyValue<byte[]> vinProp = new CarPropertyValue<>(
    0x66000200,
    vinData
);
MegaCarProperty.getInstance().setRawProp(vinProp);
```

---

## 🔐 Проверки безопасности

### 1. Доступность сервиса

```java
CarPropertyManager manager = getCarPropertyManager();
if (manager == null) {
    Log.e("CarProperty", "Manager not available");
    return;
}
```

### 2. Валидность Property ID

```java
// Некоторые Property ID могут быть недоступны в определённых режимах
if (!isPropertyAvailable(propertyId)) {
    Log.w("CarProperty", "Property " + propertyId + " not available");
    return;
}
```

### 3. InteractSetter блокировки

```java
// При вождении некоторые свойства могут быть заблокированы
if (InteractSetter.isDriveRestrict()) {
    // Блокировка опасных функций
    if (isDangerousProperty(propertyId)) {
        Log.w("CarProperty", "Property blocked during driving");
        return;
    }
}
```

---

## 📁 Ключевые файлы

| Файл | Описание |
|------|----------|
| `MegaCarProperty.smali` | Обёртка над CarPropertyManager (1036 строк) |
| `CarPropertyManager.smali` | Менеджер свойств (1760 строк) |
| `CarPropertyValue.smali` | Класс значения свойства (1280 строк) |
| `ICarProperty.smali` | AIDL интерфейс |
| `CarStateErrorException.smali` | Исключение |

---

## 📝 Summary

**API записи MegaCarProperty:**

1. **`setIntProp(propertyId, value)`** — для целочисленных свойств
2. **`setFloatProp(propertyId, value)`** — для float свойств
3. **`setRawProp(CarPropertyValue)`** — для сложных типов

**Важно:**
- Вызывать в **background потоке**
- Обрабатывать **CarStateErrorException**
- Подписываться на **события изменений** для обратной связи
- Проверять **доступность сервиса** перед записью

**Для спойлера:**
```java
MegaCarProperty.getInstance().setIntProp(0x660000c3, 1); // Открыть
MegaCarProperty.getInstance().setIntProp(0x660000c3, 2); // Закрыть
```
