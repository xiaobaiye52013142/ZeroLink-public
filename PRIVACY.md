# Privacy Policy

**Last updated: September 2026**

ZeroLink is a local network screen-sharing app. This policy explains what data the app does and does not collect.

## What We Do Not Collect

- No account or sign-up is required
- No names, emails, phone numbers, or contact information
- No location data
- No photos, files, or media from your device
- No device identifiers (IMEI, advertising ID, etc.)
- No analytics or usage statistics sent to any server
- Your screen frames **never leave your local network** — they are sent directly from one device to another over your Wi-Fi or USB connection

## What Stays On Your Device

- **Session PIN**: A 6-digit PIN is generated per session and exists only in memory. It is never stored or transmitted outside your local network.
- **Remembered device PINs**: If you choose to save a PIN for a device, it is stored locally on your device only (Android SharedPreferences). It is never uploaded anywhere.
- **Logs**: In-app diagnostic logs are disabled by default and stored only on-device. They are never sent anywhere. You can view or clear them at any time from the app.

## Local Network Communication

ZeroLink communicates directly between devices on your local Wi-Fi network or over a USB ADB tunnel. All communication is:

- **Local only** — no internet connection is used or required for screen sharing
- **PIN-protected** — every session requires a matching 6-digit PIN to connect
- **Unencrypted** — traffic is plain TCP on your LAN; we assume your home or office network is trusted

## Firebase Services

The app uses Firebase for the following limited purposes only:

- **Remote Config**: To deliver force-update flags and UI theme configuration. No user data is included in these requests.

Firebase's own privacy policy applies to these requests: https://firebase.google.com/support/privacy

## Third-Party SDKs

| SDK | Purpose | Data sent |
|-----|---------|-----------|
| Firebase Remote Config | App configuration | Anonymous app instance ID only |
| JmDNS | Local device discovery | None — LAN broadcast only |

## Children

ZeroLink does not knowingly collect any information from anyone, including children under 13.

## Changes

If this policy changes materially, the in-app consent screen will be shown again on next launch.

## Contact

Questions or concerns? Open an issue at https://github.com/erTesla/ZeroLink-public/issues
