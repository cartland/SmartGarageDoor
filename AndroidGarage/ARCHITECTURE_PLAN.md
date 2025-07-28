# Architecture Migration Plan

This document outlines the plan to refactor the Smart Garage Door app into a layered, multi-module Kotlin Multiplatform (KMP) architecture.

## 1. Primary Goals

-   **Enable Code Sharing:** Structure the project to share business logic and UI with a future iOS application.
-   **Improve Code Architecture:** Establish clear boundaries between layers (UI, domain, data) and features.
-   **Modularize by Feature:** Decompose the application into self-contained feature modules that are independently buildable and testable.

## 2. Proposed Architecture: KMP Feature Modules

The application will be broken down into a series of self-contained, layered Kotlin Multiplatform modules.

### 2.1. Module Naming Convention

-   **Feature Modules:** `:feature-<name>` (e.g., `:feature-door`, `:feature-history`)
-   **Core Modules:** `:core-<name>` (e.g., `:core-database`, `:core-network`)

### 2.2. Folder and Package Structure

The directory structure will strictly match the package name convention.

-   **Module Path:** `feature-door/`
-   **Package Name:** `com.chriscartland.garage.feature.door`
-   **Source Path:** `feature-door/src/commonMain/kotlin/com/chriscartland/garage/feature/door/`

### 2.3. Internal Feature Module Structure

Each feature module will be a KMP module with the following internal layers and source sets:

#### `commonMain` (Shared Logic & UI)

-   **`ui`**: Contains shared UI written in **Compose Multiplatform**. This includes Composable screens and platform-agnostic presentation logic (e.g., MVI State Holders instead of Android `ViewModels`).
-   **`domain`**: Contains the core, platform-agnostic business logic.
    -   `model/`: Data classes representing the core entities.
    -   `repository/`: **Interfaces** for data access (e.g., `interface DoorRepository`).
    -   `usecase/`: Individual business operations (e.g., `OpenDoorUseCase`).
-   **`data`**: Contains `expect` declarations for platform-specific implementations that the domain layer needs (e.g., `expect fun createHttpClient(): HttpClient`).

#### `androidMain` (Android-Specific Implementation)

-   Provides the `actual` implementations for the `expect` declarations in `commonMain`.
-   **`data`**: Contains the concrete repository implementations using Android-specific libraries like **Room**, **Retrofit**, and **WorkManager**.

#### `iosMain` (iOS-Specific Implementation)

-   Provides the `actual` implementations for the `expect` declarations.
-   **`data`**: Contains repository implementations using native iOS libraries like **CoreData**, **URLSession**, etc.

### 2.4. Core Modules

Cross-cutting concerns will be extracted into their own `:core` KMP modules.

-   **`:core-database`**: Manages the shared Room database setup. It will use the `expect`/`actual` pattern to provide platform-specific database builders while allowing feature modules to depend on a common `AppDatabase` class.
-   **`:core-network`**: Manages the shared network client (e.g., Ktor).

### 2.5. Application (`:androidApp`) Module

The existing `:androidApp` will become a thin "wiring" layer responsible for:
-   Hosting the main Android `Activity`.
-   Setting up the navigation graph to link the different feature modules.
-   Initializing any Android-specific services and dependency injection (Hilt).

## 3. Testing Strategy

To ensure the migration is successful and maintainable, we will implement a robust testing strategy at each stage.

-   **Unit Tests (`commonMain`):** These will be pure Kotlin tests that run on the JVM. They will validate the `domain` layer (UseCases, Presenters/StateHolders) using mock repositories. This ensures our core business logic is correct and platform-agnostic.
-   **Android Instrumented Tests (`androidMain`):** These tests will run on an Android device or emulator.
    -   **Integration Tests:** To validate the `data` layer. We will test the `actual` repository implementations to ensure they correctly interact with Room, Retrofit, and WorkManager.
    -   **UI Tests:** To validate the Compose UI. We will verify that the UI displays the correct state and that user interactions are handled correctly.

## 4. Tracking Progress and Migration Checklist

To track the progress of this refactoring effort, update the checklists below by marking completed items with an `[x]`. This file will serve as the single source of truth for the migration's status.

### Phase 0: Establish Test Safety Net
- [ ] **Review Existing Tests**
  - [ ] Audit the current unit and instrumented tests in the `androidApp` module to identify coverage gaps.
