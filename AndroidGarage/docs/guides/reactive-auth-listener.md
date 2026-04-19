# Reactive auth listener pattern

**Who this is for:** anyone modeling authentication state in a
mobile app or any app where auth is a stream of changes (sign-in,
sign-out, token refresh) rather than a one-shot login operation.
Applies to Firebase Auth, custom OAuth flows, biometric unlocks,
or any SDK that exposes an "auth state" callback.

## TL;DR

Auth state is event-driven: the platform's auth SDK tells you when
sign-in, sign-out, or token refresh happens. Your repository should
subscribe to that callback on an app-lifetime scope and translate
each event into a `StateFlow<AuthState>` write. The rest of the app
observes; no polling, no one-shot `getCurrentUser()` calls scattered
around. Sign-in and sign-out are fire-and-forget commands; the
listener reflects the result.

## The anti-pattern: imperative polling

```kotlin
// Don't do this.
class AuthViewModel {
    private val _authState = MutableStateFlow(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState

    init {
        viewModelScope.launch {
            while (true) {
                val user = firebase.currentUser
                _authState.value = if (user != null) Authenticated(user) else Unauthenticated
                delay(1000)
            }
        }
    }

    fun signIn(token: String) {
        viewModelScope.launch {
            val user = firebase.signInWithCredential(token)
            _authState.value = if (user != null) Authenticated(user) else Unauthenticated
        }
    }
}
```

Problems:
- **Wastes battery** polling for state that rarely changes.
- **Misses fast transitions** — token refresh → re-sign-in happens
  faster than the polling period, state flaps.
- **Duplicates logic** — both `init` and `signIn` write the state;
  they can disagree.
- **Scope-bound to the VM** — if the VM is destroyed mid-sign-in
  (configuration change), the state update is cancelled with the
  `viewModelScope`. See below for the cancellation pitfall.

## The pattern: listener-driven repository

```kotlin
class FirebaseAuthRepository(
    private val authBridge: AuthBridge,
    externalScope: CoroutineScope,  // app-lifetime, NOT viewModelScope
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    init {
        externalScope.launch {
            authBridge.observeAuthUser().collect { userInfo ->
                val newState = when (userInfo) {
                    null -> AuthState.Unauthenticated
                    else -> {
                        val token = authBridge.getIdToken(forceRefresh = false)
                        if (token != null) AuthState.Authenticated(User(userInfo, token))
                        else AuthState.Unauthenticated
                    }
                }
                _authState.value = newState
            }
        }
    }

    // Commands are fire-and-forget. Listener picks up the state change.
    override suspend fun signInWithGoogle(idToken: GoogleIdToken): AuthState {
        authBridge.signInWithGoogleToken(idToken)
        return _authState.value  // may still be pre-sign-in;
                                 // callers should observe, not use this return
    }

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated  // eager UI update
        authBridge.signOut()  // listener also fires and confirms
    }
}
```

Key properties:

1. **The listener is the source of truth.** `sign-in` kicks off the
   SDK; the listener observes the result and updates state. Commands
   do not race against the listener.
2. **`externalScope`, not viewModelScope.** If the VM is cancelled
   mid-sign-in (config change, user navigates away), the state update
   must still happen. App-lifetime scope guarantees this.
3. **State is a `StateFlow<AuthState>`.** Current value always
   available via `.value`. Observers see every transition.
4. **Sign-out is eager.** Optimistic UI update before the SDK call;
   listener confirms shortly after. Avoids a "stale signed-in state"
   flash while the SDK processes sign-out.

## The cancellation pitfall

This is the subtle one. The Android `ViewModelScope` is cancelled on
VM destruction (config change, navigate away, sign-out-triggered
recreate). If your auth update runs on `viewModelScope`, it can be
cancelled after the network call returns but before the state write:

```kotlin
// viewModelScope flow
viewModelScope.launch {
    val user = firebase.signIn(...)     // network call
    // <-- VM destroyed here, coroutine cancelled
    _authState.value = Authenticated(user)  // never runs
}
```

The `StateFlow<AuthState>` is stuck at its pre-sign-in value forever
(for this VM). If the VM holds "the" state, new VMs of the same class
will have fresh state but anything that references the old VM's flow
is stranded.

Fix: use an app-lifetime `externalScope` for state writes. The VM
observes; it does not write. Commands either delegate to the
repository (which uses `externalScope`) or return their result
directly without touching flows.

