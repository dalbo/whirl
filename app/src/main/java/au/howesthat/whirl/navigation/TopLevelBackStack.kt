package au.howesthat.whirl.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The app's back stacks: one per drawer destination, each starting with that destination, as Navigation 2's
 * saveState/restoreState kept them. On screen is the start screen's stack, or the start screen with the chosen drawer
 * destination's stack above it. A stack not on screen keeps its screens (and, in WhirlApp, their state).
 */
class TopLevelBackStack(val start: NavKey, tops: List<NavKey>, saved: Saved? = null) {
    private val stacks: Map<NavKey, SnapshotStateList<NavKey>> = (listOf(start) + tops).distinct().associateWith { top ->
        (saved?.stacks?.firstOrNull { it.firstOrNull() == top } ?: listOf(top)).toMutableStateList()
    }

    /** The drawer destination on screen. */
    var current: NavKey by mutableStateOf(saved?.current?.takeIf { it in stacks } ?: start)
        private set

    /** Each drawer destination's stack, in a fixed order (WhirlApp keeps their entries' state per stack). */
    val all: Map<NavKey, List<NavKey>> get() = stacks

    /** The back stack on screen. */
    val visible: List<NavKey> get() = if (current == start) stack(start).toList() else listOf(start) + stack(current)

    private fun stack(top: NavKey): SnapshotStateList<NavKey> = stacks.getValue(top)

    fun isTopLevel(key: NavKey): Boolean = key in stacks

    /** A drawer destination: its stack as it was left (choosing the one on screen changes nothing). */
    fun select(top: NavKey) {
        require(isTopLevel(top)) { "$top is not a drawer destination" }
        current = top
    }

    fun push(key: NavKey) {
        stack(current).add(key)
    }

    /**
     * Back: the top screen of the current stack goes; from a drawer destination's own screen, back to the start
     * screen's stack. False when only the start screen is left (the app closes).
     */
    fun pop(): Boolean {
        val stack = stack(current)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }
            current != start -> {
                current = start
                true
            }
            else -> false
        }
    }

    /**
     * Two panes: a thread chosen in a list replaces the threads beside it (everything above the topmost [isList]
     * entry), so the back stack does not grow with each tap. With the list on top, as on a phone, this is a push.
     */
    fun openFromList(key: NavKey, isList: (NavKey) -> Boolean) {
        val stack = stack(current)
        val list = stack.indexOfLast(isList)
        if (list >= 0) while (stack.lastIndex > list) stack.removeAt(stack.lastIndex)
        stack.add(key)
    }

    /** The list a screen sits above in its stack (what a thread is shown beside), or null. */
    fun listBelow(key: NavKey, isList: (NavKey) -> Boolean): NavKey? {
        val stack = stacks.values.firstOrNull { key in it } ?: return null
        return stack.subList(0, stack.lastIndexOf(key)).lastOrNull(isList)
    }

    /** The screen directly above [key] in its stack (the thread beside a list), or null. */
    fun above(key: NavKey): NavKey? {
        val stack = stacks.values.firstOrNull { key in it } ?: return null
        return stack.getOrNull(stack.lastIndexOf(key) + 1)
    }

    fun save(): Saved = Saved(current, stacks.values.map { it.toList() })

    @Serializable
    data class Saved(val current: NavKey, val stacks: List<List<NavKey>>)

    companion object {
        private val json = Json { serializersModule = RouteSerializers }

        fun encode(saved: Saved): String = json.encodeToString(Saved.serializer(), saved)

        fun decode(text: String): Saved? = runCatching { json.decodeFromString(Saved.serializer(), text) }.getOrNull()
    }
}
