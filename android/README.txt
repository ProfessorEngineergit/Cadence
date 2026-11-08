Cadence — Practice tracker / Übezeit-Tracker

Native Android port and redesign of the local Übezeit-Tracker iOS project.
Open this directory in Android Studio. Android 10 (API 29) or newer is supported;
the app targets Android 16 (API 36). Gradle and its wrapper are included.

Main flows
- Today: goal tabs, today's share as a ring that fills while the timer runs, the week or
  deadline overview and recent sessions.
- History: stats, last 8 weeks, month calendar, sessions per day (all goals or one).
- Plan: the selected goal's day-by-day plan and the shared free time per weekday
  (several slots per day, copy to other days).
- Settings: reminders (with the next planned one), timer notification, chime,
  language, theme, haptics, widget and CSV export.

Planning (PlanEngine.kt)
A goal applies to a period: a week starting at a chosen weekday and time, or from its
creation until a deadline (optionally with a time). Free minutes per day are clipped
to that period. Today's target is the smaller of: the remaining minutes spread evenly
over the days left, and what an even plan expects by the end of today minus earlier
practice. Being ahead therefore lowers today's share or frees the day; being behind
spreads the gap instead of piling it onto today.

Notifications (ReminderEngine.kt, Reminders.kt)
Reminders are scheduled with AlarmManager for the next useful moment and re-planned
after every change, on boot, app update and clock or time zone changes. WorkManager
runs hourly only as a safety net. Rules are documented on ReminderEngine.
The running timer uses a silent ongoing notification; on Android 16 it uses
ProgressStyle and requests Live Update promotion, which the system may decline.
After an explicit force-stop, reopen the app to resume background scheduling.

Data
Session data and settings use local private SharedPreferences with immediate
commit and migration defaults. Android system backup may include these data if
the user has enabled it. No account, ads or remote backend is used. CSV export
includes exact seconds, notes and goal names, with escaping and formula safety.
The original iOS source is preserved.

Build and verify
  ./gradlew :app:assembleRelease :app:testQaUnitTest :app:lintDebug
  ./gradlew :app:connectedQaAndroidTest
The qa build uses a separate application ID ending in .qa; device tests never
reset the user's real Cadence data. On Oppo, the runner may need permission
through the normal system dialog and a visible screen. Device test activities
use test-only show-when-locked/keep-screen-on flags.

The release APK is minified and unsigned. Local USB installation packages are
signed separately, outside Gradle. No signing key is stored in this repository.

UI: Kotlin / Jetpack Compose / Material 3 Expressive.
The floating tab bar geometry and interaction are adapted from the user's
Downlink GlassTabBar; Cadence uses its own palette. Popups have smaller top
corners and a single downward gesture owner; horizontal timeline gestures are
kept independent. Content continues behind the floating navigation bar.
