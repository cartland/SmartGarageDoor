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

## 0.7.0

- **You can now tell a stuck door to close.** A door that starts moving and
  never arrives — jammed on something, or reversed by the safety beam — used to
  refuse every spoken command, which meant voice gave up in the one situation
  where you most want to reach the door: it is stopped partway, open to the
  street, and needs another press. Saying "close the garage door" now works.

- **Opening one is still refused.** The door already tried to open and did not
  get there, so another press is not going to be more open, and everything in
  this app leans toward closed when it is unsure.

- A door with sensors that actively disagree still refuses both directions.
  That one has no position to reason from at all, which is the difference: a
  stuck door's position is known, it is just not where it was going.

## 0.6.3

- **The screen stays awake while you are talking to it.** Voice is the only
  thing here you can do without touching the watch — you tap once, say a
  sentence and wait — so nothing was keeping the display alive, and it could go
  dark mid-command and take the microphone with it. It now stays lit for the
  whole thing: the mic being open, the countdown, the send, and the outcome.

- **It stays lit a few seconds after everything stops, too.** The moment a
  command ends is the moment there is finally something to read, and going dark
  exactly then blacked out the answer.

- **A sent command keeps the screen up until the door actually moves.** Being
  told the press went through is not the thing you were waiting for. The watch
  now also checks the door more often while a spoken press is outstanding —
  before, it could be up to ten seconds behind a door it had just opened.

- **The voice screen gets out of the way once the door starts moving**, so you
  see the door animate instead of a microphone sitting on top of it. Only when
  the door *starts* moving — arriving while it is already moving leaves you
  where you are, since that is exactly when you might want to reverse it. The
  simulation in Settings never does this; it would drop you onto the real door.

## 0.6.2

- **The voice screen no longer waits for you.** It was a place the app could be
  parked: leave while it is open, come back, and you were still on it — and
  worse, sitting there doing nothing, because opening that screen is what
  starts the microphone and it had already opened. Coming back now puts you on
  the door, which is where the app is useful.

- **Every tap on the voice screen now tells you it landed.** Two moments were
  completely silent. Tapping the mic gave no feedback at all until you had
  spoken *and* been understood, so a command it failed to catch was silent from
  beginning to end. Tapping to close the mic before speaking was silent too, so
  the same gesture confirmed itself or not depending on timing you cannot see.
  Both buzz now.

- The buzzes are a language rather than a set of noises: a light tap when the
  microphone opens, a firmer one when a command lands and the door is a few
  seconds away, a tick at the halfway mark, a double beat when it is sent, and
  distinct feedback for "you stopped it" versus "that did not take". Each one
  is different from the others, so the wrist alone tells you what happened. An
  outcome fading on its own stays silent, because you did not do anything.

## 0.6.1

- **What the watch heard you say is readable now.** The live transcript was
  pinned to a single line, so a normal command came out as "…en the garage door
  please" with the first word cut off — on the one line whose entire job is to
  show you what it thinks you said. It gets two lines while you are speaking,
  and the listening animation is kept clear of them. The "Say open the garage
  door" prompt was already fine and is untouched.

- **Speaking and holding are now one gesture with two starts.** They had
  quietly drifted apart: holding the door gave you 2 seconds to change your
  mind, speaking gave you 3, and letting go of a hold buzzed while cancelling a
  spoken command said nothing at all. Both are 2 seconds now, both buzz the
  same way at the start, the halfway point, the commit and the cancel, and both
  can still be called off right up to the moment the ring closes around the
  screen. The ring means one thing everywhere: when it closes, the door moves.

- The difference that remains is the one that should: a hold is something you
  keep doing, so you stop it by lifting your finger; a spoken command runs on
  its own, so you stop it by tapping the screen.

## 0.6.0

- **The mic beside the door now opens the real garage door.** Speaking to the
  watch has been a simulation since 0.3.0 — it would name the action it was
  about to take and then tell you nothing had been sent. It sends now. Say
  "open the garage door", and unless you tap the screen within three seconds,
  the door opens. Everything that guarded the hold-to-confirm button guards
  this too: it only listens while you are signed in, only an unambiguous
  command counts, it refuses anything the door contradicts (asking it to open
  while it is already open, or while it is moving, or when its position is not
  known), and it re-checks at the last moment, so a door that starts moving
  during the countdown cancels the press instead of completing it. Walking away
  from the screen cancels too.

