# 🎮 Funtoza: 1, 2, 3, 4 Player Offline Party Battles

**Funtoza** is a high-performance Native Android offline multiplayer party game built entirely in **Kotlin**. Designed for 1, 2, 3, or 4 players on a single smartphone or tablet screen, Funtoza reverse engineers and optimizes the classic party game formula (`1-2-3-4-Player-Games-Offline`) into a lightweight, blazing-fast native package.

* **Package Name:** `com.aaritlabs.funtoza`
* **App Name:** `Funtoza`
* **Tech Stack:** Native Android, Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Java 17
* **Compiled APK Size:** **6.6 MB** *(Original Unity APK was 127 MB!)*
* **Ready-to-Install APK:** [`Funtoza.apk`](./Funtoza.apk)

---

## 🚀 Features & Minigames

### 🕹️ 5 Built-In Offline Arcade Minigames
1. **🚀 Tank Battle**: Top-down tank arena battle. Steer your tank, avoid obstacles, and fire ricocheting cannon shells that bounce off walls. Last tank standing wins.
2. **⚽ Finger Soccer / Air Hockey**: High-energy air hockey with responsive physics-based paddles, rebounding pucks, and explosive goal celebrations. First to 3 goals wins.
3. **🐍 Neon Snake**: Tron-style light trail combat. Guide your neon snake around the board leaving an impassable trail behind you. Force opponents into crashes.
4. **💥 Sumo Bumper Clash**: Circular floating electric ring. Charge into rivals with elastic bumper collision physics and knock them off the platform into the abyss.
5. **🎯 Target Blitz (Knife Hit)**: Fast reaction game. Throw daggers into a central spinning timber target with alternating rotation speeds. Avoid hitting existing knives!

### 👥 Player Modes & AI Difficulty
* **🤖 1 Player (vs AI Bots)**: Features an interactive **Bot Difficulty Selector** (Easy 😊, Medium 😏, Hard 😈) with customized AI reaction speed, precision aiming, and difficulty color themes.
* **⚔️ 2 Players (Head-to-Head Duel)**: Classic vertical split-screen duel with Player 1 at the bottom edge and Player 2 at the top edge facing each other!
* **🔺 3 Players (Trio Free-For-All)**: Triangle screen distribution.
* **👑 4 Players (4-Corner Chaos)**: All 4 players battle simultaneously around the 4 corners of the portrait phone screen.
* **⚡ 2 vs 2 Team Play**: Cooperative doubles air-hockey and team clashes.
* **🏆 Cup Tournament Mode**: A 5-round championship playlist! Players accumulate points across different games, leading to a podium ceremony with Gold 🥇, Silver 🥈, and Bronze 🥉 medals.

---

## 🎨 Visual Identity & Two-Phase Splash Screen
* **Phase 1 (Animated Hero Screen):** Dark theme background (`#0A0D18`) with glowing ambient glass highlights. The Funtoza gamepad badge and colored **1 2 3 4** numbers execute an `OvershootInterpolator` bounce & rotation entrance with neon glow.
* **Phase 2 (Glass Loading Screen):** The logo floats upward while the frosted glass progress container (`bg_glass_progress_track`) and neon gradient fill (`bg_glass_progress_fill`) smoothly fade in, counting from `0%` to `100%` with dynamic loading stages before entering the main menu.
* **Glassmorphic Dark UI:** All menus, game cards, difficulty dialogues, and HUD controls feature frosted translucent glass containers (`#2A3554` -> `#182035`), delicate light borders (`#4A5C8A`), and neon player accents.
* **Unique App Icon:** Custom 4-player quadrant arcade badge with signature colors (Red, Blue, Green, Yellow) and a central golden **"F"** emblem.
* **Vertical / Portrait First:** Built specifically for vertical mobile gameplay so players can place the device flat on a table and play comfortably.

---

## 🔊 Unique Procedural Sound Synthesis

