---
category: reference
status: active
last_verified: 2026-04-24
---
# Repository API patterns — observation, fetch, and refresh semantics

**Who this is for:** anyone designing a repository that exposes an
observable `StateFlow<T>` plus explicit fetch / refresh commands.
Covers the design choices that silently break when not thought through,
and how to test the intended contract.

## TL;DR

When a repository owns a `StateFlow<T>` backed by a cache and exposes a
`suspend fetchX()` command:
1. Decide explicitly whether `fetchX()` always hits the network or
   short-circuits to cache. Document it.
2. If it always refreshes, null / error responses should leave the
   cache unchanged (last-known-good preserved).
3. Write one test per semantic — `fetchAlwaysRefreshesCache`,
   `nullResponsePreservesCache`, and one that documents concurrent
   behavior if you care about it.
4. Resist optimizing for coalescing unless logs prove duplicate fetches
   matter.

## The failure mode

A previous version of `CachedServerConfigRepository` in this codebase
looked like this:

```kotlin
override suspend fun fetchServerConfig(): ServerConfig? =
    fetchMutex.withLock {
        val cached = _serverConfig.value
        if (cached != null) return@withLock cached  // short-circuit
        doFetch()
    }
```

The `cached != null` guard silently made `fetchServerConfig()`
behave identically to a cached read once the cache was populated. A
caller that needed to pick up a server-side change could never see it
without restarting the process.

No test caught this. The tests that existed verified the cache
populated (init fetch) and that the state flow exposed the cached
value — both of which kept passing after the guard was added. What
was missing: a test that asserted `fetchX()` returns a *new* value
when the server's response changes.

## Design decisions to make explicit

### 1. Observation vs. fetch vs. cached read

Three distinct concepts that often get conflated:

| Concept | Shape | When to use |
|---|---|---|
| **Observation** | `val x: StateFlow<T>` | Callers want to react to changes over time |
| **Force-refresh** | `suspend fun fetchX(): T?` | Callers want a network round-trip and the cache updated |
| **Cached read** | `x.value` (direct property access) | Callers want the current cached value without I/O |

If your `fetchX()` returns the cached value when cached, you've merged
fetch and cached read into one operation with a non-obvious name. Most
people expect `fetch` to hit the source of truth. Document otherwise
explicitly or don't do it.

### 2. Null return semantics

When `fetchX()` fails (HTTP error, network exception, timeout), two
reasonable behaviors exist:

- **Null = failure report, cache untouched.** Last-known-good survives.
  Callers that care about "did the fetch succeed?" check the return.
  Callers that care about "what's the latest known value?" read
  `x.value`.
- **Null = authoritative absence, cache cleared.** Rare. Only if the
  server genuinely can revoke a value.

The first is almost always what you want. A transient network blip
should not blow away the last successful response.

```kotlin
override suspend fun fetchX(): T? = fetchMutex.withLock {
    val fresh = try { callNetwork() } catch (e: Exception) { null }
    if (fresh != null) _state.value = fresh  // only write on success
    fresh  // return value tells the caller if the fetch itself worked
}
```

### 3. Concurrent coalescing — usually YAGNI

It's tempting to cache the in-flight `Deferred` so that two concurrent
callers share one network request. This is correct-looking and subtly
more code (an `inFlight: Deferred<T>?` field with a careful
completion-check).

In most apps it's unnecessary:
- Most concurrent `fetchX()` calls come from init + a null-fallback.
  Rare.
- An extra network call costs milliseconds, not correctness.
- The coalescing code has its own failure modes (stranded `Deferred`s,
  cancellation edge cases).

**Default:** a plain `Mutex.withLock` that serializes concurrent
callers is enough. Each caller's fetch hits the network, writes on
success, and returns. Revisit coalescing only if Rule 9 observability
logs show duplicate fetches mattering.

## How to test the contract

### Test 1: `fetchX()` always refreshes (force-refresh contract)

```kotlin
@Test
fun fetchXAlwaysRefreshesCache() = runTest {
    // Init populates cache with v1.
    dataSource.setResult(Success(v1))
    val repo = buildRepo(dataSource, externalScope)
    advanceUntilIdle()
    assertEquals(v1, repo.x.value)

    // Server changes. fetchX() must see the new value.
    dataSource.setResult(Success(v2))
    val refreshed = repo.fetchX()
    assertEquals(v2, refreshed)
    assertEquals(v2, repo.x.value)
    assertEquals(2, dataSource.callCount)  // init + refresh
}
```

