package au.howesthat.whirl.core.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ListScreenStateTest {

    @Test
    fun `a fetch replaced by a newer one does not report its failure`() = runTest {
        val screen = ListScreenState(this, SavedStateHandle())
        // As OkHttp can: the cancelled call ends, a little later, in an IOException rather than the cancellation.
        screen.refresh {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                withContext(NonCancellable) { delay(1_000) }
                throw IOException("Canceled")
            }
        }
        runCurrent()
        screen.refresh {}
        advanceUntilIdle()
        assertNull(screen.error.value)
        assertFalse(screen.refreshing.value)
    }
}
