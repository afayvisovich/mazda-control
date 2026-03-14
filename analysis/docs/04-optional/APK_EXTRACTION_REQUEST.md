# Запрос на извлечение дополнительных APK из автомобиля

**Дата:** 2026-03-14
**Статус:** Требуется извлечение из автомобиля

## Проблема

При анализе протокола GB32960 обнаружено, что:

1. **CallServerInterceptor** (OkHttp) в процессе PID 8755 отправляет пакеты на `localhost:32960`
2. Сервер, который слушает порт 32960, **НЕ найден** в декомпилированных APK:
   - ❌ HuronCarService.apk
   - ❌ HuronCarController.apk

3. В логах видны успешные ответы от сервера (статус-пакеты с mark=3)

## Необходимые APK для извлечения

### 1. com.mega.rmu (Remote Management Unit)

**Приоритет:** ВЫСОКИЙ

**Обоснование:**
- В логах видны сигналы `RMU_BMS_*` (Battery Management System)
- RMU может содержать сервер GB32960
- Process ID: 8895 (из логов)

**Команда для извлечения:**
```bash
adb shell pm list packages | grep rmu
adb shell pm path com.mega.rmu
adb pull /path/to/base.apk rmu.apk
```

---

### 2. com.mega.car.tbox (TBox Service)

**Приоритет:** ВЫСОКИЙ

**Обоснование:**
- В HuronCarService найден `GB32960Processor`
- TBox (Telematics Box) отвечает за телематику GB32960
- Property ID: `0x30000003` (ID_32960_MESSAGE), `0x30000004` (ID_32960_LOGIN_STATUS)

**Команда для извлечения:**
```bash
adb shell pm list packages | grep tbox
adb shell pm path com.mega.car.tbox
adb pull /path/to/base.apk tbox.apk
```

---

### 3. com.mega.car.carbodyservice (CarBody Service)

**Приоритет:** СРЕДНИЙ

**Обоснование:**
- Управление спойлером через CarProperty API
- Может содержать сервер для локальных подключений

**Команда для извлечения:**
```bash
adb shell pm list packages | grep carbody
adb shell pm path com.mega.car.carbodyservice
adb pull /path/to/base.apk carbody.apk
```

---

### 4. com.mega.hvac (HVAC Service)

**Приоритет:** СРЕДНИЙ

**Обоснование:**
- В логах видны команды климат-контроля через тот же протокол
- Может использовать общий сервер GB32960

**Команда для извлечения:**
```bash
adb shell pm list packages | grep hvac
adb shell pm path com.mega.hvac
adb pull /path/to/base.apk hvac.apk
```

---

### 5. com.mega.gateway (Gateway Service)

**Приоритет:** ВЫСОКИЙ

**Обоснование:**
- Gateway может быть центральным сервером для всех подключений
- Порт 32960 может слушаться именно здесь

**Команда для извлечения:**
```bash
adb shell pm list packages | grep gateway
adb shell pm path com.mega.gateway
adb pull /path/to/base.apk gateway.apk
```

---

## Альтернативный подход: поиск процесса по порту

Если извлечение APK затруднительно, можно определить процесс по порту:

```bash
# На автомобиле выполнить:
adb shell su -c "netstat -tuln | grep 32960"
adb shell su -c "lsof -i :32960"
adb shell su -c "ps -A | grep <PID>"
```

---

## Известные процессы из логов

| PID | Process Name | Описание |
|-----|--------------|----------|
| 8755 | (unknown) | HuronCarController (основной UI) |
| 8895 | com.mega.rmu | Remote Management Unit |
| 6055 | (unknown) | Audio Policy Manager |
| 6310 | (unknown) | Dock HVAC Controller |
| 3170 | (unknown) | Display Manager Service |
| 5260 | (unknown) | Plugin System |

---

## Критически важные находки

### 1. Upload32960DataTask

**Из логов:**
```
03-11 22:10:42.893  8755  8755 I AbsTaskExecutor: start handle: Upload32960DataTask
03-11 22:10:42.901  8755  8755 I AbsTaskExecutor: AbsTaskExecutor handle: Upload32960DataTask error != OK
```

**Значение:**
- Задача пытается подключиться к порту 32960
- Завершается с ошибкой (сервер ещё не готов?)
- Повторяется каждые 10 секунд

### 2. CallServerInterceptor

**Из логов:**
```
request <3 ip = localhost/127.0.0.1 port = 32960 > raw data >>>>>>>>
```

**Значение:**
- OkHttp interceptor отправляет пакеты на localhost:32960
- Это **TCP socket** (не Unix domain socket)
- Успешно получает ответы (mark=3 — статус)

### 3. GB32960 Processor

**Из HuronCarService:**
```smali
.field public static final ID_32960_LOGIN_STATUS:I = 0x30000004
.field public static final ID_32960_MESSAGE:I = 0x30000003
```

**Значение:**
- 32960 — это **Property ID префикс**, а не только порт
- Property ID: `0x30000003`, `0x30000004`
- TBox процессор обрабатывает входящие данные

---

## Вывод

**Сервер на порту 32960 находится в одном из неизвлечённых APK.**

**Рекомендуемый порядок извлечения:**
1. com.mega.rmu (наиболее вероятный кандидат)
2. com.mega.gateway (центральный сервер)
3. com.mega.car.tbox (обработчик GB32960)

**Без этих APK невозможно:**
- Определить точный протокол подключения
- Найти серверную реализацию
- Понять полную архитектуру взаимодействия

---

## Обновление от 2026-03-14

**Гипотеза:** Порт 32960 использует **TCP Socket**, что подтверждается:
- Логи CallServerInterceptor: `ip = localhost/127.0.0.1 port = 32960`
- Скрипт test_spoiler.py: `adb forward tcp:32960 tcp:32960`
- Нет упоминаний о Unix domain socket в логах

**Текущее приложение (MazdaControl):**
- Использует `Socket("127.0.0.1", 32960)` — TCP socket ✅
- PacketGenerator.kt соответствует реальным пакетам ✅
- Готово к тестированию на автомобиле
