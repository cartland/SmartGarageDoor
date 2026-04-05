# Hilt → kotlin-inject Migration Guide

This document records the step-by-step migration from Hilt to kotlin-inject. It serves as a permanent reference for this project and a template for migrating other repositories.

## Why Migrate

- **KMP compatibility**: kotlin-inject works in `commonMain`; Hilt is Android-only
- **No annotation processing magic**: kotlin-inject generates readable Kotlin code, not Dagger's Java
- **Constructor injection only**: no field injection, no `@AndroidEntryPoint` — simpler mental model
- **Aligns with battery-butler**: shared patterns across projects

## Prerequisites

Before starting this migration, the codebase should have:
- [x] Domain module with pure Kotlin types and repository interfaces (Phase 2.1)
- [x] UseCase layer extracting business logic from ViewModels (Phase 2.2)
- [x] Data module with pure data source interfaces (Phase 2.3)
- [x] ViewModels depend on UseCases, UseCases depend on Repositories, Repositories depend on data interfaces

## Migration Strategy

**Side-by-side migration.** Hilt and kotlin-inject coexist during the transition. Each ViewModel and its dependencies are migrated individually. The final PR removes Hilt entirely — only deleting annotations and Gradle config, no business logic changes.

## Before You Start: Hilt Inventory

### Hilt Annotations in Use

| Annotation | Count | Where |
|-----------|-------|-------|
| `@HiltAndroidApp` | 1 | `GarageApplication.kt` |
| `@AndroidEntryPoint` | 2 | `MainActivity.kt`, `FCMService.kt` |
| `@HiltViewModel` | 4 | `DoorViewModelImpl`, `RemoteButtonViewModelImpl`, `AuthViewModelImpl`, `AppSettingsViewModelImpl` |
| `@Module @InstallIn` | 10 | Repository modules, data source modules, network modules |
| `@Binds` | 6 | Repository and ViewModel interface bindings |
| `@Provides` | 4 | `AppDatabase`, `LocalDoorDataSource`, network data sources |
| `@Inject constructor` | ~15 | All implementations |

### Hilt Dependency Graph

```
GarageApplication (@HiltAndroidApp)
├── MainActivity (@AndroidEntryPoint)
│   ├── DoorViewModelImpl (@HiltViewModel)
│   │   ├── AppLoggerRepository
│   │   ├── DoorRepository (via @Binds)
│   │   ├── DispatcherProvider (via @Provides)
│   │   ├── FetchCurrentDoorEventUseCase
│   │   ├── FetchRecentDoorEventsUseCase
│   │   ├── FetchFcmStatusUseCase
│   │   ├── RegisterFcmUseCase
│   │   └── DeregisterFcmUseCase
│   ├── RemoteButtonViewModelImpl (@HiltViewModel)
│   │   ├── PushRepository (via @Binds)
│   │   ├── DoorRepository (via @Binds)
│   │   ├── DispatcherProvider (via @Provides)
│   │   ├── PushRemoteButtonUseCase
│   │   └── SnoozeNotificationsUseCase
│   ├── AuthViewModelImpl (@HiltViewModel)
│   │   ├── AuthRepository (via @Binds)
│   │   ├── AppLoggerRepository
│   │   └── DispatcherProvider (via @Provides)
│   └── AppSettingsViewModelImpl (@HiltViewModel)
│       └── AppSettings
└── FCMService (@AndroidEntryPoint)
    ├── DoorRepository (via @Binds)
    └── AppLoggerRepository
```

---

## Step 1: Add kotlin-inject Alongside Hilt

**Goal:** Both DI systems compile and run. No behavior change.

### Gradle Changes

```kotlin
// AndroidGarage/gradle/libs.versions.toml
[versions]
kotlin-inject = "0.9.0"

[libraries]
kotlin-inject-runtime = { module = "me.tatarka.inject:kotlin-inject-runtime", version.ref = "kotlin-inject" }
kotlin-inject-compiler = { module = "me.tatarka.inject:kotlin-inject-compiler-ksp", version.ref = "kotlin-inject" }
```

