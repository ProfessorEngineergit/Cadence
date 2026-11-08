# Cadence

Practise regularly without keeping it all in your head: an instrument, a talk, an exam or any other skill.

You set a goal, either a weekly amount (100 minutes of piano a week) or a total by a date (two hours before Thursday's talk at 10:00), and you add the times you actually have free. Cadence spreads the goal over that free time and shows each day how much is due.

- **The plan adapts.** Extra practice makes the next days lighter, sometimes a day becomes free. Missed days are spread over what's left instead of piling onto one day.
- **Weeks can start at a lesson.** A weekly goal can reset at, say, Monday 16:00; practice on Monday before the lesson still counts for the week before.
- **Reminders when it fits.** The first reminder comes when a free slot starts, or at the time you usually begin once Cadence has seen a few timed sessions. Later slots get their own reminder, the last one a single follow-up while there is still enough time. Nothing is sent once today is done, on days without free time, while a timer runs or within an hour of practice. After three ignored days it drops to one a day until you practise again. On a day you've earned off, an optional note says so.
- **Timer** that survives closing the app, with progress towards today's goal in its notification (Android 16 progress style, Live Update requested where the system supports it).
- **Several goals** as tabs, each with its own plan; free time is shared and overbooked days are flagged.
- **History** with stats, an 8-week chart and a month calendar. The streak skips days without free time, so planned rest never breaks it.
- **Widget** in four sizes that follows the app's light or dark theme.
- German and English, light/dark/wallpaper colours, haptics, completion chime, notes and CSV export.

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
