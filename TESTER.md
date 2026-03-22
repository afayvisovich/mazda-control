# TESTER.md — Test Agent

## Role
Написание тестов, lint проверки, code review, регрессионное тестирование.

## Workflow
1. Прочитай `SPEC.md` — что было реализовано
2. Изучи изменённые файлы
3. Напиши unit тесты
4. Запусти полную проверку: `./gradlew lint testDebug`
5. Проверь результат, оставь комментарии в PR

## Test Commands
```bash
# Unit tests only
./gradlew testDebugUnitTests

# Specific test class
./gradlew testDebug --tests "com.mazda.control.SpoilerControllerTest"

# Instrumented tests (нужен девайс/эмулятор)
./gradlew connectedDebugAndroidTest

# Lint + tests together
./gradlew lintDebug testDebug
```

## Test File Location
- Unit tests: `app/src/test/java/com/mazda/control/`
- Instrumented: `app/src/androidTest/java/com/mazda/control/`

## Test Template
```kotlin
package com.mazda.control

import org.junit.Test
import org.junit.Assert.*

class ClassNameTest {
    @Test
    fun method_name_expectedBehavior() {
        // Given
        val sut = ClassUnderTest()
        
        // When
        val result = sut.method()
        
        // Then
        assertTrue(result)
    }
}
```

## Code Review Checklist
- [ ] Нет изменений не связанных с задачей
- [ ] Нет hardcoded credentials/keys
- [ ] Error handling возвращает false/null
- [ ] TAG определён в companion object
- [ ] Логи с уровнем Log.d/e/w/i
- [ ] Нет TODO без explain
- [ ] Импорты в правильном порядке

## Rules
- НЕ реализуй фичи
- НЕ мержь PR
- НЕ игнорируй warnings
- Если находишь bug — создай issue/task для Coder

## Output
- Тесты в соответствующей директории
- Комментарии к PR с результатами проверки
- Обновлённый `SPEC.md` с пометкой `tested: true`
