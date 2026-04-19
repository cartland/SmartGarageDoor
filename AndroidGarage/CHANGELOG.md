# Android Changelog

Internal release history. For Play Store "What's New" text, see `distribution/whatsnew/`.

## Versioning

- **Major (X.0.0)** — App rewrite or a change in the core experience so significant the previous version feels like a different product.
- **Minor (X.Y.0)** — A new user-facing feature or capability (something a user couldn't do before), **or** the removal of a user-facing feature.
- **Patch (X.Y.Z)** — Bug fixes, UI polish, performance, refactors. No new capability.

Every version gets an entry in this file (internal history). Play Store `distribution/whatsnew/` gets a line per minor/major — patches roll up into the next minor's line, or get a combined line if promoted to production on their own.

## 2.4.3
- Snooze card updates to "snoozed until X" immediately after saving (no app restart needed)

## 2.4.2
- Snooze card shows "Door notifications enabled" / "Door notifications snoozed until X"
- Snooze status loads immediately instead of showing "Loading"
- Action feedback (Saved/Error) aligned under button
- Improved card layout alignment

## 2.4.1
- Faster sign-in updates and improved stability
- Fixed auth state not updating UI until app restart (reactive AuthStateListener)
- Door notification card now shows current status (enabled/snoozed)

## 2.4
- Redesigned garage door button with confirmation flow and network status diagram
- Improved color contrast for accessibility

## 2.3
- Improved architecture and performance

## 2.2
- New colors and design

## 2.1
- Snooze garage notifications when the door is open
- Snooze applies to all users for the current door position

## 2.0
- Brand new app built with Jetpack Compose
