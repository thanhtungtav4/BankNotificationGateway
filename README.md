# Bank Notification Gateway Android App

Native Android Kotlin app for reading whitelisted bank app notifications and sending signed events to the central gateway server.

## Current Implementation Status

Implemented:

- Manual Pairing screen.
- Local package whitelist screen.
- Logs screen for local sent/queued records.
- Settings screen with Notification Access shortcut.
- NotificationListenerService ingest flow.
- Room local queue and logs.
- DataStore device config storage.
- HMAC request signing.
- Heartbeat worker scheduled every 15 minutes.
- Retry worker scheduled every 15 minutes with network connectivity required.

Not yet implemented:

- QR scanner UI.
- Server-driven whitelist sync.
- Polished design/navigation.
- Battery optimization wizard.
- Full release signing.

## Build

Open `android-app` in Android Studio and sync Gradle.

If a Gradle wrapper exists:

```bash
./gradlew assembleDebug
```

Current workspace note: there is no `./gradlew` wrapper and the local machine does not have the `gradle` command installed, so CLI build cannot run until Android Studio or Gradle wrapper is available.

## Manual Device Test Steps

1. Open the `android-app` folder in Android Studio.
2. Sync Gradle.
3. Build a debug APK.
4. Install on a real Android phone.
5. Open the app.
6. Go to the Pairing tab.
7. Enter the server URL.
8. Enter temporary pairing token, for example `tenant:1`.
9. Enter a device name.
10. Tap Pair Device.
11. Go to the Whitelist tab.
12. Enable only real banking apps, for example MB Bank, Vietcombank, ACB, Techcombank, TPBank.
13. Go to Settings.
14. Tap Open Notification Access Settings.
15. Enable notification access for Bank Gateway.
16. Trigger a bank notification.
17. Go to Logs and verify the event is shown as `sent` or `queued`.
18. If queued, restore network/server and wait for retry worker or relaunch the app.

## Important Real Device Notes

- Android emulator cannot reliably test real banking notifications.
- Some bank apps hide notification content, so raw title/text may be empty.
- Some phones restrict background work aggressively; disable battery optimization for the app during testing.
- On Android 11+, installed app visibility is restricted. The manifest includes queries for common Vietnamese bank packages, but additional packages may need to be added.

## HMAC Protocol

Signed requests use:

```text
hmac_sha256(timestamp + "." + raw_body, device_secret)
```

Headers:

```text
X-Device-Id: dev_xxx
X-Timestamp: 1759980600
X-Signature: signature
```

## MVP Pairing

The server currently supports a temporary token format:

```text
tenant:{tenant_id}
```

Example:

```text
tenant:1
```

This must be replaced with real one-time QR pairing tokens before production.