Funtoza generates **100% unique procedural sound effects in code** using an in-memory 16-bit 44.1 kHz PCM audio engine (`SoundManager.kt`). There are no external audio files or MP3 decoding overhead:
* **Laser Cannon Shoot:** Downward linear frequency chirp (`900Hz -> 250Hz`).
* **Table Bounce:** Metallic ping synthesized via frequency-modulated sine waves.
* **Explosion:** Lowpass-filtered white noise burst for a deep bass thud.
* **Goal Fanfare:** C-E-G-C major triad chord progression.
* **Victory Anthem:** Retro 8-bit fanfare celebrating match winners.

---

## 📦 Project Structure

```
e:\Monika\Funtoza\
├── Funtoza.apk                          <-- Direct ready-to-install APK (6.6 MB)
├── 1-2-3-4-Player-Games-Offline...apks  <-- Original reverse-engineered APKS
├── REVERSE_ENGINEERING_REPORT.md        <-- Full technical reverse engineering dossier
├── README.md                            <-- Documentation & build guide
├── build.gradle.kts                     <-- Root Gradle build script
├── settings.gradle.kts                  <-- Project modules declaration
├── gradle.properties                    <-- JVM & AndroidX optimization settings
├── local.properties                     <-- Android SDK path configuration
├── gradlew & gradlew.bat                <-- Gradle wrapper executables
└── app/
    ├── build.gradle.kts                 <-- App dependencies & SDK 36 config
    └── src/main/
        ├── AndroidManifest.xml          <-- Immersive fullscreen & hardware acceleration
        ├── res/                         <-- App icons, themes, vector drawables, layouts
        └── java/com/aaritlabs/funtoza/
            ├── MainActivity.kt          <-- Mode & player selection menu
            ├── GameActivity.kt          <-- Game runner & HUD controller
            ├── TournamentActivity.kt    <-- 5-round Cup podium & leaderboard
            ├── audio/
            │   └── SoundManager.kt      <-- Procedural PCM audio synthesizer
            ├── model/
            │   ├── Player.kt            <-- Player data models & signature color palette
            │   ├── GameType.kt          <-- Minigame definitions & metadata
            │   └── TournamentState.kt   <-- Championship scoring state machine
            ├── engine/
            │   ├── BaseGameEngine.kt    <-- Multi-touch dispatcher, game loop, vibration
            │   ├── TankBattleEngine.kt  <-- Tanks, ricochet bullets, bot AI
            │   ├── SoccerEngine.kt      <-- Air hockey physics, goals, paddles
            │   ├── SnakeEngine.kt       <-- Neon snakes, light trails, raycast AI
            │   ├── SumoEngine.kt        <-- Bumper physics, knockouts
            │   └── KnifeHitEngine.kt    <-- Spinning target, knife deflection
            └── view/
                ├── GameSurfaceView.kt   <-- 60-120 FPS hardware-accelerated Canvas loop
                └── ParticleSystem.kt    <-- Sparks, goal celebrations & confetti
```

---

## 🛠️ How to Build and Run

### Option 1: Install Directly via ADB
If you have an Android device or emulator connected:
```bash
adb install -r Funtoza.apk
```

### Option 2: Build From Source via Terminal
```bash
# Clean and assemble debug APK
.\gradlew assembleDebug

# Output APK location:
# app\build\outputs\apk\debug\app-debug.apk
```

### Option 3: Open in Android Studio
1. Open **Android Studio**.
2. Select **Open Project** and choose the `e:\Monika\Funtoza` directory.
3. Allow Gradle to sync.
4. Click **Run (Shift + F10)** to launch on your device or emulator.

---

## 📄 License & Attribution
Developed with ❤️ by **Aarit Labs** (`com.aaritlabs.funtoza`).
Reverse engineered and re-architected natively from the original `1-2-3-4-Player-Games-Offline_3.3.2_apkcube.apks` package.

