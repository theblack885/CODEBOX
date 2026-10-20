package com.hwstudio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF111827)
val Aqua = Color(0xFF0F766E)      // main teal accent
val Violet = Color(0xFF7C3AED)
val Rose = Color(0xFFE11D48)
val GlassFill = Color(0xCCFFFFFF)
val GlassEdge = Color(0x1A111827)
val MutedText = Color(0xFF5B6478)
val CodeText = Color(0xFF1A2033)

val AppBackground = Brush.verticalGradient(
    listOf(Color(0xFFF4F7FF), Color(0xFFEEF2FB), Color(0xFFF8F5FF))
)

@Composable
fun HwTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Aqua,
            secondary = Violet,
            tertiary = Rose,
            background = Color(0xFFF4F7FF),
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink
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
