package ai.fam.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Leaf = Color(0xFF1B5E20)
private val LeafDark = Color(0xFFA5D6A7)
private val Paper = Color(0xFFF7F8F4)
private val Ink = Color(0xFF1C1B16)

private val LightColors = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    secondary = Color(0xFF4E6350),
    background = Paper,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = LeafDark,
    onPrimary = Color(0xFF003910),
    background = Color(0xFF121410),
    surface = Color(0xFF1C1F1A),
)

@Composable
fun FamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