```kotlin
// AndroidGarage/androidApp/build.gradle.kts
dependencies {
    implementation(libs.kotlin.inject.runtime)
    ksp(libs.kotlin.inject.compiler)
}
```

### Create AppComponent

```kotlin
// com.chriscartland.garage.di.AppComponent

@Component
@Singleton
abstract class AppComponent(
    @get:Provides val application: Application,
) {
    // Dependencies will be added here as ViewModels migrate
}

@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton
```

### Create Component in Application

```kotlin
// GarageApplication.kt
@HiltAndroidApp  // Keep during migration
class GarageApplication : Application() {
    // kotlin-inject component — used by migrated ViewModels
    val component: AppComponent by lazy {
        AppComponent::class.create(this)
    }
}
```

### Commit Reference

<!-- Completed -->
`65f89c8` (#86) — Add kotlin-inject alongside Hilt

---

## Step 2: Migrate One ViewModel (Proof of Concept)

**Goal:** Prove the pattern works. Pick the simplest ViewModel.

### Before (Hilt)

```kotlin
@HiltViewModel
class AppSettingsViewModelImpl @Inject constructor(
    private val appSettings: AppSettings,
) : ViewModel(), AppSettingsViewModel { ... }

// In Composable:
val viewModel: AppSettingsViewModel = hiltViewModel<AppSettingsViewModelImpl>()
```

### After (kotlin-inject)

```kotlin
// No @HiltViewModel — just @Inject
class AppSettingsViewModelImpl @Inject constructor(
    private val appSettings: AppSettings,
) : ViewModel(), AppSettingsViewModel { ... }

// In AppComponent:
abstract val appSettingsViewModel: AppSettingsViewModelImpl

// In Composable:
val component = (LocalContext.current.applicationContext as GarageApplication).component
val viewModel: AppSettingsViewModel = viewModel { component.appSettingsViewModel }
```

### Key Differences

| Aspect | Hilt | kotlin-inject |
|--------|------|---------------|
| ViewModel annotation | `@HiltViewModel` | None (just `@Inject`) |
| Getting ViewModel | `hiltViewModel<Impl>()` | `viewModel { component.property }` |
| Component access | Automatic (Hilt magic) | Manual via `Application` |
| Scope | `@HiltViewModel` = Activity-scoped | `viewModel {}` = ViewModelStore-scoped |

### Commit Reference

`a7c2ade` (#87) — Migrate AppSettingsViewModel to kotlin-inject

---

## Step 3: Migrate Remaining ViewModels

**Order:** Simplest → most complex. Each is a separate PR.

1. **AppSettingsViewModel** — no repository dependencies (Step 2)
2. **AuthViewModel** — depends on AuthRepository, AppLoggerRepository
3. **DoorViewModel** — depends on DoorRepository, UseCases, FCM UseCases
4. **RemoteButtonViewModel** — depends on PushRepository, DoorRepository, UseCases

### Pattern for Each Migration

1. Remove `@HiltViewModel` from the ViewModel implementation
2. Add `abstract val xxxViewModel: XxxViewModelImpl` to `AppComponent`
3. Move `@Provides`/`@Binds` for dependencies to `AppComponent` (if not already there)
4. Replace `hiltViewModel<XxxViewModelImpl>()` with `viewModel { component.xxxViewModel }` in composables
5. Delete the Hilt `@Module` class for that ViewModel's bindings
6. Run `./scripts/validate.sh` and verify

### Commit References

| ViewModel | Commit | PR |
|-----------|--------|-----|
| AppSettingsViewModel | `a7c2ade` | #87 |
| AuthViewModel | `953beb6` | #89 |
| DoorViewModel | `9312b01` | #90 |
| RemoteButtonViewModel | `9312b01` | #90 |
| AppLoggerViewModel | `2185646` | #91 |

---

## Step 4: Migrate Repository and Data Source Bindings

Move all `@Module`/`@Binds`/`@Provides` into `AppComponent`.

### Before (Hilt Module)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class GarageRepositoryModule {
    @Binds
    abstract fun bindGarageRepository(impl: DoorRepositoryImpl): DoorRepository
}
```

### After (kotlin-inject AppComponent)

```kotlin
@Component
@Singleton
abstract class AppComponent(...) {
    @Provides @Singleton
    fun provideDoorRepository(impl: DoorRepositoryImpl): DoorRepository = impl
}
```

### Bindings to Migrate

| Binding | Hilt Module Location | Type |
|---------|---------------------|------|
| `DoorRepository` | `door/DoorRepository.kt` | `@Binds` |
| `AuthRepository` | `auth/AuthRepository.kt` | `@Binds` |
| `PushRepository` | `remotebutton/PushRepository.kt` | `@Binds` |
| `ServerConfigRepository` | `config/ServerConfigRepository.kt` | `@Binds` |
| `DoorFcmRepository` | `fcm/DoorFcmRepository.kt` | `@Binds` |
| `AppLoggerRepository` | `applogger/AppLoggerRepository.kt` | `@Binds` |
| `RemoteButtonViewModel` | `remotebutton/RemoteButtonViewModel.kt` | `@Binds` |
| `AppDatabase` | `db/AppDatabase.kt` | `@Provides` |
| `LocalDoorDataSource` | `db/LocalDoorDataSource.kt` | `@Provides` |
| `NetworkDoorDataSource` | `internet/RetrofitNetworkDoorDataSource.kt` | `@Provides` |
| `NetworkButtonDataSource` | `internet/RetrofitNetworkButtonDataSource.kt` | `@Provides` |
| `NetworkConfigDataSource` | `internet/RetrofitNetworkConfigDataSource.kt` | `@Provides` |
| `DispatcherProvider` | `coroutines/DispatcherProvider.kt` | `@Provides` |
| `AppSettings` | `settings/AppSettings.kt` | `@Provides` |
| `GarageNetworkService` | `internet/GarageNetworkService.kt` | `@Provides` |

### Commit Reference

`c5d9d58` (#96) — Migrated alongside Hilt removal

---

## Step 5: Handle FCMService (Android Service Injection)

**Problem:** `FCMService` uses `@AndroidEntryPoint` for Hilt field injection. kotlin-inject doesn't support Android service injection.

**Solution:** Access the component from Application:

### Before (Hilt)

```kotlin
@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {
    @Inject lateinit var doorRepository: DoorRepository
    @Inject lateinit var appLoggerRepository: AppLoggerRepository
}
```

### After (kotlin-inject)

```kotlin
class FCMService : FirebaseMessagingService() {
    private val doorRepository: DoorRepository by lazy {
        (application as GarageApplication).component.doorRepository
    }
    private val appLoggerRepository: AppLoggerRepository by lazy {
        (application as GarageApplication).component.appLoggerRepository
    }
}
```

### Commit Reference

`2185646` (#91) — Migrate FCMService to lazy component access

---

## Step 6: Remove Hilt Completely

**This PR should be pure deletion — no business logic changes.**

### What to Remove

1. `@HiltAndroidApp` from `GarageApplication.kt`
2. `@AndroidEntryPoint` from `MainActivity.kt`
3. All `@Module`, `@InstallIn`, `@Binds` classes (should be empty by now)
4. Hilt Gradle plugin (`libs.plugins.hilt`) from `build.gradle.kts`
5. Hilt dependencies (`hilt-android`, `hilt-compiler`, `hilt-navigation-compose`)
6. `kapt` or `ksp` processor for Hilt

### Verification

- `./scripts/validate.sh` passes
- No `dagger` or `hilt` imports remain: `grep -r "dagger\|hilt" --include="*.kt"`
- App launches, navigates all screens, push button works, FCM notifications work

### Commit Reference

`c5d9d58` (#96) — Remove Hilt completely

---

## Rollback Plan

If a step breaks production:
1. The previous `android/N` tag is always available
2. Each step is a separate PR — revert the specific commit
3. Hilt and kotlin-inject coexist, so partial migration is stable

## Reference

- [kotlin-inject GitHub](https://github.com/evant/kotlin-inject)
- [battery-butler AppComponent](https://github.com/cartland/battery-butler) — production example
- [Hilt → kotlin-inject discussion](https://github.com/evant/kotlin-inject/discussions) — community patterns
