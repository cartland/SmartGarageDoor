---
category: plan
status: active
---

# One door, six surfaces — cross-surface UX strategy

> Audited 2026-09-28 against `main` at `8641380c` (Android 2.25.0, iOS 0.2.2,
> Wear 0.9.2). Four specialised review passes — design language, journeys and
> information architecture, capabilities and platform limits, shared-model
> drift — each read the code and the captured galleries. Every finding that
> carries weight below was then re-checked in source before it was written
> here; those are marked **[verified]**. Where a pass could not settle
> something, the doc says so rather than guessing.

## 0. The thesis

The product has done the hard architectural thing right: **one shared verdict
about the door** (`GlanceStatus`, `DataFreshness`, `StatusHeadline`) is consumed
by every surface, so no two surfaces can *reach* different conclusions. What it
has not yet done is make those surfaces **say and show** that one verdict the
same way. The single most important state — *we cannot vouch for this reading*
— is worded five different ways and painted four different greys. The glance
surfaces are blind to pushes the phone already received. And the one state a
glance most needs to shout — *the door is stuck* — is silent on all three of
them.

So the strategy is not "make everything match". It is:

1. **Consistent on the verdict and its vocabulary.** One word family for "not
   confirmed", one grey, one place that decides the second line. Where the
   surfaces are describing the same fact, they use the same words and the same
   treatment, enforced by tests rather than comments.
2. **Differentiated on the gesture and the medium.** A watch holds, a phone
   double-taps, a complication has seven characters, a widget has thirty-minute
   updates. Those are strengths to design *into*, and the audit found them
   mostly well-reasoned already. The plan leans harder into them (iOS gets the
   glance surface it lacks; Wear gets the pre-act information its own tile
   already shows).
3. **Honest before polished.** Three fixes ship before anything cosmetic:
   the light-mode contrast failure, the push-blind glances, and the silent
   stuck door.

## 1. The six surfaces on the glance → act spectrum

```
glance ────────────────────────────────────────────────────────► act + manage

Complication      Tile          Widget         Wear app        iOS app          Android app
7 chars,          word +        word +         hold, live      two-tap,         two-tap, voice*,
face colours      live dur.     instant        voice; thin     diagnose,        diagnose, history,
                                               diagnose        history, snooze  snooze, dev, widget
0 taps            1 swipe       0 taps         raise wrist     1 tap            1 tap
```

| | Android app | Android widget | iOS app | Wear app | Wear tile | Complication |
|---|---|---|---|---|---|---|
| **Kept current by** | FCM push + cold-start fetch | launcher, 30-min floor; refresh per render; **not on push** | poll 15 s while active (push config deployed, default not flipped) | poll 10 s / 2 s while visible; no push | freshness request ≥60 s; re-render on change | 600 s period + ≤8 s refresh budget |
| **Time in state** | "Since 9:47 AM · 2 hr 14 min", live | "since 3:42 PM" (instant, by design) | same as Android | **nothing** | live duration (renderer-counted) | live duration (face-counted) |
| **Can't vouch for it** | grey door + banner + Retry + red pill | "Stale" | same as Android | grey door, **no words** | "Not confirmed" | "Stale" leads |
| **Stuck door** | "Opening, taking longer than expected" chip | "Opening" | chip | glyph overlay, label says "Opening" | "Opening" | "Opening" |
| **Acts on the door** | two taps (8 s window) | no (3 tests) | two taps | hold 2 s + ring + haptics | no | no |
| **Voice** | yes, **developer-flag gated**, system dialog | — | **none** | yes, live, everyone | — | — |
| **Owns its pixels** | yes | yes (host resolves day/night) | yes | yes, always dark | yes, dark only | **no** — face draws it |

**What each platform is missing.** iOS has no glance surface at all — no
widget, no watch, no Live Activity — and no voice. Android has no lock-screen
or Quick Settings presence. Wear has no snooze, no history, no sign-out, and
its app shows less about the door than its own tile does. `GlanceStatus` was
built to answer exactly the question an iOS widget would ask; nothing consumes
it there yet.

