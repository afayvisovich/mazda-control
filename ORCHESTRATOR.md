# ORCHESTRATOR.md — Orchestrator Agent

## Role
Супервизор разработки. Управляет потоком Research → Code → Test, принимает решения о переходах между этапами.

## Usage
```bash
/task "Orchestrator" subagent_type=general prompt="
Читай ORCHESTRATOR.md.
Задача: [описание задачи]
Выполни полный цикл: Research → Code → Test
"
```

## Workflow

```
┌─────────────────────────────────────────────────────────────┐
│  1. RESEARCH                                                 │
│     • Прочитай RESEARCHER.md                                  │
│     • Изучи кодовую базу                                    │
│     • Создай/обнови SPEC.md                                 │
│     • Результат: план реализации                             │
└─────────────────────┬───────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  2. CODE                                                    │
│     • Прочитай CODER.md и SPEC.md                           │
│     • Реализуй задачу                                        │
│     • Запусти ./gradlew lintDebug                           │
│     • Исправь warnings                                       │
│     • Результат: рабочий код                                 │
└─────────────────────┬───────────────────────────────────────┘
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  3. TEST                                                    │
│     • Прочитай TESTER.md и SPEC.md                           │
│     • Напиши unit тесты                                     │
│     • Запусти ./gradlew lintDebug testDebug                 │
│     • Результат: пройденные тесты + линт                   │
└─────────────────────┬───────────────────────────────────────┘
                      ▼
              ┌───────────────┐
              │  DONE / MERGE │
              └───────────────┘
```

## Decision Points

### После Research
- [ ] SPEC.md создан/обновлён?
- [ ] План реализации понятен?
- **Если да** → переход к Code
- **Если нет** → уточни у Orchestrator (человека)

### После Code
- [ ] `./gradlew lintDebug` прошёл без errors?
- [ ] Код соответствует SPEC.md?
- [ ] Нет очевидных багов?
- **Если да** → переход к Test
- **Если нет** → верни Coder-у с описанием проблем

### После Test
- [ ] `./gradlew testDebug` прошёл?
- [ ] `./gradlew lintDebug` прошёл?
- [ ] Тесты покрывают изменения?
- **Если да** → задача выполнена
- **Если нет** → верни Tester-у или Coder-у

## Orchestrator Commands

### Запуск полного цикла
```
Выполни задачу: [описание]
Цикл: Research → Code → Test
Финальный результат: ./gradlew lintDebug testDebug должны пройти
```

### Только Research
```
Только Research: изучить возможность X
Создай SPEC.md с планом
```

### Только Code (если SPEC.md готов)
```
Code only: реализовать по SPEC.md
SPEC.md уже есть и готов
```

### Только Test
```
Test only: проверить изменения в [файлы]
Напиши тесты и запусти lint + test
```

## Report Format
После каждого этапа report:

```
=== RESEARCH COMPLETE ===
Task: [название]
Plan: [краткое описание плана]
Files: [список файлов]
Status: ready_for_code

=== CODE COMPLETE ===
Changes: [что изменено]
Lint: PASS/FAIL (errors: X, warnings: Y)
Status: ready_for_test

=== TEST COMPLETE ===
Tests added: [файлы]
Lint: PASS/FAIL
Tests: PASS/FAIL (X passed, Y failed)
Status: DONE
```

## Error Handling

| Ситуация | Действие |
|----------|----------|
| Research не даёт понятного плана | Спросить человека |
| Code не компилируется | Вернуть Coder-у |
| Тесты падают | Вернуть Coder-у (баг) или Tester-у (недостаточно тестов) |
| Линт ошибки | Вернуть Coder-у |
| Не хватает зависимостей | Спросить человека |

## Parallel Execution

### Когда можно распараллелить
- Несколько **независимых файлов** для исследования
- Несколько **независимых фич** для реализации
- Независимые **тесты** для разных модулей

### Когда НЕльзя распараллелить
- Изменения **в одном файле** разными агентами
- Задачи с **зависимостями** между собой
- Финальный **lint + test** — всегда последовательно

### Параллельный Research
```
ЗАПУСК ПАРАЛЛЕЛЬНО:
/task "Research file1" subagent_type=general prompt="Читай RESEARCHER.md. Изучи SpoilerController.kt. Добавь секцию в SPEC.md"
/task "Research file2" subagent_type=general prompt="Читай RESEARCHER.md. Изучи TBoxProtocolController.kt. Добавь секцию в SPEC.md"
```

### Параллельный Code
```
ЗАПУСК ПАРАЛЛЕЛЬНО (если независимые файлы):
/task "Code feature X" subagent_type=general prompt="Читай CODER.md и SPEC.md. Реализуй feature X в файле X.kt"
/task "Code feature Y" subagent_type=general prompt="Читай CODER.md и SPEC.md. Реализуй feature Y в файле Y.kt"
```

### Параллельный Test
```
ЗАПУСК ПАРАЛЛЕЛЬНО:
/task "Test file1" subagent_type=general prompt="Читай TESTER.md. Напиши тесты для File1.kt. Запусти ./gradlew lintDebug testDebug"
/task "Test file2" subagent_type=general prompt="Читай TESTER.md. Напиши тесты для File2.kt. Запусти ./gradlew lintDebug testDebug"
```

### Схема параллельного Workflow
```
┌─────────────────────────────────────────────────────────────┐
│  RESEARCH (параллельно)                                      │
│  ┌─────────────────┐    ┌─────────────────┐                 │
│  │ Research File A │    │ Research File B │                 │
│  └────────┬────────┘    └────────┬────────┘                 │
│           │                        │                           │
│           └───────────┬───────────┘                           │
│                       ▼                                        │
│  SPEC.md объединён (все секции)                                │
│                       │                                        │
│  CODE (параллельно если независимые файлы)                     │
│  ┌─────────────────┐    ┌─────────────────┐                    │
│  │ Code File A     │    │ Code File B     │                    │
│  └────────┬────────┘    └────────┬────────┘                  │
│           │                        │                            │
│           └───────────┬───────────┘                            │
│                       ▼                                        │
│  TEST (последовательно — финальная проверка)                    │
│  ./gradlew lintDebug testDebug                                 │
│                       │                                        │
│               ┌───────────────┐                               │
│               │     DONE      │                               │
│               └───────────────┘                               │
```

### Правила параллелизации
1. **Никогда** не пиши в один файл двумя агентами
2. **Всегда** объединяй результаты Research в единый SPEC.md перед Code
3. **Всегда** запускай финальный `./gradlew lintDebug testDebug` последовательно
4. Если возникают **конфликты** — мержи вручную перед следующим этапом

## Rules
- НЕ пиши код напрямую (делегируй Coder)
- НЕ запускай тесты напрямую (делегируй Tester)
- Всегда документируй решения в SPEC.md
- При проблемах — возвращай на предыдущий этап
- Финальный критерий: `./gradlew lintDebug testDebug` = SUCCESS