- **The simulation moved to Settings, and is now unmistakable.** It is the same
  screen and the same interaction, running against a pretend door, so the whole
  thing can still be rehearsed at a desk — including the refusals, which on the
  real door would mean cycling the garage to reach. Four things now say it is
  not real, where there used to be three: a SIMULATION marker that stays on
  screen in every state, wording that stays conditional ("Would open the door",
  ending in "Nothing was sent"), a door labelled "Demo door", and — new — a
  **blue countdown ring** instead of the real one's white. The colour is the
  one that reads while the ring is moving and you are not reading the words.

- Settings gained a Voice section for it. It lives there, and not next to the
  door, on purpose: the mic on the door screen is the real control now, so the
  practice one should not sit where a hand reaching for the real one might land.

## 0.5.4

- The spacing at the top and bottom of Settings is now Material 3's own
  recommendation rather than a number we picked. 0.5.3 fixed the last row being
  clipped by the bottom of the round screen by reserving space by hand; the Wear
  library turns out to have an API for exactly this, which each row uses to ask
  for the room it needs, and which the list grants only to the rows that are
  actually at an edge. Slightly tighter than our guess at the bottom, and the
  heading at the top now sits where the design system puts it.

## 0.5.3

- **The end of Settings can now be scrolled up into the middle of the screen.**
  The list used to stop as soon as the last row was technically on screen, which
  parked it against the bottom of the round display, where there is least width
  and the curve cuts the ends off whatever is sitting there. There is room past
  the last row now, so it comes to rest where the screen is at its widest and you
  can read all of it. The rest of the list is unchanged: the middle of the screen
  was never the problem.

## 0.5.2

- The watch now tells the paired phone which version it is running, so the
  phone's Settings can say "Version 0.5.2 on your watch" instead of only that
  the app is installed. Nothing in the Wear APIs reports another device's app
  version, so the watch has to volunteer it. No visible change on the watch
  itself; needs phone app 2.23.6 to be read.

## 0.5.1

- **The voice demo now draws the same ring as the real button.** Its countdown
  was still the old pale orange, and its commit just snapped to a finished ring
  rather than swelling into one. The door's ring went neutral white and gained
  that swell in 0.4.1, and the two drifting apart is the one thing the demo must
  not do: its entire job is to rehearse the real interaction, so it has no
  business teaching a different vocabulary for the same moment. It is now
  literally the same ring, driven by the same code, so they cannot drift again.
- **The listening screen had four lines of text and rings drawn through all of
  them.** The pulse rings expanded all the way to the edge of the screen, which
  meant every one of them crossed the words it was supposed to be accompanying.
  They now stop short of the text. The screen is down to two lines: what this is,
  and what to say.
- **What you are saying stays on one line, and it is the end of it you see.**
  A long sentence used to wrap and push itself back up into the rings. It now
  trims from the front, so the words that just arrived are the ones on screen.
- The microphone is slightly smaller while listening, which is what gives the
  rings room to travel now that they stop early.

## 0.5.0

- **Settings is now a page beside the door, not a button on top of it.** Swipe
  left from the door to reach it and right to come back, and two dots at the
  bottom of the screen say which of the two you are looking at. The three-dot
  button it replaces was borrowed from phones, where an overflow menu is a
  thing people recognise; on a watch it was a small target spending screen on
  an errand the platform already has a gesture for. The door keeps every pixel
  it had.
- **Settings scrolls, including with the crown.** It was a centred column
  before, which is the wrong shape for something meant to grow: items were
  arranged outward from the middle, so anything added moved everything already
  there, and once the column outgrew the screen the top of it was simply gone.
  It is a proper list now, and it turns with the crown, which until this
  release did nothing anywhere in the app.
- **It says which account is signed in.** Nothing anywhere on the watch did.
  While the watch is still asking the phone who you are it says so, rather than
  claiming for that moment that you are signed out.
