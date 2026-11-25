package au.howesthat.whirl.feature.writer

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.PostBodyView
import au.howesthat.whirl.core.ui.TopBarTextButton
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.whirlTopBarColors
import kotlinx.coroutines.launch

/**
 * The post writer (`docs/plans/post-writer.md` §5): WhirlCode with a formatting bar, a preview, and Copy and reply,
 * which copies the text and opens Whirlpool's reply page for the user to paste and post. Whirl never posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriterScreen(route: WriterRoute, navIcon: NavIcon, onClosed: () -> Unit) {
    val viewModel = hiltViewModel<WriterViewModel, WriterViewModel.Factory>(key = "writer-${route.threadId}") { it.create(route) }
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val isEmpty by viewModel.isEmpty.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val browser = rememberOpenInBrowser()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.writer_copied)
    val backFromReply = stringResource(R.string.writer_back_from_reply)
    val deleteLabel = stringResource(R.string.writer_delete_draft)
    val clipLabel = stringResource(R.string.writer_clip_label)

    // Set by Copy and reply; on the way back from the reply page, the offer to delete the draft shows. Saveable, so it
    // survives the app being killed while the user is on the site.
    var awaitingReturn by rememberSaveable { mutableStateOf(false) }

    fun copyAndReply() {
        viewModel.saveNow()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, viewModel.text.text.toString()))
        // Android 13+ shows its own preview of what was copied; older versions show nothing, so say so there.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        awaitingReturn = true
        browser(viewModel.replyUrl())
    }

    // Each time the writer comes back to the front: if that is from the reply page, offer to delete the draft. A snackbar
    // shown when the browser opened was gone before anyone could see it (Dallin, 2026-11-20).
    LifecycleResumeEffect(Unit) {
        if (awaitingReturn) {
            awaitingReturn = false
            scope.launch {
                val result = snackbar.showSnackbar(backFromReply, actionLabel = deleteLabel, withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.deleteDraft()
                    onClosed()
                }
            }
        }
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                navigationIcon = { NavIconButton(navIcon) },
                title = {
                    Column {
                        Text(stringResource(R.string.writer_title))
                        Text(viewModel.threadTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    TopBarTextButton(stringResource(R.string.writer_copy_and_reply), ::copyAndReply, enabled = loaded && !isEmpty)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.writer_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(deleteLabel) },
                                enabled = !isEmpty,
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Side by side when the writer itself is wide enough, not the window: beside a list it may be narrow.
        // consumeWindowInsets: the scaffold's padding already holds the navigation bar, which imePadding would add again
        // (a gap above the keyboard, as seen after a dialog closed).
        BoxWithConstraints(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            if (maxWidth >= SIDE_BY_SIDE) {
                Row(Modifier.fillMaxSize()) {
                    Editor(viewModel, loaded, Modifier.weight(1f))
                    VerticalDivider()
                    Preview(viewModel, Modifier.weight(1f))
                }
            } else {
                // Write and Preview side by side in a pager: tap a tab or swipe between them (Dallin, 2026-11-20).
                val pages = rememberPagerState { 2 }
                val keyboard = LocalSoftwareKeyboardController.current
                // The keyboard would cover the preview: it goes when Preview settles, and comes back on a tap in Write.
                LaunchedEffect(pages.settledPage) { if (pages.settledPage == 1) keyboard?.hide() }
                Column(Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = pages.currentPage) {
                        Tab(pages.currentPage == 0, { scope.launch { pages.animateScrollToPage(0) } }, text = { Text(stringResource(R.string.writer_tab_write)) })
                        Tab(pages.currentPage == 1, { scope.launch { pages.animateScrollToPage(1) } }, text = { Text(stringResource(R.string.writer_tab_preview)) })
                    }
                    HorizontalPager(pages, Modifier.weight(1f).fillMaxWidth()) { page ->
                        if (page == 0) Editor(viewModel, loaded, Modifier.fillMaxSize()) else Preview(viewModel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.writer_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteDraft()
                    onClosed()
                }) { Text(deleteLabel) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.writer_cancel)) } },
        )
    }
}

private val SIDE_BY_SIDE = 600.dp

@OptIn(ExperimentalFoundationApi::class) // TextFieldState's undo and redo
@Composable
private fun Editor(viewModel: WriterViewModel, loaded: Boolean, modifier: Modifier) {
    var link by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Column(modifier) {
        TextField(
            state = viewModel.text,
            readOnly = !loaded,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.writer_placeholder)) },
            lineLimits = TextFieldLineLimits.MultiLine(),
        )
        HorizontalDivider()
        // The formatting bar, above the keyboard: each button wraps the selection, or inserts a pair at the cursor; the
        // line buttons act on every line the selection touches. Icons in 48 dp targets, no padding between them.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            fun wrap(open: String, close: String) = viewModel.format { Formatting.wrap(it, open, close) }
            BarButton(R.drawable.writer_bold, R.string.writer_bold) { wrap("[*", "*]") }
            BarButton(R.drawable.writer_italic, R.string.writer_italic) { wrap("[/", "/]") }
            BarButton(R.drawable.writer_link, R.string.writer_link) { link = true }
            BarButton(R.drawable.writer_forum_quote, R.string.writer_forum_quote) { wrap("[\"", "\"]") }
            BarButton(R.drawable.writer_reply_quote, R.string.writer_reply_quote) { viewModel.format { Formatting.toggleLinePrefix(it, '>') } }
            BarButton(R.drawable.writer_bullets, R.string.writer_bullets) { viewModel.format { Formatting.toggleList(it, '*') } }
            BarButton(R.drawable.writer_numbers, R.string.writer_numbers) { viewModel.format { Formatting.toggleList(it, '#') } }
            BarButton(R.drawable.writer_outdent, R.string.writer_outdent) { viewModel.format { Formatting.nestList(it, deeper = false) } }
            BarButton(R.drawable.writer_indent, R.string.writer_indent_list) { viewModel.format { Formatting.nestList(it, deeper = true) } }
            BarButton(R.drawable.writer_spoiler, R.string.writer_spoiler) { wrap("[_", "_]") }
            BarButton(R.drawable.writer_code, R.string.writer_code) { viewModel.format { Formatting.block(it, "$$\n\n$$", cursorAt = 3) } }
            BarButton(R.drawable.writer_more, R.string.writer_more_codes) { more = true }
            BarButton(R.drawable.writer_undo, R.string.writer_undo, enabled = viewModel.text.undoState.canUndo) { viewModel.text.undoState.undo() }
            BarButton(R.drawable.writer_redo, R.string.writer_redo, enabled = viewModel.text.undoState.canRedo) { viewModel.text.undoState.redo() }
        }
    }
    if (link) LinkDialog(onDismiss = { link = false }) { url, label -> viewModel.format { Formatting.link(it, url, label) } }
    if (more) MoreCodes(onDismiss = { more = false }) { op -> viewModel.format(op) }
}

@Composable
private fun BarButton(icon: Int, description: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

/** The codes the bar leaves out, as a list with their names. */
@Composable
private fun MoreCodes(onDismiss: () -> Unit, apply: ((Edit) -> Edit) -> Unit) {
    val codes = listOf(
        R.string.writer_small to { e: Edit -> Formatting.wrap(e, "[(", ")]") },
        R.string.writer_strike to { e: Edit -> Formatting.wrap(e, "[-", "-]") },
        R.string.writer_mono to { e: Edit -> Formatting.wrap(e, "[#", "#]") },
        R.string.writer_serif to { e: Edit -> Formatting.wrap(e, "[~", "~]") },
        R.string.writer_sub to { e: Edit -> Formatting.wrap(e, "[\\", "\\]") },
        R.string.writer_sup to { e: Edit -> Formatting.wrap(e, "[^", "^]") },
        R.string.writer_external_quote to { e: Edit -> Formatting.wrap(e, "['", "']") },
        R.string.writer_alternate_quote to { e: Edit -> Formatting.wrap(e, "[`", "`]") },
        R.string.writer_indent to { e: Edit -> Formatting.toggleLinePrefix(e, ':') },
        R.string.writer_rule to { e: Edit -> Formatting.block(e, "-----") },
        R.string.writer_table to { e: Edit -> Formatting.block(e, "| a | b\n| --- | ---\n| c | d", cursorAt = 2) },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.writer_more_codes)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                codes.forEach { (name, op) ->
                    TextButton(onClick = { apply(op); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(name), Modifier.fillMaxWidth()) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.writer_cancel)) } },
    )
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onLink: (url: String, label: String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("https://") }
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.writer_link)) },
        text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.writer_link_url)) }, singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.writer_link_text)) }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = url.trim().length > "https://".length, onClick = {
                onLink(url, label)
                onDismiss()
            }) { Text(stringResource(R.string.writer_insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.writer_cancel)) } },
    )
}

@Composable
private fun Preview(viewModel: WriterViewModel, modifier: Modifier) {
    val body by viewModel.preview.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (body.blocks.isEmpty()) {
            Text(stringResource(R.string.writer_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Links and quote links do nothing here: this is how the post will look, not a page to browse.
            PostBodyView(body, onLink = {}, onQuote = {})
        }
    }
}
