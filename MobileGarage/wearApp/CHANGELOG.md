---
category: reference
status: active
last_verified: 2026-07-22
---
# Wear OS App Changelog

Internal release history for the Wear OS app. Releases are cut with
`./scripts/release-wear.sh` as `wear/N` tags (versionCode = 1000000 + N).
The phone app's history lives in [`../CHANGELOG.md`](../CHANGELOG.md).

## Versioning

Same rule as the phone app: major = rewrite or core-experience shift;
minor = added or removed user-facing feature; patch = fixes, polish, refactors.

## 0.3.1

- **Speaking a command is now one tap.** The watch used to hand you off to the
  system text-entry screen, which transcribed what you said and then made you
  confirm it before the app ever saw it. The demo now listens inside the app,
  shows the words as you say them, and goes straight to the countdown. This
  needs permission to use the microphone, which it asks for the first time you
  tap the mic. Say no and it still works, just the long way round.
- **Cancelling is much easier: tap anywhere on the screen.** You no longer have
  to hit the small mic button to stop a command that is counting down.
- The microphone is used only by the voice demo. It is never involved in
  operating the real garage door, which is still hold the door for two seconds.

## 0.3.0

- **New: a voice demo, and it is only a demo.** A small mic button beside the
  door opens a separate screen where you can say "open the garage door" and
  watch the whole thing happen: it repeats back what it would do, counts down
  so you can cancel, and then tells you that nothing was sent. It never
  touches the real garage door, and it says so on screen the entire time.
- The demo has its own pretend door that reacts to your commands, so you can
  see why a command gets refused. Open it by voice, and asking to open it
  again is turned down with "Demo door is already open". Ask while it is
  moving and it will wait for it to settle.
- It is as strict as the real thing will be: it acts only on a clear
  instruction. Questions ("can you open the door"), anything negative
  ("don't open the door"), and half-heard speech are all turned down, and it
  shows you what it thought you said.
- The watch vibrates when a command is understood, again at the moment a real
  press would be sent, and differently when a command is refused.
- The door itself is unchanged: holding it for two seconds is still the only
  way to operate the real garage.

## 0.2.0

- **Operating the door is now one gesture: press and hold the door for two
  seconds.** There is no longer a separate tap to arm the button first, and
  tapping the door does nothing at all. The screen no longer walks you
  through arming states.
- The hint now tells you what will happen instead of naming a mode: "Hold to
  open" when the door is closed, "Hold to close" when it is open. When the
  door is moving, or its position is not certain, it reads "Hold to press the
  remote" — the button sends one remote press and the garage decides whether
  that opens, closes, or pauses, so the app only promises an outcome when the
  sensors confirm one.
- The watch now vibrates through the press: once when your finger lands, once
  halfway, and a distinct double when the press is sent and you can let go.
  A shorter, softer buzz means you released early and nothing was sent. You
  also feel it when the door actually moves, and when a press fails.
- When the press is on its way, the ring completes and changes colour, and
  stays that way until the door responds.
- The hold is now abandoned if your finger slides while holding, so a sleeve
  or a wrist resting against something cannot complete a press.

## 0.1.6

- The watch app now announces itself to the paired phone, so the phone's
  new Settings "Watch" row (phone app 2.22.0+) can show a green check
  when the app is installed instead of offering to install it again. No
  visible change on the watch itself.

## 0.1.5

- The hold-to-confirm ring is now centered on the physical screen and hugs
  the bezel, instead of circling the door image off-center. The screen now
  stays on for up to 15 seconds while a press is in flight or the door is
  moving, so you can watch the action complete without the display timing
  out; it never stays on just because the button is armed. Pressing now
  allows a little more time before showing "Door did not move," since the
  watch's network path is less reliable than the phone's.
- Before the first door status arrives, the screen now shows "Connecting…"
  with no warning badge, instead of a gray door labeled "Unknown" with a
  warning triangle — calmer during the first few seconds after opening the
  app.

## 0.1.4

- Armed button stays armed while you keep touching the screen: every touch
  (down and up, anywhere on the screen) restarts the disarm timer, so
  partial taps and aborted holds no longer let the button quietly disarm.
  It now disarms only after ~8 seconds with no touches. Also fixes a
  mid-hold disarm edge where a hold started late in the armed window could
  visually complete but never fire. Operating the door still requires the
  full continuous 2-second hold, and a quick tap still never triggers it.

## 0.1.3

- Sign in with your phone: while the watch is signed out, the app now uses
  the paired phone's signed-in account over Bluetooth or Wi-Fi (requires
  phone app 2.21.0). Play services rejects watch-local Google sign-in on
  Wear OS ("Google Identity Services do not support this Android
  Credential Manager API on Wear OS", captured on a Pixel Watch 4), so the
  phone relay is the working path; the Sign in button remains for watches
  where it works.

## 0.1.2

- Sign-in failures now show a transient "Sign-in failed" message under the
  button instead of silently doing nothing (the 0.1.1 button appeared
  unresponsive when Credential Manager failed, e.g. watches whose Play
  services lack the Identity Sign-In module).
- Slightly smaller door in the signed-out layout so the sign-in button and
  failure message fit the round screen.

## 0.1.1

- No app changes. First release through the fully automated CI to Play
  pipeline (wear/N tag to Wear internal track), now including Play release
  notes from `distribution/wear-whatsnew/`.

## 0.1.0

- Initial Wear OS release: animated garage door status with tap-to-arm and a
  2-second hold-to-confirm remote button.
- Standalone watch app: Sign in with Google via Credential Manager,
  foreground-only status refresh, shared door animation spec with phone/iOS.
