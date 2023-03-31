import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import recipes.raceOf
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceOfTest {

    @Test
    fun should_wait_for_the_fastest() = runTest {
        raceOf(
            { delay(3) },
            { delay(1) },
            { delay(2) },
        )
        assertEquals(1, currentTime)
    }

    @Test
    fun should_wait_for_the_fastest_for_big_number() = runTest {
        val racers = List<suspend CoroutineScope.() -> Long>(1000) { i ->
            {
                val num = (i + 100).toLong()
                delay(num)
                num
            }
        }.shuffled().toMutableList()
        val result = raceOf(racers.removeFirst(), *racers.toTypedArray())
        assertEquals(100, result)
        assertEquals(100, currentTime)
    }

    @Test
    fun should_respond_with_fastest() = runTest {
        val result = raceOf(
            { delay(3000); "C" },
            { delay(1000); "A" },
            { delay(2000); "B" },
        )
        assertEquals("A", result)
        assertEquals(1000, currentTime)
    }

    @Test
    fun should_cancel_slower() = runTest {
        var slowerJob: Job? = null
        val result = raceOf(
            { delay(1000); "A" },
            { slowerJob = currentCoroutineContext().job; delay(2000); "B" },
        )
        assertEquals("A", result)
        assertEquals(1000, currentTime)
        assertEquals(true, slowerJob?.isCancelled)
    }

    @Test
    fun should_cancel_when_parent_cancelled() = runTest {
        var innerJob: Job? = null
        val job = launch {
            raceOf(
                { delay(1000) },
                { innerJob = currentCoroutineContext().job; delay(2000) },
            )
        }
        delay(500)
        assertEquals(true, innerJob?.isActive)
        job.cancel()
        assertEquals(true, innerJob?.isCancelled)
    }

    @Test
    fun should_propagate_context() = runTest {
        var innerCtx: CoroutineContext? = null

        val coroutineName1 = CoroutineName("ABC")
        withContext(coroutineName1) {
            raceOf(
                { delay(1000) },
                { innerCtx = currentCoroutineContext(); delay(2000) },
            )
        }
        delay(500)
        assertEquals(coroutineName1, innerCtx?.get(CoroutineName))

        val coroutineName2 = CoroutineName("DEF")
        withContext(coroutineName2) {
            raceOf(
                { delay(1000) },
                { innerCtx = currentCoroutineContext(); delay(2000) },
            )
        }
        delay(500)
        assertEquals(coroutineName2, innerCtx?.get(CoroutineName))
    }
}