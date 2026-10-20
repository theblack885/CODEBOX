package com.hwstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF06070C)
val Aqua = Color(0xFF5EEAD4)
val Violet = Color(0xFF8B5CF6)
val Rose = Color(0xFFFB7185)
val GlassFill = Color(0x14FFFFFF)
val GlassEdge = Color(0x26FFFFFF)

val AppBackground = Brush.verticalGradient(
    listOf(Color(0xFF0B1020), Color(0xFF0A0614), Ink)
)

@Composable
fun HwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Aqua,
            secondary = Violet,
            tertiary = Rose,
            background = Ink,
            surface = Color(0xFF0E1222),
            onPrimary = Ink,
            onBackground = Color(0xFFE8ECF8),
            onSurface = Color(0xFFE8ECF8)
        ),
        content = content
    )
}

@Composable
fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassEdge, shape)
            .padding(16.dp),
        content = content
    )
}