## 2. What is actually shared today

The distinction that matters is *enforced by code and tests* versus *matches
today because someone was careful*.

| Element | Shared how | Enforced? |
|---|---|---|
| The verdicts: `DataFreshness`, `StatusHeadline`, `GlanceStatus`/`Liveness`, `DoorWarning`, `SinceStatus`, `CheckInStatus`, `HomeAlert`, `History*`, `SnoozeRowStatus` | `presentation-model` | **Yes** — commonTest, exhaustiveness |
| Door fill colours (3 families × fresh/stale × light/dark) | `GarageDoorPalette` → every surface | **Yes** — code + `GarageDoorPaletteTest` |
| Door geometry, 12 s slide, replay memory | `GarageDoorGeometry` / `DoorAnimation` | constants yes; **the drawing routine is three hand copies** (Android and Wear near line-identical) |
| Muting (alpha 0.55, Rec.709 luma, 250 ms) | `FreshnessTint` | 4 of 5 pixel-owning surfaces; **the widget does not use it** [verified] |
| The six door words, "Connecting…", "No signal" | per-platform strings | convention + two narrow tests (widget↔Home strings; tile↔Wear strings, 2 of 8 cases) |
| "Since X · Y" grammar, section-header grammar, pill grammar | per-platform | convention only |
| Spacing tokens (`Spacing.kt` ↔ `GarageSpacing.swift`) | hand-mirrored | **nothing pins them**; Wear has no token file |
| The 11-minute staleness threshold | `CheckInStatusMapper`, `CheckInStalenessManager`, `DoorCommandGate.ts`, `verdict_table.json` | server↔fixture yes; **Kotlin↔Kotlin and Kotlin↔fixture no** |

Two shared types have **zero consumers anywhere**: `HomeScreenState` and
`DoorHistoryScreenState` (`presentation-model`). ADR-031's status header still
reads "Plan only; not yet implemented" although its phases shipped.

## 3. Findings, ranked by what a user would feel

### 3.1 One verdict, five voices, four greys

For the single shared `DataFreshness.STALE` with a known door:

| Surface | Words | Grey |
|---|---|---|
| Android / iOS app | "Not receiving updates from server" + Retry | palette `_STALE_` entry **then** luma + alpha when the check-in is stale; `_FRESH_` then luma + alpha on a fetch error — two greys on one card |
| Wear app | *(none — grey only)* | `_FRESH_DARK` + luma + alpha |
| Wear tile | "Not confirmed" | `_FRESH_DARK` + luma + alpha (its KDoc refuses the `_STALE_` entries "so mixing the two would grey the door twice") |
| Complication | "Stale" (short) / "…, not confirmed" (long) | face-owned |
| Widget | "Stale" | `_STALE_` entry **only**, no luma, no alpha — `OPEN_STALE_LIGHT` `#9A655C` still reads as brick red [verified] |

And for "we have never heard anything": everything says "No signal" except the
complication, which says "No data" in both its short and long forms (the long
form has room). Computed greys for the same *open* door: `#434343`, `#707070`,
`#555555`, `#9A655C` depending on which surface and *why* it is muted — and on
the phone the more worrying case (stale check-in) is the *lighter* grey.

Files: `androidApp/.../ui/home/HomeContent.kt:349-394`,
`iosApp/.../GarageDoorCanvas.swift:83-86`, `wearApp/.../tile/GarageTilePresentation.kt:89-108`,
`androidApp/.../widget/GarageWidgetColors.kt:67-69`, `domain/.../GarageDoorPalette.kt:47-60`,
`wearApp/res/values/strings.xml:88,113,117,133`, `androidApp/res/values/strings.xml:307,425`.

### 3.2 The glance surfaces are push-blind