- **Speaking is one tap again.** The mic beside the door used to take you to a
  screen that then asked you to tap a second time to start listening, which is
  most of the interaction when the whole point is to say two words and put your
  arm down. It now starts listening the moment it opens. Nothing else changed:
  it is still a simulation, it still never touches the real garage door, and
  holding the door is still the only thing that does.

## 0.4.1

- **The ring is white now, not peach.** It had been picking up the watch
  theme's accent colours, which resolve to a pale lavender while you hold and a
  pale orange once the press is sent. Orange reads as a warning, which is an
  unfortunate thing for the screen to say at the exact moment it means "that
  worked". The ring is an instrument, so it is neutral: dimmed white while it
  counts, full white when the press goes out. The door keeps the only colour on
  screen that carries meaning.
- **The ring no longer draws across the label.** Near the bottom of a round
  screen the usable width narrows sharply, and the label was sized against the
  screen's edge rather than against the ring drawn inside it, so "Waiting for
  the door" was struck through on both sides. It was worse on the larger watch,
  because the label scales with the screen and the ring's thickness does not.
  The outer edge of the screen is now reserved for the ring, and the label's
  position is derived from that reservation instead of being a fixed number.
- **Sending a press no longer paints over the door and the labels.** The
  confirmation used to swell until it covered the whole screen, so the moment
  you most wanted to read the door's state was the moment it was hidden. It now
  thickens into its own reserved band and goes full white, which is still the
  loudest thing on screen without borrowing anyone else's pixels.
- The door is slightly smaller, which is what pays for the label moving up.
- **The menu no longer prints the release tag it was cut from.** A line reading
  `wear/16` under the version number is release plumbing — it means something
  against this repo's tags and the Play track log, and nothing to anyone wearing
  the watch, who already has the version number right above it. A build that
  never came from a release still says so, because that is a genuinely different
  thing from an old build and the version number cannot tell you which you have.

## 0.4.0

- **New: a menu, reached by the three dots beside the door.** It tells you which
  version is running and gives you a button that opens this app's page in the
  watch's own Play Store. The watch gets updated far more often than it gets
  configured, and until now it could answer neither "what am I running?" nor "is
  there anything newer?" without going through the phone. Wear OS does update
  apps on its own, but on its own schedule, which is no help when the build you
  want to try was cut a few minutes ago.
- **It shows the running build next to the store link, not just the link.** The
  store can tell you what the newest version is; only the watch can tell you what
  it actually installed, and after tapping Update that is the question you come
  back to ask. A build cut from a release names itself the same way the release
  tags do, so it can be compared directly; a build that never came from a release
  says so instead of pretending to be one.
- The menu is reachable whether or not you are signed in. It is the one thing
  here whose usefulness does not depend on being signed in: it exists to get a
  newer version onto the watch, and a version broken enough to leave you stuck at
  the sign-in screen is exactly when that matters most.

## 0.3.6

- **Letting go early now rewinds the ring instead of blanking it.** The ring is
  the only thing that records that you held the door at all, so watching it run
  back to empty is what tells you nothing was sent. It used to disappear in a
  seventh of a second, which reads as a glitch rather than a cancellation.
- **Starting a fresh hold always starts from empty.** If you let go and
  immediately pressed again, the new hold used to pick up wherever the old one
  had got to and show a ring that was already part full. That was a promise the
  timer never made: the countdown always starts from zero, so now the ring does
  too.
- **Completing the hold is now a real moment.** The ring closes inward until the
  whole screen is a solid disc, pauses there, and opens back out into a ring.
  Your wrist gets two beats instead of one to match. Previously the sweep just
  vanished and a plain ring took its place, which was the quietest thing on
  screen at the one point of no return in the whole gesture.
- **While the press is on its way, the ring turns.** Waiting for the server and
  then for the door to actually start moving can take ten seconds or more, and a
  motionless ring through all of that looks like an app that has stopped
  responding. It is now a gapped ring rotating slowly, so waiting looks like
  waiting.
- The big finish only ever plays for a press that really was sent. A hold you
  abandoned cannot trigger it, even in the moment before the app has worked out
  which of the two happened.

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
