package io.github.professorengineergit.cadence

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

val LocalEnglish = compositionLocalOf { false }
fun isEnglish(language: AppLanguage) = when (language) { AppLanguage.DE -> false; AppLanguage.EN -> true; AppLanguage.SYSTEM -> Locale.getDefault().language != "de" }
@Composable fun words(de: String, en: String) = if (LocalEnglish.current) en else de
fun wordsNow(de: String, en: String, language: AppLanguage) = if (isEnglish(language)) en else de

// Central catalog also covers legacy screens ported from the German iOS app.
private val English = mapOf(
    "Übezeit-Tracker" to "Practice tracker", "Heute" to "Today", "Verlauf" to "Journal", "Rhythmus" to "Rhythm", "Optionen" to "Settings",
    "Dein Rhythmus.\nDein Fortschritt." to "Your rhythm.\nYour progress.", "Ganz bei dir.\nGanz im Flow." to "Fully here.\nFully in flow.",
    "ZEIT FÜR DICH" to "TIME FOR YOU", "IM FLOW" to "IN THE FLOW", "KURZ DURCHATMEN" to "TAKE A BREATH",
    "Freies Üben" to "Free practice", "Technik" to "Technique", "Repertoire" to "Repertoire", "Klavier" to "Piano",
    "Wiederholen" to "Review", "Aufgaben" to "Exercises", "Prüfungstraining" to "Exam practice", "Grundlagen" to "Fundamentals", "Projekt" to "Project",
    "Üben starten" to "Start practising", "Pause" to "Pause", "Weiter" to "Resume", "Beenden" to "Finish",
    "Session pausiert" to "Session paused", "Fokusziel erreicht ✓" to "Focus goal reached ✓", "Übezeit nachtragen" to "Log practice",
    "Diese Woche" to "This week", "Dein Tempo zählt." to "Your pace matters.", "Wochenziel erreicht. Stark!" to "Weekly goal reached. Nice work!",
    "Kleine Schritte. Große Wirkung." to "Small steps. Real progress.", "Minuten" to "Minutes", "Sekunden" to "Seconds", "Deine Serie" to "Your streak",
    "Tag in Folge" to "day in a row", "Tage in Folge" to "days in a row", "Zuletzt geübt" to "Recent practice", "Alle" to "All",
    "Hier beginnt dein Fortschritt." to "Your progress starts here.", "Starte deine erste Session. Jeder Moment zählt." to "Start your first session. Every moment counts.",
    "Jeder Moment\nzählt." to "Every moment\ncounts.", "Woche" to "Week", "Monat" to "Month", "Platz für deinen Fortschritt." to "Space for your progress.",
    "Deine Sessions erscheinen hier.\nAuch nachgetragene Übezeit zählt." to "Your sessions will appear here.\nManually logged practice counts too.", "Erste Session eintragen" to "Log your first session",
    "Session verwerfen?" to "Discard session?", "Die laufende Übezeit wird nicht gespeichert." to "This practice time will not be saved.", "Verwerfen" to "Discard", "Weiterüben" to "Keep practising",
    "Woran möchtest du üben?" to "What will you practise?", "Ein klarer Fokus macht den Anfang leichter." to "A clear focus makes it easier to begin.",
    "Dein Fokus" to "Your focus", "Fokuszeit" to "Focus time", "Fokus übernehmen" to "Set focus", "Fokus" to "Focus",
    "Dein Timer zählt aufwärts und läuft auch nach der Fokuszeit weiter. Beende deine Session, wenn es für dich passt." to "Your timer counts up and keeps going after your focus time. Finish when it feels right.",
    "Ein Moment Fortschritt." to "A moment of progress.", "Deine Session" to "Your session", "Timer vergessen? Trage deine Übezeit einfach nach." to "Forgot the timer? Log your practice here.",
    "Passe Dauer, Fokus oder deine Notiz an." to "Edit the duration, focus or your note.", "Notiz (optional)" to "Note (optional)", "Was lief gut? Was kommt als Nächstes?" to "What went well? What's next?",
    "Bitte gib eine Dauer zwischen 1 Sekunde und 24 Stunden ein." to "Enter a duration from 1 second to 24 hours.", "Session speichern" to "Save session", "Session löschen" to "Delete session", "Session löschen?" to "Delete session?",
    "Dieser Eintrag wird aus deinem Übetagebuch entfernt." to "This entry will be removed from your journal.", "Löschen" to "Delete", "Behalten" to "Keep", "Datum auswählen" to "Choose date", "Übernehmen" to "Apply", "Abbrechen" to "Cancel",
    "Für diesen Tag sind noch keine Sessions eingetragen." to "No sessions logged for this day yet.", "Dein Plan für diesen Tag steht. Alles in deinem Tempo." to "Your plan is ready. All at your own pace.",
    "Frei" to "Rest", "Mo" to "Mon", "Di" to "Tue", "Mi" to "Wed", "Do" to "Thu", "Fr" to "Fri", "Sa" to "Sat", "So" to "Sun",
    "Montag" to "Monday", "Dienstag" to "Tuesday", "Mittwoch" to "Wednesday", "Donnerstag" to "Thursday", "Freitag" to "Friday", "Samstag" to "Saturday", "Sonntag" to "Sunday",
    "Session gespeichert" to "Session saved", "Session gelöscht" to "Session deleted", "Übeziel gespeichert" to "Practice goal saved", "Tagesplan gespeichert" to "Daily plan saved",
    "Dein Wochenrhythmus ist gespeichert" to "Your weekly rhythm is saved", "Übetagebuch exportiert" to "Journal exported", "Export fehlgeschlagen. Bitte erneut versuchen." to "Export failed. Please try again.",
    "Test-Benachrichtigung gesendet" to "Test notification sent", "Erinnerungen sind ausgeschaltet." to "Reminders are turned off.",
    "Du bist schon in einer Session. Viel Spaß beim Üben!" to "You're already in a session. Enjoy your practice!",
    "Lege zuerst ein Übeziel in deinem Wochenplan fest." to "Set a practice goal in your weekly plan first.",
    "Wochenziel erreicht! Diese Woche lassen wir dich in Ruhe." to "Weekly goal reached! No more practice reminders this week.",
    "Heute ist Erinnerungspause. Morgen geht es entspannt weiter." to "Reminders are paused for today. A fresh start tomorrow.",
    "Deine Erinnerung ist um 30 Minuten verschoben." to "Your reminder is snoozed for 30 minutes.",
    "Heute hast du dir keinen Zeitraum zum Üben freigehalten." to "No practice window is set for today.",
    "Gerade geübt – jetzt ist erst einmal Ruhe. Mindestens drei Stunden ohne Erinnerung." to "You just practised. Enjoy at least three hours without reminders.",
    "Du liegst im Wochenplan. Gestern mehr geübt? Das zählt heute mit." to "You're on track. Extra practice yesterday counts towards today too.",
    "Heute ist ein freier Tag. Dein Rhythmus darf auch Pausen haben." to "Today is a rest day. Your rhythm includes breaks too.",
    "Dein Erinnerungslimit für heute ist erreicht." to "Today's reminder limit has been reached.",
    "Eine Erinnerung reicht erst einmal. Die nächste frühestens nach drei Stunden." to "One reminder is enough for now. The next is at least three hours away.",
    "Dein Zeitfenster endet gleich. Heute kein Drängeln mehr." to "Your practice window is almost over. No pressure today.",
    "Gestern stark. Heute ganz entspannt." to "Great work yesterday. Take it easy today.", "Dein Wochen-Check-in ist morgen." to "Your weekly check-in is tomorrow.",
    "Ein guter Moment für dein Wochenziel." to "A good moment for your weekly goal.", "Ein kleiner Schritt fehlt noch." to "Just one small step left.", "Dein Rhythmus hat Schwung." to "Your rhythm has momentum.",
    "Die Erinnerung passt zu deinem aktuellen Fortschritt und Zeitfenster." to "This reminder matches your current progress and practice window.",
    "Deine Session ist noch kürzer als eine Sekunde." to "Your session is less than a second long.", "Speichern fehlgeschlagen. Bitte erneut versuchen." to "Saving failed. Please try again.",
    "Fokus und Dauer ändern" to "Change focus and duration", "Laufende Session verwerfen" to "Discard active session", "Session bearbeiten" to "Edit session", "Datum ändern" to "Change date",
    "Die gespeicherten Daten konnten nicht geladen werden. Bitte die App neu starten." to "Your saved data could not be loaded. Please restart the app.",
    "Die gespeicherten Daten bleiben zum Schutz unverändert." to "Your saved data is being preserved for safety.",
)