## AuthState modeling

A useful three-case sealed type:

```kotlin
sealed interface AuthState {
    object Unknown : AuthState            // listener hasn't fired yet
    object Unauthenticated : AuthState    // signed out or never signed in
    data class Authenticated(val user: User) : AuthState
}
```

- `Unknown` exists because the listener is async. The first emission
  after app launch might take a few hundred ms. Distinguishing
  "haven't checked yet" from "checked and signed out" prevents UI
  flash during the check.
- `Authenticated` carries the `User` object (display name, email,
  token). Makes the state self-contained; no `getCurrentUser()`
  lookup elsewhere.
- No explicit `SigningIn` / `SigningOut` state. Those are
  presentation concerns (a spinner in the UI) — model them in the
  ViewModel as a separate `SignInAction` flow, not in the domain
  `AuthState`. Keeps the invariants cleaner.

## Token refresh

ID tokens typically expire in ~1 hour. Two places need freshness:

1. **At the auth state level.** When the SDK auto-refreshes, the
   listener fires again with the new token. The state flow updates.
2. **At the point of use.** Before a network call that needs a valid
   token, call a `ensureFreshIdToken()` UseCase that checks expiry
   and force-refreshes if needed.

```kotlin
class EnsureFreshIdTokenUseCase(private val authRepo: AuthRepository) {
    suspend operator fun invoke(authState: AuthState.Authenticated): FirebaseIdToken {
        val current = authState.user.idToken
        if (current.exp > (System.currentTimeMillis() / 1000) + 60) return current
        return authRepo.refreshIdToken() ?: current  // fallback
    }
}
```

The repository's `refreshIdToken()` writes the new token into
`_authState` so the rest of the app sees it without polling.

## Minimum viable defense

- [ ] Auth state is a `StateFlow<AuthState>` on a `@Singleton`
      repository, not in a ViewModel.
- [ ] Listener subscription launches on an app-lifetime scope
      (`externalScope` / `ApplicationScope`), not any VM scope.
- [ ] Commands (`signIn`, `signOut`, `refreshToken`) write state on
      `externalScope` — either directly or via the listener.
- [ ] At least one test proves a VM cancellation mid-sign-in does
      not strand the auth state.
- [ ] UI observes the `StateFlow`; no `getCurrentUser()` one-shot
      reads scattered in screens.

## Common pitfalls

### Pitfall: using `viewModelScope` for the listener

If the listener itself runs on a VM scope, destroying the VM
unsubscribes the listener. State updates stop. New VMs don't
resubscribe because the singleton repo's `init` already ran (once).

**Fix:** the listener ALWAYS runs on `externalScope` in the
repository's `init { }`. One subscription per process lifetime.

### Pitfall: conflating sign-in command flight with auth state

Putting `SigningIn` in `AuthState` couples domain state to UI flow.
Makes `when (authState)` exhaustively check UI-only transitions in
every consumer.

**Fix:** separate the domain state from the presentation state.
`AuthState` has Unknown / Unauthenticated / Authenticated. A
VM-local `SignInAction` (Sending, Succeeded, Failed.X) handles the
command lifecycle.

### Pitfall: not modeling Unknown

"No signed-in user" defaulting to `Unauthenticated` means the UI
shows the sign-in button during the ~300ms between app launch and
the first listener emission. Users see a flash.

**Fix:** start as `Unknown`, let the UI show a neutral loading
state, transition to Authenticated or Unauthenticated when the
listener fires.

### Pitfall: ignoring sign-out race

If sign-out triggers a VM recreation (e.g., navigation reset), and
your sign-out command writes state on `viewModelScope`, the write
can be cancelled when the VM dies. User sees a signed-in UI briefly
after tapping "Sign out."

**Fix:** eager optimistic sign-out state write in the repository,
on `externalScope`. The SDK call follows. The listener confirms.
Three layers all move in the same direction.

## Reference

- [Firebase Auth `AuthStateListener`](https://firebase.google.com/docs/auth/android/manage-users#get_the_currently_signed-in_user)
- [`repository-api-patterns.md`](repository-api-patterns.md) — the
  `StateFlow` + fetch contract for repositories.
- [`compose-nav3-vm-scoping.md`](compose-nav3-vm-scoping.md) — why
  VMs can't own auth state in a multi-instance world.
- [`kotlin-inject.md`](kotlin-inject.md) — making the auth
  repository an actual singleton.
