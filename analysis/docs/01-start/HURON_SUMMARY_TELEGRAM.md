# 🚗 HuronCarController — Краткий обзор

## Что это?
**HuronCarController** (`com.mega.controller`) — главное UI-приложение для управления автомобилем. Содержит **35 контроллеров**, которые через центральный класс `MegaCarProperty` управляют всеми системами машины.

---

## 📦 Архитектура

```
HuronCarController (UI приложение)
    ↓
MegaCarProperty (Singleton-посредник)
    ↓
HuronCarService (Сервис автомобиля)
    ↓
CAN-шина (Физическое подключение)
```

---

## 🔧 35 Контроллеров

| # | Контроллер | Назначение |
|---|------------|------------|
| 1 | `ApaClientImpl` | Автопарковщик |
| 2 | `AtmoController` | Климат/атмосфера |
| 3 | `AudioController` | Аудиосистема |
| 4 | `BackLightController` | Подсветка |
| 5 | `BtPhoneController` | Bluetooth телефон |
| 6 | `CalibController` | Калибровка систем |
| 7 | `CarbodyController` | Кузов (спойлеры, зеркала) |
| 8 | `ChildrenForgetController` | Защита от детей |
| 9 | `CustomInputEventController` | Кастомные жесты |
| 10 | `DataCollectorController` | Сбор данных |
| 11 | `DataTrackController` | Трекинг данных |
| 12 | `DayNightController` | День/ночь режим |
| 13 | `ECallController` | Экстренный вызов |
| 14 | `GestureController` | Жесты |
| 15 | `InsulationController` | Изоляция |
| 16 | `LiveDetectionController` | Детекция жизни |
| 17 | `NotifyController` | Уведомления |
| 18 | `OtaController` | OTA обновления |
| 19 | `PrivacyAuthController` | Приватность |
| 20 | `RemoteLiveController` | Удалённый доступ |
| 21 | `RestrictedController` | Ограничения |
| 22 | `RmuMsgController` | Сообщения RMU |
| 23 | `SceneModeController` | Сценарии |
| 24 | `ScreenOffController` | Выключение экрана |
| 25 | `SecondBluetoothController` | Второй Bluetooth |
| 26 | `SentryController` | Режим охраны |
| 27 | `SettingsController` | Настройки |
| 28 | `TimeController` | Время |
| 29 | `TrailerController` | Прицеп |
| 30 | `TtsController` | TTS озвучка |
| 31 | `VehicleHealthController` | Диагностика |
| 32 | `VitalSignsDetectController` | Жизненные показатели |
| 33 | `WarningNoticeController` | Предупреждения |
| 34 | `WiperController` | Стеклоочистители |
| 35 | `XCallController` | Дополнительные вызовы |

---

## 🎯 MegaCarProperty — Центральный API

**Паттерн:** Singleton с авто-переподключением

**Возможности:**
- ✅ Типобезопасный API: `getIntProp()`, `getFloatProp()`, `getProperty()`
- ✅ Событийная модель: `CarPropertyEventCallback`
- ✅ Кэширование конфигураций
- ✅ Rate limiting для событий
- ✅ 300+ property ID для всех систем авто

**Пример использования:**
```java
// Получить скорость (Property ID: 0x66000001)
float speed = MegaCarProperty.getInstance().getFloatProp(0x66000001);

// Получить передачу (Property ID: 0x66000007)
int gear = MegaCarProperty.getInstance().getIntProp(0x66000007);

// Подписаться на события
MegaCarProperty.getInstance().registerCallback(callback, propertySet);
```

---

## 🛡️ InteractSetterImpl — Система безопасности UI

**Назначение:** Блокировка опасных функций во время вождения

**Логика:**
```java
Блокировка активна если:
├── restrict_switch = 1 (в настройках)
├── скорость > 7.0 м/с (~25 км/ч)
└── передача != N/P (машина не в нейтрали)
```

**Feature flags (битовые):**
- `0x02` — Блокировка при вождении
- `0x10` — Задняя передача (камера)
- `0x40` — Мойка авто
- `0x400` — Световое шоу
- `0x8000` — Блокировка AVM (камеры 360°)
- `0x10000` — Блокировка автопарковщика

---

## 📊 Состояние экранов

**Два экрана:**
- **Primary** (ID=0) — водительский
- **Secondary** (ID=1) — пассажирский (если есть)

**System property:** `ro.ecu.config.PASSENGER_SCREEN`
- `"1"` — пассажирский экран есть
- `""` — нет пассажирского экрана

---

## 🔍 Ключевые файлы

| Файл | Описание |
|------|----------|
| `HuronCarController.apk` | Основное приложение |
| `MegaCarProperty.smali` | Посредник для доступа к свойствам |
| `InteractSetterImpl.smali` | Система блокировок UI |
| `InteractFeature.smali` | Битовые флаги функций |
| `MegaControllerService.smali` | Сервис с 35 контроллерами |

---

## 📝 Документация

- [`HURON_CAR_CONTROLLER_ANALYSIS.md`](analysis/docs/HURON_CAR_CONTROLLER_ANALYSIS.md) — Полный анализ 35 контроллеров
- [`MEGA_CAR_PROPERTY_ANALYSIS.md`](analysis/docs/MEGA_CAR_PROPERTY_ANALYSIS.md) — Детальный разбор MegaCarProperty
- [`INTERACT_SETTER_IMPL_ANALYSIS.md`](analysis/docs/INTERACT_SETTER_IMPL_ANALYSIS.md) — Система безопасности UI

---

## 💡 Для разработчиков

**Хотите добавить свою функцию?**
1. Найдите нужный контроллер в `MegaControllerService`
2. Используйте `MegaCarProperty` для доступа к свойствам
3. Зарегистрируйтесь в `InteractSetter` с вашим feature flag
4. Обрабатывайте события через `CarPropertyEventCallback`

**Пример для спойлера:**
```java
// CarbodyController уже управляет спойлером
// Property ID для спойлера: найти в MegaCarProperty
MegaCarProperty.getInstance().setIntProp(PROPERTY_ID, SPOILER_OPEN);
```

---

**📍 Итого:** HuronCarController — это комплексная система управления автомобилем с 35 контроллерами, типобезопасным API и встроенной системой безопасности вождения.