Nothing outside `widget/GarageDoorWidgetReceiver.kt` ever calls
`GlanceAppWidget.updateAll` [verified]. When an FCM door event lands
(`fcm/FcmMessageHandler.kt:98-105`), Room is updated, the Home screen is
current — and the home-screen widget keeps saying "Closed, since 3:42 PM" for up
to the launcher's 30-minute cycle. The same shape on the watch: the Wear app's
poll sees the door move, and neither `TileService.getUpdater().requestUpdate`
nor `ComplicationDataSourceUpdateRequester` is ever called, so the face can
show the old state for up to ten minutes after the user *watched* it change in
the app. Both fixes are small and respect the platform floors (they are the
ceiling, not a replacement).

### 3.3 A stuck door is silent on every glance surface

`DoorHeadlineMapper` collapses `OPENING_TOO_LONG → OPENING` and `GlanceStatus`
carries no warning field [verified]. The phone shows "Opening, taking longer
than expected"; the widget, tile and complication show "Opening" with a live
duration that grows — an accurate number attached to an unqualified word, on
the surfaces someone glances at precisely when they wonder whether the door
ever closed. `GlanceStatus` needs a `warning` (the shared `DoorWarning` already
exists) and each surface needs a word for it within its budget.

### 3.4 Accessibility defects in the shipped product

- **Light-mode contrast fails on Android.** `primaryLight #A6C8FF` on
  `backgroundLight #FFFBFE` = **1.66:1** — every section header ("STATUS",
  "REMOTE CONTROL", "ACCOUNT", day labels). `tertiaryLight #A5C9E9` on
  `surfaceContainerLight #E1E2DC` = **1.33:1** — the history transit tags, so
  "Took 4 min to open, longer than expected" is close to invisible. WCAG AA is
  4.5:1. Dark mode is fine. [verified by computation]
  `ui/theme/Color.kt:24,32`; `HomeContent.kt:328`; `SettingsContent.kt:386`;
  `HistoryContent.kt:231,443,450`.
- **The Wear hold cannot be performed by a screen reader.** The door is
  `pointerInput`-only with no semantics role or action (`HeroScreen.kt:418-441`).
  The fix must *not* be a single `onClick` — that would swap the hold guard for
  one activation.
- **The complication's `contentDescription` is the bare door word** in every
  state (`GarageDoorComplicationService.kt:134-135`) — a screen reader hears
  "Open" for a reading the visual design refuses to leave unqualified.
- The widget declares no semantics; TalkBack reads two texts with no
  relationship (not verified on device).

### 3.5 The acting surfaces under-serve the pre-act question

The watch is the only surface where you can act **and** cannot read how long
the door has been in its state: the hero shows the label and the hold hint,
nothing else (`HeroScreen.kt:256-275`). Its own tile and complication answer
"how long" — so tapping the tile to see more shows less. When a known reading
goes stale the hero goes grey and *says nothing*; the tile beside it manages
"Not confirmed" in the same width, so room is not the constraint the KDoc
claims. And the Wear hero re-derives the headline from the raw `DoorPosition`
in a hand-written 9-arm `when` instead of consuming the shared `DoorHeadline`
the tile and complication already use (`HeroScreen.kt`, `doorStateLabel`) — the
one screen behind those glances is the one that could drift from them.

### 3.6 Dead ends and copy that isn't true

- **Wear signed-out is a dead end.** If the phone relay does not sign the watch
  in, the only affordance is a "Sign in" button that Google Play Services
  rejects on Wear OS → "Sign-in failed" for five seconds, pointing nowhere
  (`HeroScreen.kt:336-351`, `WearHomeViewModel.kt:446-466`).
- **Phone voice says "Tap to cancel"; the tap restarts listening**
  (`VoiceCommandController.kt:270-276`). The content description admits it
  ("Cancel and speak again"); the visible label does not.
- **Android's simulated voice says "Command sent / The door should respond
  shortly"** against a pretend door — the exact claim Wear's four-signal
  rehearsal design exists to forbid (`VoiceControlCard.kt:214-243`).
- **An HTTP 403 is worded "Server error"** while the pill beside it says
  "Unauthorized" — `ActionError` has no `Forbidden` variant; the watch says
  "Server did not respond" for the same case.
- **Snooze invites a deterministic failure**: neither phone gates the sheet on a
  settled door, so during OPENING/CLOSING it 404s and the message says "Try
  again" when retrying cannot succeed (`docs/SNOOZE_BEHAVIOR.md:45-82`).
