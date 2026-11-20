package au.howesthat.whirl.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import au.howesthat.whirl.core.model.ThreadId

/**
 * Threads with a reply draft (post writer), so thread lists can mark them (`docs/plans/post-writer.md` §6). Provided once
 * at the top of the app, so no list's view model needs to know about drafts.
 */
val LocalDraftThreads = staticCompositionLocalOf<Set<ThreadId>> { emptySet() }