- [ ] **Create Characterization Tests**
  - [ ] Write new instrumented UI tests for the critical user flows (e.g., logging in, viewing door status, opening/closing the door, viewing history). These tests will characterize the existing behavior of the application.
- [ ] **Ensure Stability**
  - [ ] **Testable Milestone:**
    - [ ] Run all existing and new tests (`./gradlew :androidApp:check`) and confirm they are all passing before proceeding to Phase 1.

### Phase 1: Core Module Setup
- [ ] **Establish Core Modules**
  - [ ] Create `:core-database` KMP module.
  - [ ] Set up Room with the `expect`/`actual` pattern for the database builder.
  - [ ] Create `:core-network` KMP module.
  - [ ] Configure a Ktor client for shared networking.
  - [ ] **Testable Milestone:**
    - [ ] Write instrumented tests in `:core-database` to verify database creation on Android.
    - [ ] Write unit tests in `:core-network` to verify the Ktor client can be configured.

### Phase 2: Feature Migration (`:feature-door`)
- [ ] **Migrate First Feature (`door`)**
  - [ ] **Step 2.1: Create Module and Domain Layer**
    - [ ] Create new KMP module: `:feature-door`.
    - [ ] Set up the `ui`, `domain`, and `data` package structure.
    - [ ] Move `DoorStatus` model to `feature-door/domain/model`.
    - [ ] Define `DoorRepository` interface in `feature-door/domain/repository`.
    - [ ] Move business logic to `UseCases` in `feature-door/domain/usecase`.
    - [ ] **Testable Milestone:**
      - [ ] Write pure Kotlin unit tests for the `UseCases` in `commonTest` using a mock `DoorRepository`.

  - [ ] **Step 2.2: Implement Data Layer**
    - [ ] Implement the `actual` `DoorRepository` in `androidMain` using Retrofit and `:core-database`.
    - [ ] **Testable Milestone:**
      - [ ] Write Android instrumented tests for `DoorRepositoryImpl` in `androidMain` using a mock web server.

  - [ ] **Step 2.3: Implement UI Layer**
    - [ ] Re-implement the UI in Compose Multiplatform within `feature-door/ui`.
    - [ ] Wire the UI to the domain layer's UseCases/Presenters.
    - [ ] **Testable Milestone:**
      - [ ] Write Android instrumented UI tests for the new Compose screen.

  - [ ] **Step 2.4: Integrate into App**
    - [ ] Wire the `:feature-door` module into the `:androidApp` navigation graph.
    - [ ] Manually verify the feature works end-to-end in the running application.

### Phase 3: Subsequent Feature Migration
- [ ] **Migrate `:feature-history`**
  - [ ] Repeat the process from Phase 2.
- [ ] **Migrate `:feature-settings`**
  - [ ] Repeat the process from Phase 2.
- [ ] ... (add other features as needed)

### Phase 4: Finalization
- [ ] **Decommission Old Code**
  - [ ] Once all features are migrated, safely remove the now-unused packages from `:androidApp`.
  - [ ] Remove old tests associated with the decommissioned code.
  - [ ] **Testable Milestone:**
    - [ ] Run all tests for all modules together (`./gradlew check`) to ensure the complete, refactored application is stable.

## Phase 5: Technical Debt / Modernization

This phase addresses warnings and deprecated APIs to modernize the codebase.

-   **Java Compiler Version:**
    -   [x] Update Java source/target compatibility to a newer version (e.g., Java 11 or 17) or configure Java toolchains in `build.gradle.kts` files to resolve deprecation warnings related to Java 8.
-   **Deprecated `onActivityResult`:**
    -   [ ] Refactor `MainActivity.kt` to use the Activity Result API for handling activity results.
-   **Deprecated Google Sign-In APIs:**
    -   [ ] Update the authentication logic in `AuthViewModel.kt` to use the newer APIs for Google Sign-In (e.g., `SignInClient` and `SignInCredential`).
-   **Moshi Kapt to KSP Migration:**
    -   [ ] Migrate Moshi code generation from Kapt to KSP to resolve deprecation warnings.

## Phase 6: Test Fixes

This phase addresses issues found during instrumented test execution.

-   **`ExampleInstrumentedTest` Package Name:**
    -   [ ] Update `ExampleInstrumentedTest` to correctly handle the `.debug` package name suffix for debug builds.
-   **Hilt Test Application Setup:**
    -   [ ] Configure `GarageAppTest` to use `HiltTestApplication` or a custom Hilt test application generated with `@CustomTestApplication` for proper Hilt test setup.