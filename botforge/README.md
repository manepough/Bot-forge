# BotForge — Discord Bot Deployer APK

24/7 Discord bot deployer for Android. Runs multiple bots simultaneously with UDP keep-alive, auto-restart, and foreground service.

## Project Structure

```
botforge/
├── .github/workflows/build-apk.yml   ← GitHub Actions (builds the APK)
├── app/
│   ├── build.gradle                   ← App-level Gradle config
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/botforge/
│       │   ├── MainActivity.java      ← Main UI (WebView)
│       │   ├── BotService.java        ← 24/7 foreground service
│       │   ├── BotRunner.java         ← Discord Gateway WebSocket per bot
│       │   ├── UdpKeepAlive.java      ← UDP pings to prevent sleep
│       │   └── BootReceiver.java      ← Auto-start after reboot
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/
│               ├── strings.xml
│               └── themes.xml
├── build.gradle                       ← Root Gradle config
├── settings.gradle
└── gradle.properties
```

## Quick Start

### 1. Clone and push to GitHub
```bash
git clone <this-repo>
cd botforge
git remote set-url origin https://github.com/YOUR_USER/YOUR_REPO.git
git push -u origin main
```

GitHub Actions will automatically trigger and build the APK.

### 2. Download your APK
Go to → **GitHub repo → Actions tab → Latest run → Artifacts → BotForge-APK-xxx**

### 3. Build release APK (optional)

Add these secrets in **GitHub repo → Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -w 0 your-key.jks` |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Your key password |

Then run workflow manually → select `release`.

### 4. Generate a keystore (if you don't have one)
```bash
keytool -genkey -v -keystore botforge.jks \
  -alias botforge -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 botforge.jks
# Paste that output into KEYSTORE_BASE64 secret
```

## Features
- ✅ Runs multiple bots simultaneously
- ✅ True 24/7 — Android foreground service (never killed)
- ✅ UDP keep-alive every 25s (prevents deep sleep)
- ✅ Auto-restart on crash (START_STICKY service)
- ✅ Auto-start after device reboot
- ✅ Discord Gateway WebSocket with heartbeat handling
- ✅ Drag & drop file upload via WebView UI
- ✅ Secrets management in UI
- ✅ Package manager tab
- ✅ GitHub Actions CI/CD — APK built on every push
