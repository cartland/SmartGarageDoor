# Room Nullability Bug

## The Problem

Room silently accepts non-nullable return types on `@Query` methods that can return zero rows, then throws `IllegalStateException` at runtime instead of failing at compile time.

```kotlin
// COMPILES but CRASHES at runtime on empty table
@Query("SELECT * FROM DoorEvent ORDER BY lastChangeTimeSeconds DESC LIMIT 1")
fun currentDoorEvent(): Flow<DoorEventEntity>
```

```
java.lang.IllegalStateException: The query result was empty,
but expected a single row to return a NON-NULL object of type
<com.chriscartland.garage.db.DoorEventEntity>.
```

## Why This Is a Bug

Kotlin's type system guarantees that non-nullable types never hold null. Room's annotation processor (`room-compiler`) generates the DAO implementation at compile time — it knows the query, knows the return type, and knows that `LIMIT 1` can return zero rows. It should either:

1. **Fail at compile time** with an error: "Query may return no rows but return type is non-nullable. Use `DoorEventEntity?`."
2. **Not emit** to the Flow when there are no rows (instead of throwing).

Instead, Room generates code that calls `__db.assertNotSuspendingTransaction()` and then throws when the cursor is empty. The non-nullable contract is violated at the Room-generated boundary, not in user code.

## When It Manifests

- Fresh app install (database empty, first network fetch hasn't completed)
- After clearing app data
- In instrumented tests on a fresh emulator
- Any race condition where a Room Flow is collected before data is inserted

The bug is timing-dependent: if data arrives before the Flow emits, the crash never happens. This makes it hard to catch in manual testing.

## The Fix

Use nullable return types for any `@Query` that can return zero rows:

```kotlin
// CORRECT — nullable, handles empty table
@Query("SELECT * FROM DoorEvent ORDER BY lastChangeTimeSeconds DESC LIMIT 1")
fun currentDoorEvent(): Flow<DoorEventEntity?>
```

Queries returning `List<T>` are safe — Room returns an empty list, not null.

Aggregate queries (`SELECT COUNT(*)`) are safe — they always return a value.

## Our Safeguards

1. **`DaoNullabilityTest`** — Source-scanning unit test that checks all `*Dao.kt` files for `@Query` methods returning non-nullable single objects. Fails the build if found.

2. **Instrumented tests** — `ComponentGraphTest` creates all ViewModels (which trigger Room queries), catching this crash on an empty database.

3. **Git hook** — Warns on push when Room/DAO files are modified, suggesting `./scripts/run-instrumented-tests.sh`.

## Affected Versions

Observed in Room 2.7.2 (our version). This behavior has existed since Room first supported Kotlin coroutines. As of April 2026, there is no compile-time check from Room for this issue.

## References

- [Our fix commit](https://github.com/cartland/SmartGarageDoor/pull/115) — Made `currentDoorEvent` nullable through DAO → DataSource → Repository chain
- [Room documentation](https://developer.android.com/training/data-storage/room/accessing-data) — Does not warn about this behavior
