# Garage Server

A Firebase Cloud Functions server that manages garage door sensor data and notifications.

## Features

- Real-time garage door status monitoring
- Push notifications for door state changes
- Notification snoozing capabilities
- Remote door control

## Architecture

The server is built using:
- Firebase Cloud Functions
- Firebase Firestore
- Firebase Cloud Messaging (FCM)
- TypeScript

### Key Components

- **Event Processing**
  - Interprets sensor data to determine door state
  - Manages state transitions and timing
  - Sends real-time updates via FCM

- **Data Management**
  - Time-series data storage
  - Historical event tracking
  - Automatic old data cleanup

- **Security**
  - Authorized user management
  - Encrypted secrets handling

## Development

### Prerequisites

- Node.js 22 (pinned via `.nvmrc`; `engines.node` in `package.json` enforces at install)
- Firebase CLI
- TypeScript

Use `scripts/firebase-npm.sh` (or `scripts/validate-firebase.sh`) from the repo root — both auto-switch Node via nvm so you don't need to `nvm use` by hand. See CLAUDE.md for why `NODE_OPTIONS='--no-experimental-strip-types'` is pinned in the `tests` npm script (Node 22.18+ default strip-types breaks `import * as admin from 'firebase-admin'` under mocha).

### Setup

1. Install dependencies:

```bash
npm install
```

2. Build the project:

```bash
npm run build
```

3. Run tests:

```bash
npm run tests
```

### Configuration

Update the server configuration in `serverConfig.json`, then push it to the server:

```bash
curl -H "Content-Type: application/json" -H "X-ServerConfigKey: $SERVER_CONFIG_UPDATE_KEY" https://us-central1-escape-echo.cloudfunctions.net/serverConfigUpdate -d @serverConfig.json
```

Example config payload (illustrative — the live values are managed in production via `httpServerConfigUpdate`; see `docs/FIREBASE_HARDENING_PLAN.md` for the buildTimestamp config-authority rule shipped in `server/16` / `server/17`):

```json
{
  "buildTimestamp": "<plain build identifier>",
  "deleteOldDataEnabled": true,
  "deleteOldDataEnabledDryRun": false,
  "remoteButtonEnabled": true,
  "remoteButtonBuildTimestamp": "<URL-encoded build identifier>",
  "remoteButtonPushKey": "<requiredKey>",
  "host": "https://example.com",
  "path": "addRemoteButtonCommand",
  "remoteButtonAuthorizedEmails": [
    "email@example.com"
  ]
}
```

**Reading current config:** `GET /httpServerConfig` returns the active payload. The four pubsub/HTTP handlers (`httpCheckForOpenDoors`, `pubsubCheckForOpenDoorsJob`, `pubsubCheckForDoorErrors`, `pubsubCheckForRemoteButtonErrors`) read `buildTimestamp` / `remoteButtonBuildTimestamp` from this config — they no longer carry hardcoded fallbacks (A3 of the hardening plan).

### Database Structure

Every Firestore collection is wrapped by a typed module in `src/database/` with a contract test pinning the collection string (see `docs/FIREBASE_DATABASE_REFACTOR.md`).

| Collection pair | Module | Purpose |
|-----------------|--------|---------|
| `eventsCurrent` / `eventsAll` | `SensorEventDatabase` | Interpreted door events (derived from sensor updates) |
| `updateCurrent` / `updateAll` | `UpdateDatabase` | Raw sensor updates from the ESP32 |
| `configCurrent` / `configAll` | `ServerConfigDatabase` | Server configuration (buildTimestamps, keys, feature flags) |
| `notificationsCurrent` / `notificationsAll` | `NotificationsDatabase` | FCM notification history (open-door warnings) |
| `snoozeNotificationsCurrent` / `snoozeNotificationsAll` | `SnoozeNotificationsDatabase` | User-initiated snooze windows |
| `remoteButtonCommandCurrent` / `remoteButtonCommandAll` | `RemoteButtonCommandDatabase` | Server-issued button-push commands (polled by ESP32) |
| `remoteButtonRequestCurrent` / `remoteButtonRequestAll` | `RemoteButtonRequestDatabase` | Client-initiated button-push requests |
| `remoteButtonRequestErrorCurrent` / `remoteButtonRequestErrorAll` | `RemoteButtonRequestErrorDatabase` | Error entries for stale/missing button requests |

```

Set up Firebase:

```bash
firebase login
firebase init
```

### Deployment
Deploy to Firebase:

```bash
firebase deploy
```

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.
