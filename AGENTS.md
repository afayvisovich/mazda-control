# AGENTS.md - Mazda Control Project

## Project Overview
Android application for controlling Mazda vehicle spoiler via Shizuku.
Uses Kotlin, Jetpack Compose, and Gradle build system.

## Build Commands

### Gradle Wrapper
```bash
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK
./gradlew clean              # Clean build artifacts
```

### Testing
```bash
./gradlew test               # Run all unit tests
./gradlew testDebug          # Run debug unit tests
./gradlew testDebugUnitTests  # Run unit tests only

# Run specific unit test
./gradlew testDebug --tests "com.mazda.control.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
./gradlew connectedDebugAndroidTest
```

### Code Quality
```bash
./gradlew lint                # Run lint analysis
./gradlew lintDebug           # Run lint on debug build
./gradlew ktlintCheck         # Run Kotlin style check (if configured)
```

### Gradle Daemon
```bash
./gradlew --stop             # Stop Gradle daemon
./gradlew --no-daemon        # Run without daemon
```

## Code Style Guidelines

### Kotlin Version & Style
- Kotlin 2.2.10 (from `settings.gradle`)
- **Official code style** (`kotlin.code.style=official` in `gradle.properties`)
- 4-space indentation, 120 character line length recommended

### Package Structure
```
com.mazda.control
├── MainActivity.kt
├── TestMode.kt
├── SpoilerController.kt
├── TBoxProtocolController.kt
├── Logger.kt
├── CircuitBreaker.kt
├── IMegaController.kt
├── RealMegaController.kt
├── MockMegaController.kt (in mock/ subpackage)
└── ui/theme/ (Compose theming)
```

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `SpoilerController`, `TBoxProtocolController` |
| Functions | camelCase | `setSpoiler()`, `isShizukuAvailable()` |
| Constants | UPPER_SNAKE_CASE | `CAR_SERVICE`, `SPOILER_OPEN` |
| Companion Object | PascalCase | `companion object { const val TAG = ... }` |
| Data classes | PascalCase | `data class ServerResponse(...)` |
| Enum values | PascalCase | `PacketType.COMMAND` |
| Package private | camelCase or UPPER_SNAKE | |

### Import Organization
Standard Android/Kotlin ordering:
1. `android.*`
2. `java.*` / `javax.*`
3. Kotlin standard library
4. Third-party libraries (`rikka.shizuku.*`, `com.google.gson.*`)
5. Internal packages (`com.mazda.control.*`)

### Error Handling
- Use specific exception types (`UnknownHostException`, `ConnectException`, `SocketTimeoutException`)
- Always log errors with context: `Log.e(TAG, "message", e)`
- Return `false` or `null` on failure, never throw silently
- Wrap risky operations in try-catch with specific handling

### Logging
- Use Android's `android.util.Log` (d, e, w, i)
- Tag constant in companion object: `private const val TAG = "ClassName"`
- Use centralized `Logger` object for file logging
- Log format: `[timestamp] [level] tag: message`

### Coroutines
- Use `runBlocking` sparingly - only when bridging non-suspend to suspend code
- Prefer structured concurrency with `CoroutineScope`

### Compose
- Use `@Composable` for UI functions
- `@OptIn(ExperimentalMaterial3Api::class)` for experimental APIs
- Prefer `remember { mutableStateOf() }` for local UI state
- Use `Modifier` chaining for layout

### Testing
- Unit tests: JUnit 4 in `src/test/java/`
- Instrumented tests: AndroidJUnit4 in `src/androidTest/java/`
- Test naming: `ClassNameTest` with `@Test` methods named `method_name_expectedBehavior`

## Architecture Patterns

### Controller Pattern
- `IMegaController` interface defines contract
- `RealMegaController` / `MockMegaController` implementations
- `TestMode.getController()` returns appropriate implementation

### Risk Mitigation
- `CircuitBreaker` - prevents cascade failures
- `SystemHealthMonitor` - monitors system health
- `SafeServiceCaller` - timeout and retry logic

### Service Communication
- Shizuku for privileged operations
- TCP socket (`127.0.0.1:32960`) for emulator communication
- AIDL for binder communication

## File Locations
- Main activity: `app/src/main/java/com/mazda/control/MainActivity.kt`
- App class: `app/src/main/java/com/mazda/control/MazdaControlApp.kt`
- Build config: `app/build.gradle`
- Gradle config: `settings.gradle`, `gradle.properties`

## Multi-Agent Workflow

### Agent Roles
```
Orchestrator (human/supervisor)
├── Researcher   → SPEC.md, анализ, документация
├── Coder        → реализация, рефакторинг
└── Tester       → тесты, lint, ревью
```

### Workflow: Research → Code → Test
1. **Researcher** создаёт/обновляет `SPEC.md` с задачей
2. **Coder** читает `SPEC.md`, реализует, создаёт PR/MR
3. **Tester** проверяет код, пишет тесты, запускает lint
4. **Orchestrator** мержит результат

### Agent Files
- `ORCHESTRATOR.md` — управление потоком (самостоятельный цикл Research→Code→Test)
- `RESEARCHER.md` — инструкции для исследователя
- `CODER.md` — инструкции для кодера
- `TESTER.md` — инструкции для тестировщика

### Quick Start (Orchestrator)
```bash
/task "Orchestrator" subagent_type=general prompt="
Читай ORCHESTRATOR.md.
Задача: [описание задачи]
Выполни полный цикл: Research → Code → Test
"
```

### Communication Protocol
- Orchestrator управляет всем циклом автоматически
- Все агенты читают `SPEC.md` перед работой
- Результат работы сохраняется в `SPEC.md` (статус, что сделано)
- Tester оставляет комментарии в коде через PR review
- Финальный check: `./gradlew lint testDebug`

## Notes
- Min SDK: 30, Target SDK: 35
- Uses Shizuku 12.1.0 for system service calls
- Compose BOM: 2024.09.00
- Gson for JSON serialization