- **The button-health pill is labelled "TEMPORARY (debug)"** in its header
  comment, its labels are Kotlin literals, it shipped on both phones, and its
  info sheet explains two of its five states (`HomeContent.kt:256-258`,
  `RemoteButtonHealthPill.kt:146-172`).
- **The check-in info sheet says the pill "shows 'no signal'"** — it never does;
  it flips an icon and turns red.
- `HistoryFormatter.kt`'s KDoc claims iOS "renders History the same way"; iOS
  honours the device's 24-hour setting and Android pins `"h:mm a"` (in
  `Locale.US` on History, default locale on Home — three clock policies on one
  platform). ADR-035 already says 12/24-hour is a locale property; iOS is the
  conformant side.

### 3.7 Voice availability is inverted

Voice is a hero-screen feature for every signed-in watch user, a
developer-allowlist feature on the phone, and absent on iOS. The safest,
most-guarded implementation (three gates, server verdict) is the least
available.

### 3.8 Enforcement is mostly convention

- No test loads the three string sources together; cross-platform vocabulary
  is checked by nobody.
- Three Swift `default:` arms on shared enums (`HomeViewModelWrapper.swift:354`,
  `HomeScreen.swift:465,482`) mean adding a case would not fail the iOS build.
- `checkHardcodedColors` scans `androidApp/src/main/java` only — not
  `wearApp`, not the widget package, not iOS.
- iOS `Localizable.xcstrings` has **zero plural variations**; plurals are Swift
  ternaries — exactly the failure ADR-035 §Why-2 names.
- Android's check-in pill and button-health labels are Kotlin literals the
  `checkNoLiteralStringsInCompose` regex cannot see.
- Spacing tokens, the same-day clock rule, the glance second-line precedence
  (written three times, agreeing only because the mapper withholds the
  instant), and the 11-minute threshold are all unpinned.

### 3.9 The review set is stale