fun translate(text: String, english: Boolean): String {
    if (!english) return text
    English[text]?.let { return it }
    val rules = listOf(
        "^(\\d+) Min\\. Fokuszeit$" to "$1 min focus time", "^(\\d+) Min\\.$" to "$1 min", "^(\\d+) Sek\\.$" to "$1 sec",
        "^(\\d+) Minuten$" to "$1 minutes", "^von (\\d+) Minuten$" to "of $1 minutes", "^(\\d+) von (\\d+) Minuten geübt$" to "$1 of $2 minutes practised",
        "^(\\d+) Sessions in diesem Zeitraum$" to "$1 sessions in this period", "^Minuten · freier Tag$" to "minutes · rest day",
        "^Freier Tag · (\\d+) Minuten Bonuszeit$" to "Rest day · $1 bonus minutes", "^Session gespeichert · (.*)$" to "Session saved · $1",
        "^Wir erinnern dich nur in deinem Zeitfenster: (.*)\\.$" to "Reminders only within your window: $1.",
        "^Deine nächste Gelegenheit liegt ungefähr bei (.*)\\.$" to "Your next opportunity is around $1.",
        "^(\\d+) Minuten nur für dich\\?$" to "$1 minutes just for you?",
    )
    for ((pattern, replacement) in rules) if (Regex(pattern).matches(text)) return text.replace(Regex(pattern), replacement)
    if (Regex(".*\\d.*").matches(text)) {
        var date = text
        listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag").forEach { date = date.replace(it, English.getValue(it)) }
        listOf("Januar" to "January", "Februar" to "February", "März" to "March", "Mai" to "May", "Juni" to "June", "Juli" to "July", "Oktober" to "October", "Dezember" to "December", "Mär" to "Mar", "Okt" to "Oct", "Dez" to "Dec").forEach { date = date.replace(it.first, it.second) }
        if (date != text) return date
    }
    return text
}

@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, style: TextStyle = LocalTextStyle.current) {
    androidx.compose.material3.Text(text = translate(text, LocalEnglish.current), modifier = modifier, color = color, fontSize = fontSize,
        fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign, maxLines = maxLines, overflow = overflow, style = style)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector, contentDescription?.let { translate(it, LocalEnglish.current) }, modifier, tint)
}
