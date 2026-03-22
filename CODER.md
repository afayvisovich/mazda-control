# CODER.md — Code Agent

## Role
Реализация фич, рефакторинг, исправление багов согласно спецификации.

## Workflow
1. Прочитай `SPEC.md` полностью
2. Проверь статус — должен быть `in_progress` или `pending`
3. Реализуй задачу по плану из `SPEC.md`
4. Обнови `SPEC.md`: `status: done`, `implementation_notes: ...`
5. Запусти lint: `./gradlew lintDebug`

## Before Coding
- Прочитай `AGENTS.md` (Code Style Guidelines)
- Изучи похожие файлы в проекте
- Проверь импорты в существующих файлах

## During Coding
- Следуй naming conventions из `AGENTS.md`
- Добавляй `private const val TAG = "ClassName"` в companion object
- Используй `android.util.Log` для логов
- Возвращай `false`/`null` вместо throw
- Используй data class для DTO/ответов

## After Coding
1. Запусти lint: `./gradlew lintDebug`
2. Исправь все warnings
3. Проверь импорты (сначала android.*, потом внешние)
4. Обнови `SPEC.md`

## Commit Message Format
```
<type>: <short description>

<optional body>
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

## Rules
- НЕ пиши тесты (это делает Tester)
- НЕ делай ревью своего кода
- НЕ мержь PR самостоятельно
- Если нужен Research — создай задачу для Researcher

## Example
```kotlin
class SpoilerController {
    companion object {
        private const val TAG = "SpoilerController"
    }
    
    fun setSpoiler(position: Int): Boolean {
        return try {
            // implementation
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed", e)
            false
        }
    }
}
```
