package au.howesthat.whirl.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionsMenuTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `menu entries are TalkBack custom actions, disabled ones left out`() {
        val ran = mutableListOf<String>()
        val actions = listOf(
            MenuAction("Watch") { ran += "watch" },
            MenuAction("Jump to last post", enabled = false) { ran += "last" },
            MenuAction("Open in browser") { ran += "browser" },
        )
        compose.setContent {
            Text("Row", Modifier.menuClickable(actions, onLongClick = {}, onClick = {}, onLongClickLabel = "show actions"))
        }
        val node = compose.onNodeWithText("Row")
        val custom = node.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Watch", "Open in browser"), custom.map { it.label })
        assertEquals("show actions", node.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)

        compose.runOnIdle { custom.last().action() }
        assertEquals(listOf("browser"), ran)
    }

    @Test
    fun `a disabled row offers no actions`() {
        compose.setContent {
            Text("Gone", Modifier.menuClickable(listOf(MenuAction("Watch") {}), onLongClick = {}, onClick = {}, enabled = false))
        }
        val config = compose.onNodeWithText("Gone").fetchSemanticsNode().config
        assertTrue(config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
    }

    @Test
    fun `long press still opens the menu`() {
        var opened = false
        compose.setContent {
            Text("Row", Modifier.menuClickable(emptyList(), onLongClick = { opened = true }, onClick = {}))
        }
        compose.onNodeWithText("Row").performSemanticsAction(SemanticsActions.OnLongClick)
        assertTrue(opened)
    }
}
