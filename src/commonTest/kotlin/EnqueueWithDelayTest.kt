import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import recipes.enqueueWithDelay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

private val EMIT_RATE = 500.milliseconds
private val VERY_SLOW = EMIT_RATE.times(2)
private val VERY_FAST = EMIT_RATE.div(2)

@OptIn(ExperimentalCoroutinesApi::class)
class EnqueueWithDelayTest {

    private fun dataFlow(n: Int): Flow<Int> = flow {
        repeat(n) {
            emit(it)
            delay(EMIT_RATE)
        }
    }

    @Test
    fun period_greater_than_flow_rate() = runTest {

        val period = VERY_SLOW
        var time = 0L

        withContext(Dispatchers.Default) {
            dataFlow(10)
                .enqueueWithDelay(period = period)
                .onEach {
                    val current = currentTimeMillis()
                    val delta = current - time

                    if (delta < period.inWholeMilliseconds)
                        assertEquals(period.inWholeMilliseconds, delta)

                    time = current
                }
                .toList()
        }
    }

    @Test
    fun period_smaller_than_flow_rate() = runTest {

        val period = VERY_FAST
        var time = 0L

        withContext(Dispatchers.Default) {
            dataFlow(10)
                .enqueueWithDelay(period = period)
                .onEach {
                    val current = currentTimeMillis()
                    val delta = current - time

                    if (delta < period.inWholeMilliseconds)
                        assertEquals(period.inWholeMilliseconds, delta)

                    time = current
                }
                .toList()
        }
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}