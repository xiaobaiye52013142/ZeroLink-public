# ZeroLink

Share your Android screen — or your PC screen — to any other device on your local Wi-Fi network. No accounts, no cloud, no internet required. Everything stays on your network.

## Features

- **Phone → Phone**: Share your Android screen to another Android device in real time
- **PC → Phone**: View your Windows/macOS/Linux desktop on your Android phone
- **Remote control**: Control the sender's phone from the viewer (tap, swipe, back/home/recents, keyboard input) via Android Accessibility Service
- **USB support**: Connect over USB cable using ADB reverse — no Wi-Fi needed
- **Zero setup**: Devices discover each other via mDNS (no router config, no port forwarding)
- **PIN protection**: Every session requires a 6-digit PIN — no unintended viewers

## Platforms

| Component | Platform |
|-----------|----------|
| Android app (sender + receiver) | Android 8.0+ |
| Desktop app (PC sender) | Windows, macOS, Linux |

## Getting Started

### Android app

1. Clone the repo and open in Android Studio
2. Add your `app/google-services.json` (Firebase — needed for Remote Config and App Distribution; the app works without it in debug builds if you remove the `google-services` plugin)
3. Run on device: `./gradlew :app:installDebug`

### Desktop app

```bash
./gradlew :desktop:run
```

To build an installer:
```bash
./gradlew :desktop:packageMsi      # Windows
./gradlew :desktop:packageDmg      # macOS
./gradlew :desktop:packageDeb      # Linux
```

### USB connection (PC → Phone)

1. Enable USB debugging on your Android phone
2. Connect via USB cable
3. In the desktop app, click **Share PC Screen → Start Sharing → Set Up USB**
4. In the Android app, tap **Mirror PC → Using USB cable?** to auto-fill `127.0.0.1`
5. Enter the PIN shown on the PC app and tap Connect

## Project Structure

```
app/        Android app (Jetpack Compose)
desktop/    Desktop app (Compose for Desktop)
shared/     Shared protocol code (Kotlin Multiplatform)
```

## How It Works

- **Discovery**: mDNS/NSD — the sender advertises a `_localscreenshare._tcp` service; receivers find it automatically
- **Transport**: Direct TCP socket on port 5000; JPEG frames prefixed with a 4-byte length
- **Auth**: 6-digit PIN exchanged as the first 6 bytes of each connection
- **Remote control**: A separate TCP channel on port 5001 carries touch/gesture commands; executed on the sender via Android Accessibility Service

## Security & Privacy

- **No internet traffic**: All data stays on your local network. The app requires `usesCleartextTraffic` because Android restricts plain TCP by default — there is no cloud relay.
- **PIN authentication**: Every session requires a 6-digit PIN exchanged as the first bytes of the TCP connection. The PIN is not encrypted in transit (it's on your LAN), but it prevents unintended viewers from attaching to a stream.
- **No data collection**: The app has no analytics, no account system, and no telemetry. Firebase Remote Config is used only for force-update flags and theme delivery — no user data is sent.
- **Logging is opt-in**: The in-app log viewer is hidden behind a 5-tap easter egg and disabled by default. Logs are stored only on-device.
- **Accessibility Service**: The remote control feature uses Android's Accessibility Service to inject gestures on the sender device. This permission is optional and only used when the viewer explicitly enables remote control.

## Contributing

Pull requests are welcome. For larger changes, open an issue first to discuss the approach.

Repo: https://github.com/erTesla/ZeroLink-public

## License

MIT — see [LICENSE](LICENSE)
