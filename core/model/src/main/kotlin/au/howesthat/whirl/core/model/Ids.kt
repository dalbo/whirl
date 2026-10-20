package au.howesthat.whirl.core.model

/**
 * A thread identifier as Whirlpool uses it in URLs: normally an 8-character slug (`3rvvvlwj`), but legacy numeric
 * IDs (`1000422`) still resolve. Always a string; never parse it as a number.
 */
@JvmInline
value class ThreadId(val value: String) {
    init {
        require(value.isNotEmpty() && value.all { it.isLetterOrDigit() }) { "Invalid thread id '$value'" }
    }

    override fun toString(): String = value
}

/** A user, as linked from posts and thread lists (`/user/<id>`). */
data class UserRef(val id: Long, val name: String)

/** A forum, optionally narrowed to one of its groups (`/forum/<id>?g=<group>`). */
data class ForumRef(val id: Int, val title: String, val groupId: Int? = null)

/** A sub-forum group as offered by a forum page's group selector. */
data class ForumGroup(val id: Int, val name: String)
