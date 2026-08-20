## Overview

DeX is a cross-platform desktop utility that connects your PC to your phone — one shared **Kotlin + Compose Multiplatform** codebase for **Windows and macOS**.

## Features


```bash
# Build
./gradlew :composeApp:desktopJar

# Run
./gradlew :composeApp:run

# Tests (58 desktop tests)
./gradlew :composeApp:desktopTest

# Package
./gradlew :composeApp:createDistributable   # runnable app
./gradlew :composeApp:packageMsi            # Windows installer
./gradlew :composeApp:packageDmg            # macOS disk image
```

## Android App

The Android companion app is a **separate standalone project** in [`DeX/`](DeX/) — open `DeX/` in Android Studio, or:

```bash
cd DeX
./gradlew :app:assembleDebug
```

## Legacy Implementation

The retired WPF / C# / PowerShell implementation lives (read-only) in [`Archived_Legacy_WPF/`](Archived_Legacy_WPF/) — do not modify, delete, or restore it.
