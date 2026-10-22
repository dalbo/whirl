package au.howesthat.whirl.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where a row sits in its section; a section drawn as one card rounds its first and last rows. */
data class RowPosition(val first: Boolean, val last: Boolean) {
    companion object {
        fun of(index: Int, count: Int) = RowPosition(first = index == 0, last = index == count - 1)
    }
}

/**
 * One row of a section (thread lists, forums, settings). [Surfaces.FLAT]: the row full width, with a full-width
 * divider below it when [flatDivider]. [Surfaces.CARDS]: the section's rows together form one outlined card inset by
 * the style's gutter, with inset dividers between rows. Each row stays its own lazy item.
 */
@Composable
fun SectionRow(position: RowPosition, flatDivider: Boolean = true, content: @Composable () -> Unit) {
    val layout = LocalAppStyle.current.layout
    if (layout.lists != Surfaces.CARDS) {
        Column {
            content()
            if (flatDivider) HorizontalDivider()
        }
        return
    }
    val corner = layout.cornerDp
    val shape = RoundedCornerShape(
        topStart = if (position.first) corner else 0.dp,
        topEnd = if (position.first) corner else 0.dp,
        bottomStart = if (position.last) corner else 0.dp,
        bottomEnd = if (position.last) corner else 0.dp,
    )
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .padding(horizontal = layout.gutterDp)
            .padding(top = if (position.first) 4.dp else 0.dp, bottom = if (position.last) 12.dp else 0.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()
                // The card's outline, drawn taller than the row where the card continues, so only the outer edges
                // (and the rounded corners of the first and last rows) show.
                val stroke = 1.dp.toPx()
                val radius = corner.toPx()
                val top = if (position.first) stroke / 2 else -radius
                val bottom = if (position.last) size.height - stroke / 2 else size.height + radius
                drawRoundRect(
                    outline,
                    topLeft = Offset(stroke / 2, top),
                    size = Size(size.width - stroke, bottom - top),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(stroke),
                )
            },
    ) {
        content()
        if (!position.last) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = outline)
    }
}

/**
 * A section heading: a tinted band in the primary colour ([Headings.BAND]) or text after a small accent marker
 * ([Headings.MARKER]). [onClick] makes it a button (a forum's name above its threads).
 */
@Composable
fun SectionHeading(text: String, onClick: (() -> Unit)? = null) {
    val layout = LocalAppStyle.current.layout
    val tap = onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier
    when (layout.headings) {
        Headings.BAND -> Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text,
                Modifier.fillMaxWidth().semantics { heading() }.then(tap).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Headings.MARKER -> Row(
            Modifier.fillMaxWidth().semantics { heading() }.then(tap)
                .padding(start = layout.gutterDp + 4.dp, end = layout.gutterDp + 4.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(4.dp, 14.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
