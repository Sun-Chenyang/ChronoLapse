# ChronoLapse

<p align="center">
  <img src="docs/logo.png" width="110" alt="ChronoLapse logo"/>
</p>

<p align="center">
  <strong>An Android time-lapse app that works like a camera</strong><br>
  Built with Kotlin + Jetpack Compose · Photo & video lapse modes · Time watermark · Built-in library
</p>

<p align="center">
  <a href="#features"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+"/></a>
  <a href="#build"><img src="https://img.shields.io/badge/API-26%2B-informational" alt="API 26+"/></a>
  <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose"/>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0"/></a>
</p>

<p align="center">
  <a href="README.md">简体中文</a> | English
</p>

---

<p align="center">
  <img src="docs/screenshot_camera.png" width="200" alt="Viewfinder"/>
  <img src="docs/screenshot_capture.png" width="200" alt="Capture settings"/>
  <img src="docs/screenshot_library.png" width="200" alt="Library"/>
  <img src="docs/screenshot_viewer.png" width="200" alt="Project viewer"/>
</p>

## Features

### Camera-style capture

- **Full-screen viewfinder** that works like a native camera: pinch to zoom, tap to focus, an AE chip for exposure, and a vertical slider for quick zoom on the left edge
- **Two-mode slider**: photo lapse (JPG sequence) or video lapse (hardware-encoded MP4) — time lapse only, nothing else
- **Any capture interval**: free h / m / s wheel combination, from 1 second to 23 h 59 min
- **Recording state at a glance**: REC timer, captured frames, next-frame countdown and estimated clip length
- One-tap toggles for grid, time watermark and flash

### Library

- Photo sessions are **grouped into projects**, videos listed flat, with All / Photos / Videos filters
- Project viewer: **live scrubbing** through frames, playback at the configured frame rate, single-frame stepping for fine inspection
- **Export a photo sequence to MP4** in one tap, plus share and batch delete

### Unattended

- Foreground service + WakeLock keeps capturing with the screen off
- **Scheduled recording**: starts automatically at a set time
- **Power saving mode**: intervals over 10 s switch to per-shot alarm wake-ups
- Automatic stop on low battery or low storage

### Also

- **Time watermark**: current time overlaid on every frame, white with black outline, adaptive to resolution
- **REST API**: start/stop recording over LAN HTTP (`http://<IP>:8085`)
- Modern storage: written straight to the gallery via MediaStore on Android 10+, no storage permission needed
- Simplified Chinese / English follows the system language

## Install

Grab the APK from [Releases](../../releases) (requires Android 8.0+).

On first launch grant camera, microphone, notification and media permissions, and allow "ignore battery optimizations" (required for unattended recording).

## Build

Open the project in Android Studio (Ladybug or newer) and build, or from the command line:

```bat
gradlew assembleRelease
```

Release signing: place a `keystore.properties` file in the project root (containing `storeFile` / `storePassword` / `keyAlias` / `keyPassword`); without it the release build is unsigned. See `keystore.properties.example`.

## Usage

1. Pick **Photo Lapse** or **Video Lapse** with the mode slider at the bottom
2. Tap the settings button on the right to adjust the capture interval (h/m/s wheels), playback FPS, frame size and time watermark; scheduled recording is set here too
3. Everything else (camera selection, shutter mute, auto stop, REST API, …) lives in the **⋮ settings** screen at the top right
4. Tap the shutter to start/stop; the screen can be locked while recording, with the REC timer and frame count shown live
5. The thumbnail left of the shutter opens the **library**: long-press for multi-select delete, open a project to scrub frames, play a preview or export a video
6. Files end up in `Movies/ChronoLapse/<date>/` (video laps and exports) and `Pictures/ChronoLapse/<date>/` (photo laps)

## Acknowledgements & License

This project is a complete rewrite based on these GPL projects — many thanks to the original authors:

- [SpartanTimeLapseRecorder](https://github.com/zeitgeist87/SpartanTimeLapseRecorder) — zeitgeist87
- [TimeLapseCamera](https://github.com/woheller69/TimeLapseCamera) — woheller69

This program is free software, licensed under [GPL-3.0](LICENSE).

Copyright (C) 2026 Sun-Chenyang
