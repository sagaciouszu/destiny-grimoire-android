# Grimoire (Android)

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
