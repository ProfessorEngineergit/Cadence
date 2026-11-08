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

// Built-in focus names are stored in German (as older versions did) and shown in the app language.
private val FocusNames = mapOf(
    "Freies Üben" to "Free practice", "Technik" to "Technique", "Repertoire" to "Repertoire", "Neues Stück" to "New piece",
    "Wiederholen" to "Review", "Aufgaben" to "Exercises", "Prüfungstraining" to "Exam practice", "Karteikarten" to "Flashcards", "Probeklausur" to "Practice exam",
    "Grundlagen" to "Basics", "Projekt" to "Project", "Durchsprechen" to "Run-through", "Einstieg" to "Opening", "Folien" to "Slides", "Mit Stoppuhr" to "Timed run",
)

fun translate(text: String, english: Boolean): String = if (english) FocusNames[text] ?: text else FocusNames.entries.firstOrNull { it.value == text }?.key ?: text

@Composable
fun Text(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null, fontFamily: FontFamily? = null, textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, style: TextStyle = LocalTextStyle.current, softWrap: Boolean = true) {
    androidx.compose.material3.Text(text = text, modifier = modifier, color = color, fontSize = fontSize,
        fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign, maxLines = maxLines, overflow = overflow, style = style, softWrap = softWrap)
}

@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint)
}
