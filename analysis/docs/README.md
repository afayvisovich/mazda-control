# 📚 Huron Car Control — Оглавление документации

## Основное

### [README.md](README.md)
Этот файл — навигационный указатель по всей документации.

---

## 🔥 Начать здесь

### [01-start/ANALYSIS.md](01-start/ANALYSIS.md) ⭐
**Первый документ для чтения:** Reverse engineering протокола, структура пакетов, Property ID, декодирование команд.

---

## 📖 Краткий старт

### [01-start/HURON_SUMMARY_TELEGRAM.md](01-start/HURON_SUMMARY_TELEGRAM.md)
Краткий обзор системы управления для отправки коллегам в чат.

### [01-start/355_COMPLETE_GUIDE.md](01-start/355_COMPLETE_GUIDE.md)
Полное руководство по протоколу: структура пакетов, таблицы команд, готовые инструменты.

---

## 🏗️ Архитектура

### [02-architecture/HURON_CAR_CONTROLLER_ANALYSIS.md](02-architecture/HURON_CAR_CONTROLLER_ANALYSIS.md)
Анализ UI-приложения: 35 контроллеров, архитектура, взаимодействие с MegaCarProperty.

### [02-architecture/MEGA_CONTROLLER_SERVICE_ANALYSIS.md](02-architecture/MEGA_CONTROLLER_SERVICE_ANALYSIS.md)
Центральный сервис: инициализация 28 контроллеров, AIDL интерфейсы, TLV-интеграция.

### [02-architecture/MEGA_CAR_PROPERTY_ANALYSIS.md](02-architecture/MEGA_CAR_PROPERTY_ANALYSIS.md)
Центральный API для доступа к свойствам автомобиля: singleton, методы чтения, события.

### [02-architecture/INTERACT_SETTER_IMPL_ANALYSIS.md](02-architecture/INTERACT_SETTER_IMPL_ANALYSIS.md)
Система блокировок UI на основе скорости, передачи и состояния экранов.

### [02-architecture/CARBODY_CONTROLLER_PACKAGE.md](02-architecture/CARBODY_CONTROLLER_PACKAGE.md)
Управление кузовом: Property ID спойлера, алгоритм работы, state machine.

### [02-architecture/MEGA_CAR_PROPERTY_WRITE_API.md](02-architecture/MEGA_CAR_PROPERTY_WRITE_API.md)
⚠️ **Справочно:** Методы записи MegaCarProperty (НЕДОСТУПНЫ для сторонних приложений). Только для понимания архитектуры.

---

## 🔌 Протокол и обратная инженерия

### [03-protocol/ANALYSIS.md](03-protocol/ANALYSIS.md)
Reverse engineering протокола: структура пакетов, Property ID, декодирование команд.

### [03-protocol/reverse-engineering.md](03-protocol/reverse-engineering.md)
Полный разбор протокола GB32960: заголовок, RealBody, Function Bytes, контрольные суммы.

### [03-protocol/PROPERTY_ID_MAPPING.md](03-protocol/PROPERTY_ID_MAPPING.md)
Соответствие Property ID между Android Automotive и сырыми логами.

### [03-protocol/VALUE_ENCODING_ANALYSIS.md](03-protocol/VALUE_ENCODING_ANALYSIS.md)
Декодирование Function Bytes: Component ID, Action Code, кодирование значений.

### [03-protocol/huron-car-service-capabilities.md](03-protocol/huron-car-service-capabilities.md)
Комплексный анализ HuronCarService: процессоры, CAN-шина, логирование.

---

## 📎 Дополнительные материалы

### [SUMMARY_1303.md](SUMMARY_1303.md) ⭐
**Итоги исследования (13-14 марта):** Критическое обновление — CarProperty API недоступен, единственный способ GB32960 Protocol.

### [04-optional/ROOT_ACCESS_ANALYSIS.md](04-optional/ROOT_ACCESS_ANALYSIS.md)
⚠️ **Опционально:** Анализ возможностей с root-доступом. 5 способов обхода CarProperty API, сравнение, рекомендации.

### [04-optional/ERROR_HANDLING_ANALYSIS.md](04-optional/ERROR_HANDLING_ANALYSIS.md)
⚠️ **Опционально:** Обработка ошибок GB32960 Protocol. Типы некорректных данных, реакция сервера, механизмы защиты.

### [04-optional/RMU_MSG_CONTROLLER_ANALYSIS.md](04-optional/RMU_MSG_CONTROLLER_ANALYSIS.md)
⚠️ **Опционально:** Контроллер уведомлений о зарядке (TLV-протокол). Не относится к управлению спойлером.

### [04-optional/VEHICLE_HEALTH_DIAGNOSIS_ANALYSIS.md](04-optional/VEHICLE_HEALTH_DIAGNOSIS_ANALYSIS.md)
⚠️ **Опционально:** Система диагностики автомобиля (7 проверок, TLV-протокол). Не относится к управлению спойлером.

### [04-optional/DOCUMENTATION_UPDATES.md](04-optional/DOCUMENTATION_UPDATES.md)
История обновлений документации и найденных несоответствий.

### [04-optional/RESPONSE_ANALYSIS_SUMMARY.md](04-optional/RESPONSE_ANALYSIS_SUMMARY.md)
Анализ ответных пакетов от сервера: форматы, тайминги, обработка ошибок.

### [04-optional/SPOILER_APP_PLAN.md](04-optional/SPOILER_APP_PLAN.md)
План разработки приложения управления спойлером: этапы, задачи, сроки.

---

## 🧭 Навигация

**Первый запуск:**  
`01-start/HURON_SUMMARY_TELEGRAM` → `01-start/355_COMPLETE_GUIDE` → `02-architecture/MEGA_CAR_PROPERTY_ANALYSIS`

**Для работы со спойлером:**
`02-architecture/CARBODY_CONTROLLER_PACKAGE` → [SUMMARY_1303](SUMMARY_1303.md) → `03-protocol/reverse-engineering`

**Для глубокого понимания:**  
`03-protocol/ANALYSIS` → `03-protocol/reverse-engineering` → `03-protocol/PROPERTY_ID_MAPPING` → `03-protocol/VALUE_ENCODING_ANALYSIS`

**Архитектура:**  
`02-architecture/HURON_CAR_CONTROLLER_ANALYSIS` → `02-architecture/INTERACT_SETTER_IMPL_ANALYSIS` → `03-protocol/huron-car-service-capabilities`
