<!-- GENERATED from sources by DataGraphExtractionKonsistTest — do not edit.
     Regenerate: ./scripts/generate-data-graph.sh
     The same test pins this file byte-exact to the code (DATA_GRAPH_PLAN.md §6). -->

# Shared data graph

| Node | Kind | Cadence | Declared by | Sharing | Read by |
|---|---|---|---|---|---|
| `authState` | input | USER_ACTION | `AuthRepository` | — | via `ObserveAuthStateUseCase` |
| `currentDoorEvent` | input | PUSH | `DoorRepository` | — | via `ObserveDoorEventsUseCase` |
| `recentDoorEvents` | input | PUSH | `DoorRepository` | — | via `ObserveDoorEventsUseCase` |
| `buttonHealth` | input | PUSH | `ButtonHealthRepository` | — | — |
| `snoozeState` | input | USER_ACTION | `SnoozeRepository` | — | — |
| `serverConfig` | input | USER_ACTION | `ServerConfigRepository` | — | — |
| `allowlist` | input | USER_ACTION | `FeatureAllowlistRepository` | — | via `ObserveFeatureAccessUseCase` |
| `testNotificationSandbox` | input | USER_ACTION | `TestNotificationRepository` | — | via `ObserveTestNotificationStateUseCase` |
| `nowEpochSeconds` | input | CLOCK | `LiveClock` | — | `DoorHistoryViewModel`, `HomeViewModel` |
| `isCheckInStale` | input | CLOCK | `CheckInStalenessManager` | — | `DoorHistoryViewModel`, `HomeViewModel` |
| `watchCompanion` | input | POLL | `WearCompanionRepository` | — | — |
| `buttonHealthDisplay` | derived | — | `ComputeButtonHealthDisplayUseCase` | eager | `HomeViewModel` |
| `effectiveSnoozeState` | derived | — | `ComputeEffectiveSnoozeStateUseCase` | eager | `ProfileViewModel` |
| `watchAppStatus` | derived | — | `ObserveWatchAppStatusUseCase` | gated on `watchCompanion` | `ProfileViewModel` |

Screens observe inputs through the listed `Observe*` pass-throughs (ADR-022)
or direct manager injection. An input with no outgoing edge in the diagram is
consumed at action time inside the data layer (fetch plumbing), not observed.

```mermaid
graph LR
    authState(["authState · USER_ACTION"])
    currentDoorEvent(["currentDoorEvent · PUSH"])
    recentDoorEvents(["recentDoorEvents · PUSH"])
    buttonHealth(["buttonHealth · PUSH"])
    snoozeState(["snoozeState · USER_ACTION"])
    serverConfig(["serverConfig · USER_ACTION"])
    allowlist(["allowlist · USER_ACTION"])
    testNotificationSandbox(["testNotificationSandbox · USER_ACTION"])
    nowEpochSeconds(["nowEpochSeconds · CLOCK"])
    isCheckInStale(["isCheckInStale · CLOCK"])
    watchCompanion(["watchCompanion · POLL"])
    buttonHealthDisplay["buttonHealthDisplay"]
    effectiveSnoozeState["effectiveSnoozeState"]
    watchAppStatus["watchAppStatus"]
    ObserveAuthStateUseCase[["ObserveAuthStateUseCase"]]
    ObserveDoorEventsUseCase[["ObserveDoorEventsUseCase"]]
    ObserveFeatureAccessUseCase[["ObserveFeatureAccessUseCase"]]
    ObserveTestNotificationStateUseCase[["ObserveTestNotificationStateUseCase"]]
    DoorHistoryViewModel{{"DoorHistoryViewModel"}}
    FunctionListViewModel{{"FunctionListViewModel"}}
    HomeViewModel{{"HomeViewModel"}}
    ProfileViewModel{{"ProfileViewModel"}}
    authState --> ObserveAuthStateUseCase
    ObserveAuthStateUseCase --> HomeViewModel
    ObserveAuthStateUseCase --> ProfileViewModel
    currentDoorEvent --> ObserveDoorEventsUseCase
    recentDoorEvents --> ObserveDoorEventsUseCase
    ObserveDoorEventsUseCase --> DoorHistoryViewModel
    ObserveDoorEventsUseCase --> FunctionListViewModel
    ObserveDoorEventsUseCase --> HomeViewModel
    ObserveDoorEventsUseCase --> ProfileViewModel
    allowlist --> ObserveFeatureAccessUseCase
    ObserveFeatureAccessUseCase --> FunctionListViewModel
    ObserveFeatureAccessUseCase --> HomeViewModel
    ObserveFeatureAccessUseCase --> ProfileViewModel
    testNotificationSandbox --> ObserveTestNotificationStateUseCase
    ObserveTestNotificationStateUseCase --> FunctionListViewModel
    isCheckInStale --> DoorHistoryViewModel
    isCheckInStale --> HomeViewModel
    nowEpochSeconds --> DoorHistoryViewModel
    nowEpochSeconds --> HomeViewModel
    authState --> buttonHealthDisplay
    buttonHealth --> buttonHealthDisplay
    nowEpochSeconds --> buttonHealthDisplay
    buttonHealthDisplay --> HomeViewModel
    nowEpochSeconds --> effectiveSnoozeState
    snoozeState --> effectiveSnoozeState
    effectiveSnoozeState --> ProfileViewModel
    watchCompanion -. poll, gated .-> watchAppStatus
    watchAppStatus --> ProfileViewModel
```
