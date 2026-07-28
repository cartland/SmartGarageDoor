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

## 0.3.5

- **The voice countdown now buzzes halfway round, like holding the door
  does.** Both put a ring around the edge of the screen and fill it, but only
  the hold buzzed at the start, the middle and the end. The countdown buzzed at
  the two ends and went quiet in between, so the same picture felt different
  depending on which screen drew it, and the longer of the two waits had the
  least to go on. Cancelling still works right up to the last moment, and
  cancelling early takes the middle buzz with it.

## 0.3.4

- **Leaving the voice demo now actually ends it.** Swiping back while a command
  was counting down used to leave it running behind the home screen: it would
  finish on its own, move the demo door, buzz your wrist for a command you had
  walked away from, and still be showing that result when you went back in. It
  now stops when you leave, and the microphone stops with it.
- **Nothing on the demo screen jumps around any more.** The microphone and the
  labels above it used to shift up and down by a few pixels every time the
  wording changed, which happened three times during a single command. They now
  stay exactly where they are, including when the screen switches to listening,
  so the microphone grows in place instead of hopping.
- **You can see how to stop it while it is listening.** Since the last release a
  tap has cancelled, but the listening screen never said so, which left you
  watching it with no visible way out. It now says the same thing the countdown
  says.
- **The ring no longer disappears at the moment it matters.** When the countdown
  finishes, the ring completes and stays, the way the real garage button's ring
  already does, instead of vanishing at the instant of commitment.
- **"Nothing was sent" stays on screen long enough to read it.** It is the whole
  point of the demo and it used to be gone in a second and a half.
- The demo door is now shown while you speak, not just before, so you can see
  what your command is about to be judged against.
- Watching the garage door move, and the buzz when a press lands, no longer
  depend on which screen you happen to be looking at.

## 0.3.3

- **Tapping while it is listening now stops it.** Previously a tap did nothing
  at all until the recognizer gave up on its own. One rule now covers the whole
  screen: a tap starts whatever is not running and stops whatever is.
- **Cancelling a command now just cancels it.** Tapping during the countdown
  used to cancel and immediately start listening again, which meant brushing
  the screen dropped you into a live microphone you had not asked for. It now
  returns to the start, and the microphone really does stop.
- The microphone and the pulse rings are now properly concentric. The
  microphone had been sitting slightly above the rings that were supposed to
  be coming out of it.

## 0.3.2

- **You can now tell it is listening at a glance.** Speaking takes over the
  whole screen: the microphone becomes large and rings pulse outward from it,
  so you no longer have to read the word "Listening" to know it is your turn.
- **It reacts to your voice.** The rings travel further and get brighter the
  louder you speak, and the microphone grows slightly with you. This is driven
  by the actual microphone level, so silence looks different from talking.
- **The words appear as you say them.** The example command is shown only
  until you start speaking, and then that same line becomes the live
  transcript. Nothing else competes for space while you talk.

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
