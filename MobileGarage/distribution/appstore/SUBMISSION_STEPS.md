---
category: plan
status: active
last_verified: 2026-07-05
---

# App Store submission — step-by-step (everything pre-filled)

Every repo-side prerequisite is DONE. This is the complete walkthrough
for the one thing an agent cannot do: your App Store Connect session.
Work top to bottom; every value is pre-filled — copy/paste as you go.
Estimated time: 30–45 minutes.

> **Progress (2026-07-06): Steps 0–3 DONE, submission deliberately
> PAUSED — TestFlight-only is sufficient for now. Resume at Step 4.**
> Build 6 confirmed processed and visible in TestFlight; App
> Information, age rating (4+), and Free pricing are saved in ASC.
> Nothing has been submitted for review. Reminder: TestFlight builds
> expire after 90 days (build 6 ≈ 2026-10-03) — cut a fresh `ios/N`
> via `scripts/release-ios.sh` when needed; any later build works for
> the eventual submission (all contain the privacy manifest).

**Already done for you:** build 6 (`ios/6`, 0.1.0) is on TestFlight with
the privacy manifest + door fix; 12 screenshots at exact ASC sizes are
in this directory; all copy is drafted below and in
[`LISTING.md`](LISTING.md); the privacy policy URL is live (verified
HTTP 200 on 2026-07-05).

---

## Step 0 — Confirm build 6 finished processing

App Store Connect → **My Apps → Garage by Chris Cartland → TestFlight**.

- Build **6** (version 0.1.0) should show as ready (processing takes
  ~15–60 min after upload; it was uploaded 2026-07-05).
- Export compliance should already be answered (the project sets
  `ITSAppUsesNonExemptEncryption = NO`); if ASC still asks, answer
  "None of the algorithms mentioned above" / no encryption.
- If build 6 shows a processing error instead, stop and report it back;
  the agent will investigate.

## Step 1 — App Information (left sidebar → General → App Information)

| Field | Value |
|---|---|
| Name | Garage by Chris Cartland *(should already be set)* |
| Subtitle | `Smart garage door companion` |
| Primary category | Lifestyle |
| Secondary category | (leave empty, or Utilities) |
| Content rights | "No, it does not contain, show, or access third-party content" |

## Step 2 — Age rating (same page, Edit next to Age Rating)

Answer **None / No to every question** — no violence, no sexual
content, no profanity, no drugs, no gambling (simulated or real), no
horror, no medical content, no user-generated content, no messaging,
no unrestricted web access, not a web browser. Result: **4+**.

## Step 3 — Pricing and Availability (left sidebar)

- Price: **Free** (USD 0.00).
- Availability: all territories is fine (or trim if you prefer).

## Step 4 — App Privacy (left sidebar → App Privacy)

1. **Privacy policy URL:** `https://chriscart.land/garage-privacy-policy`
   *(the URL the app itself links from Settings → About; verified live.
   If your new URL is different, use yours — and tell the agent so
   `AppLinks.PRIVACY_POLICY_URL` gets updated to match.)*
2. **Get started → "Do you collect data from this app?"** → **Yes**.
3. Declare exactly two data types (matches the shipped
   `PrivacyInfo.xcprivacy` — reviewers cross-check):
   - **Contact Info → Email Address**: collected, **linked to the
     user's identity**, **not used for tracking**, purpose **App
     Functionality**.
   - **Identifiers → User ID**: same four answers.
4. Nothing else is collected (no location, no diagnostics/crash, no
   usage data, no ads). → **Publish**.

## Step 5 — Prepare the 1.0 submission page (left sidebar → the "0.1.0 Prepare for Submission" version page)

### 5a. Screenshots

Drag from `MobileGarage/distribution/appstore/` (order shown = order
in the store; light first):

- **iPhone 6.9" slot:** `iphone-6.9-01-home-light.png`,
  `02-history-light`, `03-settings-light`, `04-home-dark`,
  `05-history-dark`, `06-settings-dark`
- **iPad 13" slot:** the six `ipad-13-*.png` in the same order

(ASC auto-fills smaller size classes from these two.)

### 5b. Text fields (all drafted in LISTING.md — paste)

| Field | Value |
|---|---|
| Promotional text | See LISTING.md § Promotional text |
| Description | See LISTING.md § Description |
| Keywords | `garage,door,smart home,opener,monitor,ESP32,IoT,remote,notification` |
| Support URL | `https://github.com/cartland/SmartGarageDoor` |
| Marketing URL | (optional; same GitHub URL or leave empty) |
| Version | 0.1.0 *(pre-filled; leave as is — 1.0 bump deferred by choice)* |
| Copyright | `© 2026 Christopher Cartland` *(adjust to taste)* |

### 5c. Build

**Add Build → select build 6 (0.1.0).**

### 5d. App Review Information

- Sign-in required: **check the box but explain** — there is no demo
  account. Leave the username/password fields empty and rely on the
  notes:

> This app requires custom hardware (an open-source ESP32 garage door
> device) and an allowlisted Google account, so most features cannot be
> exercised by review. The app launches to a functional signed-out state
> showing live door status from our production server; sign-in is
> Google-only. No demo hardware can be provided; the project is
> open source: https://github.com/cartland/SmartGarageDoor

- Contact info: your name, email, phone.
- Release option: **Manually release this version** (recommended for a
  first submission — you control the go-live moment after approval).

## Step 6 — Submit

**Add for Review → Submit.** Typical first-review turnaround is 1–3
days.

### If Apple responds with questions

- *"How do we test it?"* → point at the review notes; offer a short
  screen recording of the app controlling the physical door (the
  standard answer for hardware-dependent apps). Record on your phone
  when asked — don't pre-produce it.
- *Rejection citing sign-in without demo account* → reply that door
  control is hardware-bound and allowlisted; the signed-out experience
  (live status) is fully functional for review. This usually resolves
  it; if they insist, the fallback is a demo video.
- Anything else → paste the rejection text back to the agent; fixes and
  resubmission are mostly automatable.

---

## Recommended while you're at it (separate from submission)

These are the two long-standing device-gated verifications from
[`PENDING_FOLLOWUPS.md`](../../docs/PENDING_FOLLOWUPS.md) § 1 — both
need only your phone and the TestFlight app:

1. **Install build 6 from TestFlight** on your iPhone.
2. **Sign-in tap-through:** Settings tab → Sign in with Google →
   complete the flow. Success = your name/email on the Account row and
   the snooze section unlocking. (Safe to drive; just don't tap the
   Home remote button unless you mean to operate the door.)
3. **Push delivery:** with the app backgrounded, wait for (or cause) a
   door event; the door state should be current when you reopen the
   app. If you want a deliberate test without touching the door, tell
   the agent — the test-notification sandbox can target iOS once
   sign-in works.

Report pass/fail back; the agent updates the docs and closes these out.

## After approval

1. Release the version (you chose manual release).
2. Tell the agent — docs get a status sweep (`PENDING_FOLLOWUPS` § 1
   Phase G → DONE, MIGRATION Phase 13 row, memory), and the App Store
   link gets added to the README/listing docs.
