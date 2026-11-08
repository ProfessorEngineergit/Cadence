Cadence — Practice tracker / Übezeit-Tracker

Native Android port and redesign of the local Übezeit-Tracker iOS project.
Open this directory in Android Studio. Android 10 (API 29) or newer is supported;
the app targets Android 16 (API 36). Gradle and its wrapper are included.

Main flows
- Today: choose a practice goal, start/pause/finish a persistent timer, or log time.
- Journal: view, edit and remove sessions for the selected goal.
- Rhythm: tap the weekly total, choose practice days and distribute the goal;
  alternatively tap a weekday to edit its precise availability and daily target.
- Settings: reminders, live timer, completion chime, language, theme, haptics,
  home-screen widget and CSV export. There is no duplicate settings button.

Goals can be ongoing music/study/other routines or have a deadline and total
practice target. Availability is shared across goals. Conflicting allocations
and insufficient time before a deadline are shown in the plan.

Notifications
Reminders are local and offline. WorkManager checks recurring opportunities
approximately every 30 minutes, within availability, subject to Android/Oppo
battery scheduling. They use progress, deadlines, recent practice, daily caps,
three-hour gaps and optionally the median time of recent timed sessions.
Actions: start the matching goal, snooze 30 minutes, or pause reminders today.

The active timer uses a separate silent ongoing notification with an Android
chronometer and pause/resume/finish actions. Android 16 Live Update promotion
is requested; OEM/system eligibility decides whether it is promoted. A normal
ongoing notification remains the fallback. The timer itself is timestamp-based
and does not require a continuously running foreground service.
The completion chime plays when a session is finished and respects silent/DND.
Notification permission is required for reminders and the live notification.
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
