# ניהול סיכות — Lapel Pin Order Manager

Android app (Kotlin, Jetpack Compose, Room, Hilt, WorkManager) for a lapel-pin reselling business:
orders from client → Alibaba → FedEx → client, 50% deposit / 50% balance reminders, real net profit,
and stock pins sold piece by piece. Hebrew first (RTL), English second.

- Architecture plan: [`docs/architecture/`](docs/architecture/README.md)

## Project layout

| Path | What |
|---|---|
| `domain/` | Pure-Kotlin business rules: money, profit, payment status, status reconciler, reminders, FedEx codes, stock. No Android. |
| `app/` | Android app: Room database, repositories, Hilt, Compose UI. |

## Build & test

```bash
./gradlew -p domain test          # business rules (no Android SDK needed)
./gradlew :app:testDebugUnitTest  # database + repository tests (Robolectric)
./gradlew :app:assembleDebug      # debug APK → app/build/outputs/apk/debug/
```

Requires JDK 17+ and the Android SDK (platform 36). CI runs all of the above on every push and
uploads the debug APK as a build artifact.
