# 🎩 Alfred — Always-On Android Voice Assistant

Alfred is an always-listening, background voice assistant for Android. Inspired by Jarvis and modeled after a distinguished British butler, Alfred responds immediately when you say **"Hey Alfred"** (or just **"Alfred"**) followed by a natural language command — even when your phone is locked or Alfred is in the background, exactly like Spotify.

---

## ⚡ Features & Spoken Commands

| Command Example | What Alfred Does |
|---|---|
| **"Hey Alfred, call Papa"** | Queries your device contacts via `ContactsContract` and dials or places direct call |
| **"Hey Alfred, click a selfie"** / **"take a selfie"** | Headless front camera capture via CameraX, auto-saves directly to Gallery / Photos (`Pictures/Alfred/`) |
| **"Hey Alfred, open YouTube and search interstellar trailer"** | Deep-links directly into YouTube with the search query pre-loaded |
| **"Hey Alfred, open WhatsApp"** | Fuzzy-matches installed applications and opens the requested app |
| **"Hey Alfred, search for best pasta recipe"** | Launches standard Android web search or Google Chrome |
| **"Hey Alfred, send message to Mom I will be home soon"** | Prepares SMS/message with recipient and body pre-filled |
| **"Hey Alfred, play music"** | Launches Spotify or default system music player |
| **"Hey Alfred, set alarm for 7:30 AM"** | Programs the Android system clock alarm |
| **"Hey Alfred, turn on flashlight"** / **"turn off flashlight"** | Toggles camera torch mode on or off |
| **"Hey Alfred, what time is it?"** / **"what's today's date?"** | Speaks the current time or date with butler etiquette |
| **"Hey Alfred, battery status"** | Checks battery percentage and provides status advisory |

---

## 🏛️ Architecture & How Background Listening Works

```
                                 ┌────────────────────────┐
                                 │   Device Boot Event    │
                                 └───────────┬────────────┘
                                             │ BOOT_COMPLETED
                                             ▼
                                  [ BootReceiver.kt ]
                                             │
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       AlfredService.kt (Foreground Service)                     │
│                                                                                 │
│   • Persistent Low-Priority Notification ("Alfred is listening...")             │
│   • START_STICKY resilience (auto-restarts if memory pressure occurs)           │
│                                                                                 │
│         ┌────────────────────────────────────────────────────────┐              │
│         │        WakeWordDetector.kt ("Hey Alfred")              │              │
│         └───────────────────────┬────────────────────────────────┘              │
│                                 │ Wake Word Detected                            │
│                 ┌───────────────┴───────────────┐                               │
│                 ▼                               ▼                               │
│     [ Trailing Command Included ]    [ No Command Followed ]                    │
│                 │                               │                               │
│                 │                               ▼                               │
│                 │               [ CommandRecognizer.kt ]                        │
│                 │               ("Yes, sir?" + 5s capture)                      │
│                 │                               │                               │
│                 └───────────────┬───────────────┘                               │
│                                 │ Spoken Command Text                           │
│                                 ▼                                               │
│                     [ CommandParser.kt ]                                        │
│                 (NLP / Regex pattern matching)                                  │
│                                 │                                               │
│                                 ▼ ParsedCommand                                 │
│                     [ CommandExecutor.kt ]                                      │
│                                 │                                               │
│       ┌───────────┬─────────────┼─────────────┬────────────┐                    │
│       ▼           ▼             ▼             ▼            ▼                    │
│  CallHandler CameraHandler AppLaunchHandler SearchHandler DeviceHandler ...    │
│                                                                                 │
│                                 │ Output Response String                        │
│                                 ▼                                               │
│                     [ AlfredSpeaker.kt ]                                        │
│               (Android TTS with Locale.UK Accent)                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

1. **Foreground Service with Microphone Type**:
   In modern Android (Android 10 - 14+), background microphone access requires a Foreground Service declared with `android:foregroundServiceType="microphone"`. This provides Spotify-level persistence with a clean ongoing notification.
2. **Headless CameraX**:
   Selfie capture is performed headlessly from background execution using CameraX with a custom `LifecycleOwner` and the front-facing lens (`LENS_FACING_FRONT`). Photos are saved to the device's public `MediaStore.Images`.
3. **Natural British Butler Voice**:
   The `AlfredSpeaker` wraps Android's `TextToSpeech` engine, configured for `Locale.UK` with lowered pitch (0.9) and deliberate cadence (0.95), prefixing responses with classic butler phrases: *"Right away, sir"*, *"Consider it done"*, *"As you wish"*.

---

## 🛠️ How to Open & Build the Project

### Prerequisites
- **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer)
- **Android SDK** API 34 installed
- **JDK 17** (standard with modern Android Studio)

### Steps
1. Open **Android Studio**.
2. Select **File > Open...** and navigate to:
   ```
   /Users/jagdishbaviskar/.gemini/antigravity/scratch/jarvis-android
   ```
3. Allow Gradle to sync dependencies.
4. Connect your Android phone via USB (with **USB Debugging** enabled in Developer Options) or start an Android Emulator.
5. Click the green **Run (▶)** button or press `Shift + F10`.

---

## 📱 Crucial Android Settings for Background Listening

To ensure Android does not kill Alfred when your phone is in deep sleep:
1. **Disable Battery Optimization**:
   - Go to phone **Settings > Apps > Alfred > Battery**.
   - Change setting from *Optimized* to **Unrestricted**.
2. **Auto-Start Permission** (Xiaomi / Oppo / Vivo / OnePlus):
   - In Settings > Apps > Alfred > permissions, enable **Auto-start** and **Run in background**.
3. **Grant All Permissions on Launch**:
   - Audio (Microphone)
   - Camera
   - Phone
   - Contacts
   - Notifications
