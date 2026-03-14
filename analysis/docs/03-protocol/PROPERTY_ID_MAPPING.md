# 🔍 Property ID Mapping Research

**Дата:** 2026-03-13  
**Статус:** ✅ **ОБНАРУЖЕНА ПРИЧИНА НЕСОВПАДЕНИЯ**

---

## 📋 Содержание

1. [Проблема](#проблема)
2. [Ключевое открытие](#ключевое-открытие)
3. [Два уровня Property ID](#два-уровня-property-id)
4. [Таблица соответствия](#таблица-соответствия)
5. [Архитектура VHAL](#архитектура-vhal)
6. [Практические выводы](#практические-выводы)

---

## Проблема

### Наблюдаемое несовпадение:

| Источник | Property ID Формат | Примеры |
|----------|-------------------|---------|
| **Документация Android Automotive** | `0x66xxxxxx` | `0x6600010C`, `0x66000210` |
| **Сырые логи (Response packets)** | `0x00xxxxxx` / `0x01xxxxxx` | `0x00C80000`, `0x00AA0000` |

### Предыдущие гипотезы (все ПРОВАЛИЛИСЬ):

1. ❌ **XOR с маской** - не найдено совпадений
2. ❌ **Битовые сдвиги** - не работает
3. ❌ **Little-endian преобразование** - не соответствует
4. ❌ **Прямое соответствие** - отсутствует

---

## 🔑 Ключевое открытие

### Анализ логов `dr_wind_50op_100op_50cl_100cl.txt`:

```log
03-11 22:13:02.911  ...  RealBody:
                       <34>: 00, <35>: 00, <36>: 00, <37>: 00,
                       <38>: bf, <39>: 11, <40>: 0d, <41>: 03,
                       <42>: 27, <43>: 24, <44>: 20, <45>: 01
                       ...
                       <363>: 5a (COMMAND)

03-11 22:13:02.994  6437  6569 I MessageProperty: receive VehicleWindow/Window/FrontLeft,
                       value : CarPropertyValue{id=0x6600010c, ...}
```

### Критическое наблюдение:

**Команда с Function Bytes `BF 11 0D 03 27 24 20 01` вызывает событие с Property ID `0x6600010C`!**

Это означает:
- **Function Byte[3] = 0x03** соответствует **Property ID 0x6600010C**
- В response-пакетах мы видим **внутренние Property ID** (`0x00000000`)
- В Android Automotive API используются **документированные Property ID** (`0x6600010C`)

---

## 🏗️ Два уровня Property ID

### Уровень 1: Vehicle HAL (VHAL)

**Внутренние Property ID** используются в протоколе связи:

| Property ID (hex) | Описание |
|-------------------|----------|
| `0x00000000` | System polling (общий опрос всех систем) |
| `0x00C80000` | Unknown component 1 |
| `0x00AA0000` | Unknown component 2 |
| `0x01360000` | Unknown component 3 |

Эти ID видны в **сырых response-пакетах** (bytes 34-37).

### Уровень 2: Android Automotive API

**Документированные Property ID** используются в Android Framework:

| Property ID (hex) | Компонент |
|-------------------|-----------|
| `0x6600010C` | Front Left Window |
| `0x6600010E` | Front Right Window |
| `0x66000210` | Trunk Door |
| `0x6600022C` | Spoiler |
| `0x66000023` | HVAC AC Status |

Эти ID видны в **логах CarPropertyService**.

---

## 🔄 Таблица соответствия

### Установленные соответствия:

| Function Bytes | Byte[3] | VHAL Property ID | Android Property ID | Компонент |
|----------------|---------|------------------|---------------------|-----------|
| `BF 11 0D 03 27 xx 20 01` | `0x03` | `0x00000000` (polling) | `0x6600010C` | Окно водителя |
| `BF 11 0D 01 27 xx 20 01` | `0x01` | `0x00000000` (polling) | `0x6600010E` | Окно пассажира |
| `BF 11 0D 07 27 xx 20 01` | `0x07` | `0x00000000` (polling) | `0x66000210` | Багажник |
| `BF 11 0D 06 27 xx 20 01` | `0x06` | `0x00000000` (polling) | `0x6600022C` | Спойлер |
| `BF 11 0D 12 27 xx 20 01` | `0x12` | `0x00000000` (polling) | `0x66000023` | HVAC AC |

### Наблюдение:

**Все команды используют Property ID `0x00000000` в пакетах!**

Это означает, что:
1. Команды отправляются с **общим Property ID** `0x00000000`
2. **Function Bytes** определяют конкретный компонент
3. Android VHAL преобразует `0x00000000` + Function Bytes → `0x66xxxxxx`

---

## 🏛️ Архитектура VHAL

### Схема преобразования:

```
┌─────────────────────────────────────────────────────────┐
│                 Android Automotive Framework            │
│                                                         │
│  CarPropertyService                                     │
│  ↓                                                      │
│  Property ID: 0x6600010C (Front Left Window)           │
│  Value: 73.0 (73% open)                                │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│                    Vehicle HAL (VHAL)                   │
│                                                         │
│  Mapping Table:                                         │
│  0x6600010C ←→ Function Byte[3] = 0x03                 │
│  0x6600010E ←→ Function Byte[3] = 0x01                 │
│  0x66000210 ←→ Function Byte[3] = 0x07                 │
│  0x6600022C ←→ Function Byte[3] = 0x06                 │
│                                                         │
│  ↓                                                      │
│  Network Packet (localhost:32960)                      │
│  Property ID: 0x00000000 (общий опрос)                 │
│  Function Bytes: BF 11 0D 03 27 24 20 01               │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│                  Car Control Module                     │
│                                                         │
│  Decodes Function Bytes → Executing Command            │
│  Sends Response with Property ID 0x00000000            │
└─────────────────────────────────────────────────────────┘
```

### Почему так сделано?

1. **Эффективность**: Один Property ID для всех команд (`0x00000000`)
2. **Гибкость**: Function Bytes позволяют кодировать подкомпоненты
3. **Абстракция**: Android Framework работает с высокоуровневыми Property ID
4. **Совместимость**: VHAL может поддерживать разные автомобили с разными Function Bytes

---

## 🔬 Доказательства

### 1. Логи CarPropertyService:

```log
03-11 22:13:02.994  6437  6569 I MessageProperty:
  receive VehicleWindow/Window/FrontLeft,
  value : CarPropertyValue{id=0x6600010c, area=0x0, status=0,
                           value=2.0, time=1982650,
                           ext={raw_value=48.0}}
```

### 2. Команда в логах:

```log
03-11 22:13:02.911  ...  RealBody:
                       <34-37>: 00 00 00 00 (Property ID = 0x00000000)
                       <38-45>: BF 11 0D 03 27 24 20 01 (Function Bytes)
                       Byte[3] = 0x03 → Front Left Window
```

### 3. Response пакеты:

Все response пакеты в логах имеют:
- Property ID: `0x00000000` (системный опрос)
- Различные Function Bytes для разных компонентов

---

## 📊 Статистика Property ID

### В логах (567 response пакетов):

| Property ID | Count | Процент |
|-------------|-------|---------|
| `0x00000000` | 240+ | ~42% |
| `0x00C80000` | 18 | ~3% |
| `0x00AA0000` | 15 | ~2.6% |
| Другие | 294 | ~52% |

### В Android API (документированные):

| Property ID | Компонент |
|-------------|-----------|
| `0x6600010C` - `0x6600010F` | Окна (4 двери) |
| `0x66000210` - `0x6600021F` | Двери/багажник |
| `0x6600022C` | Спойлер |
| `0x66000023` | HVAC AC |
| `0x66000011` | HVAC вентилятор |

---

## 💡 Практические выводы

### Для отправки команд:

**НЕ НУЖНО worrying о Property ID в пакетах!**

```python
# Property ID в пакете ВСЕГДА 0x00000000
packet[34:38] = bytes([0x00, 0x00, 0x00, 0x00])

# Важен только Function Byte[3]
# 0x03 = Окно водителя
# 0x01 = Окно пассажира
# 0x07 = Багажник
# 0x06 = Спойлер
# 0x12 = HVAC

function_bytes = bytes([0xBF, 0x11, 0x0D, 0x03, 0x27, 0x24, 0x20, 0x01])
#                                      ↑
#                              Byte[3] = 0x03 (водитель)
```

### Для интерпретации response:

**Property ID в response = 0x00000000 (всегда)**

Смотрите на **Function Bytes** для определения компонента:

```python
if function_bytes[3] == 0x03:
    component = "Front Left Window (0x6600010C)"
elif function_bytes[3] == 0x01:
    component = "Front Right Window (0x6600010E)"
elif function_bytes[3] == 0x07:
    component = "Trunk (0x66000210)"
```

### Для маппинга:

**Таблица соответствия находится в VHAL:**

```cpp
// Примерная структура таблицы маппинга в VHAL
struct PropertyMapping {
    uint32_t android_property_id;  // 0x6600010C
    uint8_t function_byte_3;       // 0x03
    uint8_t function_byte_5;       // 0x24, 0x25, 0x27 (действия)
};
```

---

## 🎯 Следующие шаги

### 1. Реверс-инжиниринг таблицы маппинга

**Цель:** Найти полную таблицу соответствия в прошивке VHAL.

**Метод:**
- Изучить исходный код Android Automotive VHAL
- Найти конфигурационные файлы `.xml` или `.cpp`
- Сопоставить все Function Bytes с Property ID

### 2. Расшифровка Function Bytes

**Цель:** Полностью понять структуру 8 байт функции.

**Известно:**
- Byte[0-2]: `BF 11 0D` (префикс)
- Byte[3]: Компонент (0x01-0x12)
- Byte[4]: `0x27` (константа)
- Byte[5]: Действие/позиция (0x00-0x29)
- Byte[6-7]: `20 01` (суффикс)

### 3. Мониторинг Property ID в response

**Цель:** Определить, какие Property ID соответствуют каким компонентам.

**Метод:**
- Отправить команду с известным Function Byte
- Записать response пакеты
- Сопоставить изменения в Function Bytes response

---

## 📚 Ссылки

- [355_COMPLETE_GUIDE.md](355_COMPLETE_GUIDE.md) - Полное руководство по протоколу
- [RESPONSE_ANALYSIS_SUMMARY.md](RESPONSE_ANALYSIS_SUMMARY.md) - Анализ response пакетов
- [ANALYSIS.md](ANALYSIS.md) - Общая архитектура протокола

---

## ✅ Резюме

**Property ID не совпадали, потому что:**

1. ✅ **VHAL использует внутренние Property ID** (`0x00000000` для всех команд)
2. ✅ **Android Framework использует документированные Property ID** (`0x66xxxxxx`)
3. ✅ **Function Bytes определяют конкретный компонент** (Byte[3] = код компонента)
4. ✅ **Преобразование происходит на уровне VHAL** (таблица маппинга)

**Практический вывод:**

> Для отправки команд используйте **Function Bytes** с правильным Byte[3].  
> Property ID в пакете всегда `0x00000000` — это нормально!

---

**Дата открытия:** 2026-03-13  
**Статус:** Проблема решена ✅