Android reference PNGs date from July (they still show the removed "Checking…"
pill and the pre-#1153 button label); iOS and Wear are September. iOS dark mode
has **never been captured** (all 33 snapshots are light). The four settle-window
Home fixtures CLAUDE.md calls "the design" have no Android PNG. The widget has
no capture, no gallery row, and no picker preview. Design review is comparing
two different months.

### 3.10 Doc drift I own

CLAUDE.md § "The phone's home-screen widget" claimed the widget "greys by
exactly the rule and amount every other surface does". The code did not, and
the tile's own KDoc explains why the mechanism the widget uses is the wrong
one. CLAUDE.md § "The Wear complication" also still described the 0.9.0 design
("the AGE leads", "`6h ago`", "days capped at 99") after 0.9.1 removed the age.
Both are corrected in the same change as this document.

## 4. The decision rule

> **Consistent where two surfaces describe the same fact. Differentiated where
> the medium or the gesture differs.**

| Axis | Rule | Because |
|---|---|---|
| The verdict (what is true) | one shared type, everywhere | already so; keep it |
| Vocabulary for a shared verdict | one word family per concept, per language; a surface may *shorten* it under a hard budget but not *rename* it | "Stale" vs "Not confirmed" vs a sentence is three claims for one fact |
| Colour → meaning (door families, muting, caution vs alarm) | one rule, one output, on every pixel-owning surface | a phone + watch user sees the same door |
| Typography, chrome, navigation, native controls | platform idiom | the app should feel native; recorded in `UI_FIDELITY_TIERS.md` |
| Confirm gesture | per surface, each deliberate: two-tap / hold / none | the strongest guard each medium can express |
| Time in state | per medium: live duration where the renderer counts; instant where it cannot; **never a number we compute and cannot keep current** | `GlanceStatus` KDoc rule; the widget is the proof it works |
| Text budget | per medium; the complication's seven characters are a feature | honesty in the room available |
| Availability of voice | should not depend on which pocket the device is in | see §3.7 |

## 5. The plan

Phases are ordered by user harm, then by leverage. Effort: S = one focused PR,
M = a few PRs or one substantial one, L = a project.

### Phase 0 — Truth (do first; all S)

| # | Change | Files | Why first |
|---|---|---|---|
| 0.1 | **Legible light mode.** Section headers → `onSurfaceVariant` (what iOS already does); transit tags → a real caution role, not `tertiary`. | `Color.kt`, `HomeContent.kt:328`, `SettingsContent.kt:386`, `HistoryContent.kt:231,443,450` | 1.66:1 and 1.33:1 on shipping screens |
| 0.2 | **Push-triggered widget repaint.** `GarageDoorWidget().updateAll(context)` after `handleDoorMessage` succeeds and after user-initiated fetches. | `fcm/FcmMessageHandler.kt:98-105`, `widget/` | the phone knows; its home screen doesn't |
| 0.3 | **Wear app → glance nudge.** Observe `currentDoorEvent` on `applicationScope`; on change call `TileService.getUpdater().requestUpdate` + `ComplicationDataSourceUpdateRequester.requestUpdateAll()`. | `GarageWearApplication.kt`, `tile/`, `complication/` | the face shows a door the user just watched move |
| 0.4 | **Stuck door reaches the glances.** Add `warning: DoorWarning?` to `GlanceStatus`; widget/tile word it ("Opening · stuck" / "Stuck"), complication uses it as the leading text within seven characters ("Stuck", title "Opening"). | `presentation-model/GlanceStatus.kt`, the three `*Words.kt` | the alarm case is silent |
| 0.5 | **Wear signed-out copy.** Replace "Sign in"/"Sign-in failed" with "Sign in on your phone" when the relay reports signed-out; keep the local button as the secondary path. | `HeroScreen.kt:336-351`, `WearHomeViewModel.kt:446-466`, Wear `strings.xml:139-142` | the only affordance cannot succeed |
| 0.6 | **Fix the doc drift** in CLAUDE.md (widget muting claim; complication 0.9.0 text) and `HistoryFormatter.kt`'s false parity KDoc; update ADR-031's status header. | `CLAUDE.md`, `HistoryFormatter.kt`, `DECISIONS.md` | false statements in load-bearing docs |

### Phase 1 — One verdict, one voice (consistency; S–M)

| # | Change | Files |
|---|---|---|
| 1.1 | **One word family for "not confirmed".** Tile keeps "Not confirmed"; widget "Stale" → "Not confirmed" (it has room); complication long text already says it, short stays "Stale" (budget); **Wear hero gains the same one-line word** when `isSpoken && hasData`; complication "No data" → "No signal". Phone/iOS banner unchanged (it is an alert with an action, not a label). | Wear `strings.xml`, Android `strings.xml:425`, `GarageComplicationWords.kt`, `HeroScreen.kt:256-276` |
| 1.2 | **One grey.** Always drain the `_FRESH_` base through `FreshnessTint` (luma + alpha) on every pixel-owning surface; stop feeding `isStale` into `doorColorSet`; retire the six `_STALE_` palette constants once nothing consumes them; widget adopts `FreshnessTint` via Glance `ColorProvider`. Pin phone + widget with tests shaped like `GarageTilePresentationTest.aMutedDoorIsFullyGreyAndDimmed`. | `HomeContent.kt:349`, `GarageDoorCanvas.swift:83-86`, `GarageWidgetColors.kt`, `GarageDoorPalette.kt:47-60` |
| 1.3 | **The second line is decided once.** `GlanceSubline { Duration(since) \| NotConfirmed \| Nothing }` computed in `GlanceStatusMapper`; widget, tile, complication map it. Removes three copies of a precedence rule that currently agree by accident. | `GlanceStatus.kt`, `GarageWidgetSubline.kt`, `GarageDoorTileLayout.kt`, `GarageComplicationWords.kt` |
| 1.4 | **Wear hero words the shared headline.** Consume `DoorHeadline` in the `Door` arm; delete the 9-arm `when`; extend the tile↔screen word test to all eight cases. | `HeroScreen.kt` (`HeroScreenMappers`), `GarageTilePresentationTest` |
| 1.5 | **Caution is a shared severity.** `DoorWarning`/history warnings carry `ADVISORY \| ALARM`; both phones colour advisory amber (Android currently blue `tertiary`, iOS `.orange`). | `presentation-model/DoorWarning.kt`, `HistoryRow.kt`, `HistoryContent.kt:441-450`, `HistoryScreen.swift:150-153` |
| 1.6 | **One warning vocabulary per platform.** iOS derives its alert tint from the theme (one red beside the brand door red, not system `.red`); one triangle variant. Both phones adopt the same remote-button outcome rule — either colour Done/failed on both or neither (iOS colours, Android greys everything after confirm). | `GarageColors.swift`, `HomeScreen.swift:342,357,408-422,635-642`, `GarageDoorButton.kt:110-121` |
| 1.7 | **Accidental vocabulary drift only.** iOS adds the missing "Last change time unknown" arm; "clicked" → "tapped"; voice `NO_SPEECH` / `NOT_A_COMMAND` / `DOOR_STATE_CHANGED` and "button" vs "remote" pick one wording each; Wear remote-button `ServerFailed` aligns with the phone or is recorded as deliberate. | Android/Wear `strings.xml`, `Localizable.xcstrings` via the sync script, `HomeScreen.swift:244` |
| 1.8 | **Share the since-clock decision and settle the hour cycle.** `SinceClock { TimeOnly \| DateAndTime }` in `SinceStatusMapper` (kotlinx-datetime, as `HistoryMapper` already does); Android/widget/iOS consume it; Android moves to locale hour-cycle (`FormatStyle.SHORT`, already used in `TimeFormats.kt`). | `SinceStatus.kt`, `HomeStatusFormatter.kt`, `HistoryFormatter.kt`, `HomeViewModelWrapper.swift:clockText` |

### Phase 2 — Acting surfaces answer the pre-act question (M)

| # | Change | Files |
|---|---|---|
| 2.1 | **Wear hero "since" line.** Reuse `SinceStatus`/`ElapsedDuration` under the label; share the hint slot at rest, yield to the hint mid-hold. Size the door from the measured label block so the label never crosses the frame (`DOOR_WIDTH_FRACTION` is 0.46; the KDoc says 0.48 and describes a slot that no longer exists). | `HeroScreen.kt:218-276,705-727`, `HeroLayout.kt`, `WearHomeViewModel.kt` |
| 2.2 | **Wear hold for accessibility services.** Two `customActions` — "Arm the remote", "Confirm" — driving the existing `ButtonStateMachine` two-tap path with its 8 s window. Never a single `onClick`. Positive-control test like `GarageDoorTileSafetyTest`. | `HeroScreen.kt:418-441` |
| 2.3 | **Complication `contentDescription` carries the qualifier** (duration when live; "not confirmed" when stale) via a `ComplicationText` template. Widget gets `semantics { contentDescription = "$headline, $subline" }` and `maxLines`. | `GarageDoorComplicationService.kt:134-158`, `GarageDoorWidget.kt` |
| 2.4 | **Snooze gated on a settled door**, worded "Snooze once the door settles"; drop "Try again" from the event-changed message; add a snooze entry point on Home's warning chip for OPEN. | `ProfileContent.kt:225-239`, `SnoozeBottomSheet.kt`, `SettingsScreen.swift:309-331`, `strings.xml:234` |
| 2.5 | **Phone voice tells the truth.** Either the label becomes "Tap to cancel and retry" or the tap becomes cancel-only like Wear. Android's simulated voice gets Wear's per-surface wording (`VoiceSurfaceMode` port): "Would open the door", "Nothing was sent". | `strings.xml:166-167,185`, `VoiceControlCard.kt`, `SimulatedVoiceBottomSheet.kt`, `VoiceCommandController.kt:270-301` |
| 2.6 | **Finish the button-health pill.** Labels to resources; explain all five arms in the info sheet; typed `ActionError.Forbidden` so a 403 says "Not allowed for this account" on phone and watch; correct the check-in sheet's "shows 'no signal'". | `RemoteButtonHealthPill.kt`, `HomeContent.kt:256-263`, `ActionError.kt`, `HomeViewModel.kt:398-410`, `HomeViewModelWrapper.swift:426-442`, `WearHomeViewModel.kt:509-516` |
| 2.7 | **Voice availability.** Decide the phone's gate: ship it to everyone (it already has the three gates and the server verdict), or record why the watch is the only public voice surface. | `ui/HomeContent.kt:177-192` |

### Phase 3 — Lean into strengths (differentiation; M–L)

| # | Change | Why it is this surface's strength |
|---|---|---|
| 3.1 | **iOS WidgetKit** (Home, Lock Screen, StandBy) driven by `GlanceStatus`, with `Text(date, style: .relative)` — the self-updating duration the Android widget *cannot* do. Timeline reload from `didReceiveRemoteNotification` and on foreground. Needs an App Group snapshot written by `DefaultStatusSnapshotStore` and the KMP framework linked into the extension (feasibility with `embedAndSignAppleFrameworkForXcode` not verified). Mirror `GarageDoorWidgetSafetyTest`'s three assertions. **L.** | iOS has no glance surface; its widget medium is strictly more capable than Android's |
| 3.2 | **Android widget: `SizeMode.Responsive`** with a 4×1 layout that puts the since-line inline; `providePreview` for an honest picker image; capture on an emulator; gallery row. **S–M.** | resizing is declared but every size gets one layout |
| 3.3 | **Wear `OngoingActivity`** while a press is in flight or the door is moving, keyed off the existing `keepScreenOnTrigger` states. **M.** | the wrist drops; the face should still say "Waiting for the door" |
| 3.4 | **Read-only voice on iOS via App Intents** ("Is the garage door open?") returning the shared `StatusHeadline`/`SinceStatus` wording; static App Shortcuts on Android. **M.** Explicitly *no* action intent — it would bypass the confirm gesture and the `doorCommand` third gate. | the only platform with no voice; query-only fits the safety posture |
| 3.5 | Notification actions ("Snooze 1 h") on the app-built notifications; `UNNotificationCategory` on iOS. **M.** | snooze is not a press; it belongs where the warning is |
| 3.6 | Considered and declined for now: a Quick Settings tile (its convention is *toggle*; a tap that opens the app fights the idiom), Live Activities (needs an APNs activity-token flow), FCM on the watch (L; the poll is bounded by construction). | |

### Phase 4 — Guard it (enforcement; S–M)

| # | Change |
|---|---|
| 4.1 | **Cross-platform string parity test** — a node test under `.github/scripts` (already a required check on every PR) that loads Android `strings.xml`, Wear `strings.xml` and `Localizable.xcstrings` and asserts a curated map of shared-decision words are equal, with a positive control. |
| 4.2 | **Swift `default:` fence** for `iosApp/Features` in the `check-ios-localizable-text.sh` style, then remove the three existing arms. |
| 4.3 | **Single-source the 11-minute threshold** in `:domain` (usecase does not depend on presentation-model) and a commonTest that reads `verdict_table.json`'s `staleThresholdSeconds` — closing client↔server the way `DoorCommandGateTest.ts:84` already does for the server. |
| 4.4 | **Extend `checkHardcodedColors`** to `wearApp` and the widget package; add the literal-duration grep for door views that `UI_FIDELITY_TIERS.md` proposes. |
| 4.5 | **Pin spacing tokens**: a test asserting `GarageSpacing.swift` values equal `Spacing.kt`; give Wear a token file. |
| 4.6 | **Catch string literals the Compose regex misses**: a Konsist rule for `String`-typed functions/properties returning literals in `ui/` non-preview objects (`DeviceCheckIn.kt`, `RemoteButtonHealthPill.kt`); replace the `NO_DATA_LABEL` sentinel with the typed `CheckInStatus`. |
| 4.7 | **Prune** `HomeScreenState` / `DoorHistoryScreenState`, `DoorStatusCard.kt` (unrouted, title-case strings, still a gallery row), the unreferenced drawables, and iOS `GarageColors.statusOk/statusOpen/cardBackground`. |
| 4.8 | **A truthful review set**: regenerate Android references on a working environment, add the four settle-window Home fixtures to the manifest, add a dark `#Preview` pass to Prefire, add widget and tile rows. |

## 6. Preserve — divergences that are correct

These were checked and should **not** be "fixed":

- Complication: "Sensors", short "Stale", worded units, emphasis inversion, only `SHORT_TEXT`/`LONG_TEXT` advertised — the seven-character budget is a feature.
- Tile: renderer-counted duration with worded units starting at two ("hours" not "hr"); no picker preview (a captured PNG would freeze a live number).
- Widget: absolute instant, not a duration; read-only; 30-minute floor honoured rather than hidden.
- Wear: press-and-hold with the ring; "Hold to open / Hold to close / Hold to press the remote"; the armed copy without a countdown (the ring carries it); always-dark canvas; the neutral white ring so the door owns the only hue; the azure simulation ring; voice leaves that never survive backgrounding.
- Phone: two-tap confirm; "Package" vs "Bundle ID"; "Android system settings" vs "iOS Settings app"; native tab rendering, inset-grouped lists, sheets, large titles.
- Android adaptive layouts (rail, 3-pane) — Tier-4 exclusive.
- iOS system accent for CTAs with the brand-locked door.

## 7. Verification model

Every phase-0 and phase-1 item is CLI-verifiable, which is this repo's release
gate:

- Contrast: a unit test computing WCAG ratios for the header and tag roles
  against their grounds (both themes), so 0.1 cannot regress silently.
- Muting: `aMutedDoorIsFullyGreyAndDimmed`-shaped tests on phone and widget
  (luma spread = 0, alpha = `MUTED_ALPHA`).
- Push repaint: a test that a door event through `FcmMessageHandler` results in
  an `updateAll` call (fake the widget manager); on Wear, that a `currentDoorEvent`
  change calls the two update requesters.
- Stuck door: `GlanceStatusMapperTest` gains a warning arm; each `*Words` test
  gains the stuck case; `GarageComplicationLengthTest` gains "Stuck".
- Vocabulary: the parity test in 4.1 is what makes 1.1 and 1.7 stay true.
- The review set (4.8) is regenerate-not-assert, as today; it is the design
  review's eyes, not CI's gate.

Things CLI cannot see, stated rather than hidden: how a complication looks on a
physical face; the widget's pixels (composition tree only); TalkBack behaviour
on the Wear hold and the widget.

## 8. PR map (suggested order)

1. `docs`: this document + CLAUDE.md corrections *(this PR)*
2. `fix(android)`: light-mode header/tag colour + contrast test — 0.1
3. `feat(widget)`: push-triggered repaint — 0.2
4. `feat(wear)`: glance update nudge — 0.3
5. `feat(glance)`: `GlanceStatus.warning` + stuck wording on three surfaces — 0.4
6. `fix(wear)`: signed-out copy — 0.5
7. `fix(glance)`: one "Not confirmed" + Wear hero stale line + "No signal" on the complication — 1.1
8. `fix(theme)`: one grey; retire `_STALE_`; widget `FreshnessTint` — 1.2
9. `refactor(glance)`: `GlanceSubline` in the mapper — 1.3
10. `refactor(wear)`: hero consumes `DoorHeadline` — 1.4
11. `feat(presentation)`: caution severity — 1.5
12. `fix(ios)` / `fix(android)`: warning vocabulary + button outcome rule — 1.6
13. `fix(strings)`: accidental drift list — 1.7
14. `refactor(presentation)`: `SinceClock` + hour cycle — 1.8
15. Phase 2 items, one PR each
16. `ci`: parity test, Swift fence, threshold single-source, lint scope — 4.1–4.4
17. Phase 3 by appetite: iOS WidgetKit is the largest and the most differentiating.

Each of 2–14 is a minor or patch on its lane by the repo's own rule; 0.4 and
2.1 are user-facing capability changes (minor). Nothing here changes the door's
acting path or its gates.
