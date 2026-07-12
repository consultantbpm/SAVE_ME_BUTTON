package com.savemebutton.phone.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savemebutton.phone.R

/**
 * Ported from the Crown-family white phone theme — Crown Button's
 * `PhoneThemePalette` / `SectionCard` (`com.vibrorituals.phone.ui.MainActivity`),
 * mirrored via Answer Button's `com.vibrorituals.answerbutton.phone.ui.PhoneTheme`
 * — so Save Me Button's settings screen shares the same collapsible-card
 * visual language as the rest of the Crown family: rounded 10dp cards, 1dp
 * border, uppercase extra-bold 13sp section titles with an expand/collapse
 * chevron.
 *
 * Save Me Button keeps its own panic-red brand accent (0xFFFF1744, already
 * used throughout this app's UI for the "SAVE ME" title and TEST button)
 * as the palette's `primary`/`secondary` in place of Crown's blue — the
 * ported piece is the STRUCTURE (white background, card style, collapse
 * behavior), not Crown's color identity.
 */
data class PhoneThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val mutedText: Color,
)

val SaveMeRed = Color(0xFFFF1744)
private val LightBorder = Color(0xFFD6DCE3)

val SaveMePhonePalette = PhoneThemePalette(
    background = Color.White,
    surface = Color(0xFFF5F7FA),
    surfaceAlt = Color.White,
    border = LightBorder,
    primary = SaveMeRed,
    secondary = SaveMeRed,
    text = Color.Black,
    mutedText = Color(0xFF5F6B76),
)

/** MaterialTheme wrapper: white background/surfaces, Save Me's red as accent. */
@Composable
fun SaveMeButtonTheme(content: @Composable () -> Unit) {
    val palette = SaveMePhonePalette
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
        ),
        content = content,
    )
}

/**
 * Same collapsible-card pattern as Crown's `SectionCard`: rounded 10dp card,
 * 1dp border, uppercase extra-bold 13sp title with an expand/collapse arrow.
 * [initiallyExpanded] defaults to false (collapsed) — only About passes
 * `true`, matching Crown.
 */
@Composable
fun SectionCard(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val palette = SaveMePhonePalette
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 13.sp,
                    color = palette.primary,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(bottom = 6.dp),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.content_desc_collapse else R.string.content_desc_expand
                    ),
                    tint = palette.primary,
                    modifier = Modifier.size(24.dp).padding(start = 4.dp),
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                content()
            }
        }
    }
}

/** Same filled-button style as Crown's `AboutButton`, tinted with Save Me's red. */
@Composable
fun AboutButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = SaveMeRed,
            contentColor = Color.White,
        ),
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
        )
    }
}
