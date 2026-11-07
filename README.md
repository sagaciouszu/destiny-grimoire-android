# Grimoire (Android)

<p align="center">
  <img src='https://img.shields.io/github/v/release/sagaciouszu/destiny-grimoire-android?label=version&color=blue' alt='Version'/>
  <a href='https://github.com/sagaciouszu/destiny-grimoire-android/actions/workflows/ci.yml'><img src='https://github.com/sagaciouszu/destiny-grimoire-android/actions/workflows/ci.yml/badge.svg' alt='CI'/></a>
  <a href='https://github.com/sagaciouszu/destiny-grimoire-android/actions/workflows/codeql-analysis.yml'><img src='https://github.com/sagaciouszu/destiny-grimoire-android/actions/workflows/codeql-analysis.yml/badge.svg' alt='CodeQL'/></a>
  <img src='https://img.shields.io/badge/license-MIT-green' alt='License'/>
  <img src='https://img.shields.io/badge/Kotlin-2.0-blue' alt='Kotlin'/>
  <img src='https://img.shields.io/badge/architecture-ARM64%20%2F%20x86_64-lightgrey' alt='ARM64 / x86_64'/>
</p>


Offline-first, native-accelerated search over the Destiny grimoire archive.

- **UI**: Jetpack Compose, Material 3 (Material You — dynamic color on Android 12+)
- **Native core**: C++ / NDK + SQLite FTS5 (bundled amalgamation with FTS5 enabled)
- **Data**: `grimoire.db` (~215 MB, 120k+ indexed entries) — downloaded once on first
  launch from the publisher's GitHub release as `grimoire.db.gz` (~56 MB), verified
  against the release's `SHA256SUMS` manifest, kept offline on device
- **Icons**: [Lucide](https://github.com/lucide-icons/lucide) (via `com.composables:icons-lucide-android`)

## Architecture

```
Kotlin / Compose UI (Material You)
        │
        ▼ JNI
native search engine (SQLite FTS5, C++17)
        │
        ▼
grimoire.db (read-only, ~120k rows)
```

## Build

Requires: JDK 21+, Android SDK 36, NDK r30 (16 KB page support), CMake, Ninja.

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # installs on a connected device
```

## Targets

| | |
|---|---|
| minSdk | 33 (Android 13) |
| compileSdk / targetSdk | 36 |
| NDK | 30.0.16248370 |
| ABIs | arm64-v8a, x86_64 |

## Notes

- The app bundles no content; the database is fetched live from the latest GitHub
  release of [sagaciouszu/destiny-agent-grimoire](https://github.com/sagaciouszu/destiny-agent-grimoire).
- Destiny is a trademark of Bungie Inc. Fan-built project; not affiliated with
  or endorsed by Bungie.