This test is the single highest-leverage one. It would have caught the
bug above the day it landed.

### Test 2: null response preserves cache

```kotlin
@Test
fun nullFetchResultPreservesCachedValue() = runTest {
    dataSource.setResult(Success(v1))
    val repo = buildRepo(dataSource, externalScope)
    advanceUntilIdle()
    assertEquals(v1, repo.x.value)

    dataSource.setResult(ConnectionFailed)
    val result = repo.fetchX()
    assertNull(result)           // fetch itself failed
    assertEquals(v1, repo.x.value)  // cache untouched
}
```

Documents the "null = failure report" contract. Prevents a future
"clean up error handling" PR from accidentally blowing away the cache.

### Test 3: exception resilience

```kotlin
@Test
fun fetchSwallowsDataSourceExceptions() = runTest {
    val throwingDataSource = object : DataSource {
        var throwNext = true
        override suspend fun call(): Result<T> {
            if (throwNext) { throwNext = false; throw RuntimeException("transient") }
            return Success(v1)
        }
    }
    val repo = buildRepo(throwingDataSource, externalScope)
    advanceUntilIdle()  // init fetch throws internally
    assertNull(repo.x.value)

    // Subsequent fetch works — no mutex leak, no uncaught exception.
    val result = withTimeout(1_000) { repo.fetchX() }
    assertEquals(v1, result)
}
```

Data-source exceptions happen. The repo must catch them, leave the
cache alone, and allow subsequent fetches to proceed. If an exception
bubbles out of a `launch`ed init fetch on an `externalScope`, it can
crash the scope and strand every future consumer.

## Minimum viable defense

For every state-owning repository you write:

- [ ] One `fetchAlwaysRefreshesCache` test with v1 → v2 server change.
- [ ] One `nullResponsePreservesCache` test.
- [ ] One exception-resilience test if the data source can throw
      (usually: always).
- [ ] A KDoc on `fetchX()` stating the contract in one sentence:
      "Force-refresh; returns null on failure; cache untouched on null."

Together these take ~100 lines of test code and encode the repository's
contract so it survives cleanup refactors.

## Anti-patterns to avoid

### Anti-pattern: merging cached read and force-refresh

```kotlin
// Don't — unclear contract.
suspend fun getServerConfig(): ServerConfig? =
    cached ?: fetch()  // is this cached? force-refresh? both?
```

Split it into two: expose the cached read as `val x: StateFlow<T>` and
provide a separate `fetchX()` command. Callers pick the semantic they
want.

### Anti-pattern: writing null to state on error

```kotlin
// Don't — transient errors clear last-known-good.
is Failure -> _state.value = null
```

Leave the state alone. The return value of `fetchX()` is where you
signal failure to the caller.

### Anti-pattern: coalescing by short-circuit

```kotlin
// Don't — breaks force-refresh silently.
if (cached != null) return cached
```

As above. If you want coalescing, use in-flight `Deferred` tracking,
and only if logs prove duplicate fetches are a real problem.

### Anti-pattern: init fetch on caller's scope

```kotlin
class Repo(scope: CoroutineScope) {  // intended as externalScope
    suspend fun fetchOnInit() { ... }  // whoever calls this first, wins
}
```

The init fetch belongs on `externalScope` (an app-lifetime scope), not
on `viewModelScope` or any caller scope. Otherwise a VM cancellation
mid-fetch can strand the StateFlow at its initial value (e.g.,
`Loading` forever). See ADR-018 for the same pattern applied to the
platform auth listener.

```kotlin
class Repo(
    private val externalScope: CoroutineScope,
) {
    init {
        externalScope.launch { fetch() }  // init on app scope
    }
}
```

## Reference

- Contract-first repo design is part of ADR-022 (state-y data owned by
  repository `StateFlow`).
- Scope discipline for init fetches is ADR-018 / ADR-019.
- Observability logging at state-write sites is ADR-021 Rule 9.

## Provenance of this guide

Written after a regression in `CachedServerConfigRepository` where a
`cached != null` short-circuit made `fetchServerConfig()` silently stop
refreshing after the first successful call. The bug shipped undetected
because no test exercised "server value changes → fetch picks up new
value." The tests encoded "cache populates" and "state flow emits,"
both of which kept passing.

The "minimum viable defense" tests in this guide are the smallest set
of checks that would have caught the regression at merge time.
