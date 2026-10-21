package au.howesthat.whirl.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Widest a column of post text gets before lines grow too long to read (landscape, tablets, desktop windows). */
val ReadableWidth = 640.dp

/** Fills the width up to [ReadableWidth] and centres beyond it; short content still fills the column. */
fun Modifier.readableWidth(): Modifier = fillMaxWidth().wrapContentWidth().widthIn(max = ReadableWidth).fillMaxWidth()
