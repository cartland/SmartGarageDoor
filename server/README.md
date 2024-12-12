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

- Node.js 20
- Firebase CLI
- TypeScript

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
npm run test
```

### Configuration

Update the server configuration in `serverConfig.json`, then push it to the server:

```bash
curl -H "Content-Type: application/json" -H "X-ServerConfigKey: $SERVER_CONFIG_UPDATE_KEY" https://us-central1-escape-echo.cloudfunctions.net/serverConfigUpdate -d @serverConfig.json
```

```json
{
  "buildTimestamp": "Sat Mar 6 14:45:00 2021",
  "deleteOldDataEnabled": true,
  "deleteOldDataEnabledDryRun": false,
  "remoteButtonEnabled": true,
  "remoteButtonBuildTimestamp": "Sat%20Mar%2020%2015:20:34%202021",
  "remoteButtonPushKey": "requiredKey",
  "host": "https://example.com",
  "path": "addRemoteButtonCommand",
  "remoteButtonAuthorizedEmails": [
    "email@example.com"
  ]
}
```

### Database Structure

Collections

- eventsCurrent: Current door state
- eventsAll: Historical door states
- updateCurrent: Latest sensor updates
- updateAll: Historical sensor data
- configCurrent: Server configuration
- notificationsCurrent: Active notifications

  "deleteOldDataEnabled": true,
  "snoozeNotificationsEnabled": true
}
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
