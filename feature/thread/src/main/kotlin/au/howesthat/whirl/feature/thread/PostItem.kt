package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.ui.ActionsMenu
import au.howesthat.whirl.core.ui.FindHighlight
import au.howesthat.whirl.core.ui.LocalAppStyle
import au.howesthat.whirl.core.ui.MenuAction
import au.howesthat.whirl.core.ui.PostBodyView
import au.howesthat.whirl.core.ui.Surfaces
import au.howesthat.whirl.core.ui.highlighted
import au.howesthat.whirl.core.ui.menuClickable
import au.howesthat.whirl.core.ui.readableWidth
import au.howesthat.whirl.core.ui.style

/** Where the unread posts start, like the orange line on Whirlpool. */
@Composable
internal fun NewPostsMarker() {
    val color = MaterialTheme.colorScheme.tertiary
    Row(Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = color)
        Text(
            stringResource(R.string.new_posts),
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = color)
    }
}

/** A post full width with a divider below, or an outlined card ([Surfaces.CARDS]). */
@Composable
internal fun PostFrame(content: @Composable () -> Unit) {
    if (LocalAppStyle.current.layout.posts != Surfaces.CARDS) {
        Column(Modifier.readableWidth()) {
            content()
            HorizontalDivider()
        }
        return
    }
    val shape = cardShape()
    Surface(
        modifier = Modifier.readableWidth().cardGutter(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

/** Card corners in styles with post cards, square otherwise. */
@Composable
internal fun cardShape(): Shape = LocalAppStyle.current.layout.let { if (it.posts == Surfaces.CARDS) RoundedCornerShape(it.cornerDp) else RectangleShape }

/** The space around a card in styles with post cards. */
@Composable
internal fun Modifier.cardGutter(): Modifier = LocalAppStyle.current.layout.let {
    if (it.posts == Surfaces.CARDS) padding(horizontal = it.gutterDp, vertical = 6.dp) else this
}

@Composable
internal fun PostItem(
    post: UiPost,
    textStyle: TextStyle,
    isMine: Boolean,
    onLink: (String) -> Unit,
    onQuote: (Long) -> Unit,
    onUser: () -> Unit,
    onUsersPosts: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onReport: () -> Unit,
    onOpenInBrowser: () -> Unit,
    highlight: FindHighlight?,
) {
    val p = post.post
    var menu by remember { mutableStateOf(false) }
    val actions = listOfNotNull(
        MenuAction(stringResource(R.string.post_reply), onClick = onReply),
        MenuAction(stringResource(R.string.post_edit), onClick = onEdit).takeIf { isMine },
        MenuAction(stringResource(R.string.post_user_info, p.author.name), onClick = onUser),
        MenuAction(stringResource(R.string.post_users_posts, p.author.name), onClick = onUsersPosts),
        MenuAction(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser), onClick = onOpenInBrowser),
        MenuAction(stringResource(R.string.post_report), onClick = onReport),
    )
    val body: @Composable () -> Unit = {
        when {
            p.isHidden -> Text(
                stringResource(R.string.post_hidden, p.hiddenReason.orEmpty()),
                style = textStyle,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            p.isDeleted && post.body.blocks.isEmpty() -> Text(
                stringResource(R.string.post_deleted),
                style = textStyle,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> PostBodyView(post.body, onLink = onLink, onQuote = onQuote, textStyle = textStyle, highlight = highlight)
        }
    }
    val clickable = Modifier
        .fillMaxWidth()
        .menuClickable(
            actions,
            onLongClick = { menu = true },
            onClick = {},
            onLongClickLabel = stringResource(au.howesthat.whirl.core.ui.R.string.show_actions),
        )
    Box {
        if (LocalAppStyle.current.layout.posts == Surfaces.CARDS) {
            // In a card: the author on a deeper band above the body.
            Column(clickable) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PostHeader(p, onUser, highlight)
                }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { body() }
            }
        } else {
            Column(clickable.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PostHeader(p, onUser, highlight)
                body()
            }
        }
        ActionsMenu(menu, onDismiss = { menu = false }, actions)
    }
}

@Composable
internal fun PostHeader(p: Post, onUser: () -> Unit, highlight: FindHighlight?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                highlighted(p.author.name, highlight?.let { it.query to it.style() }),
                Modifier.weight(1f, fill = false).clickable(role = Role.Button, onClick = onUser),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("#${p.number}", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The user group sits here rather than beside the name, which large text would squeeze to nothing.
        val meta = listOfNotNull(
            p.userGroup,
            // A rep's company ("Leaptel").
            p.repOf,
            stringResource(R.string.post_op).takeIf { p.isOp },
            p.postedText,
            p.editedText?.let { stringResource(R.string.post_edited, it) },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (p.isOp) FontWeight.Medium else null,
            )
        }
        p.quip?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
