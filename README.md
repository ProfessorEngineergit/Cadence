# Cadence

Practice time for music, studying, presentations and new skills.

Cadence helps you set a realistic practice plan, track sessions and see what remains until your goal. Keep an ongoing piano routine alongside a presentation or test with a deadline.

- Separate goals, session histories and progress.
- A weekly total shared across selected days, with precise daily availability and targets when needed.
- A persistent timer with pause/resume, manual entries and notes.
- Local reminders that adapt to progress, recent practice and available time.
- An ongoing timer notification with pause and finish actions, plus Android 16 Live Update support where available.
- A home-screen widget, completion chime and optional haptic feedback.
- German and English, light/dark themes, device colours and CSV export.

No account, ads or remote backend. Android system backup may include app data when enabled.

## Build

Open `android/` in Android Studio, or use Java 21 and the included Gradle wrapper:

```sh
cd android
./gradlew :app:assembleRelease
./gradlew :app:testQaUnitTest :app:lintDebug
```

The app supports Android 10 and newer and targets Android 16. Release builds are unsigned; signing keys are kept outside the repository. The `qa` variant has a separate application ID so tests do not reset the main app's data.

## Repository

- `android/`: native Android app and tests.
- `Übezeit-Tracker/` and `Übezeit-Tracker.xcodeproj/`: preserved original iOS project.
- `fastlane/metadata/android/`: English and German store descriptions and screenshots.
