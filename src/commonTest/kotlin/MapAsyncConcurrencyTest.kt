@file:OptIn(ExperimentalCoroutinesApi::class)

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import recipes.mapAsync
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MapAsyncConcurrencyTest {
    private val anyConcurrency = 3

    @Test
    fun should_behave_like_a_regular_map_for_a_list_and_a_set() = runTest {
        val list = ('a'..'z').toList()
        val charTransformation1 = { c: Char -> c.inc() }
        assertEquals(list.map(charTransformation1), list.mapAsync(concurrency = anyConcurrency, charTransformation1))
        val charTransformation2 = { c: Char -> c.code }
        assertEquals(list.map(charTransformation2), list.mapAsync(concurrency = anyConcurrency, charTransformation2))
        val charTransformation3 = { c: Char -> c.uppercaseChar() }
        assertEquals(list.map(charTransformation3), list.mapAsync(concurrency = anyConcurrency, charTransformation3))

        val set = (1..10).toSet()
        val intTransformation1 = { i: Int -> i * i }
        assertEquals(set.map(intTransformation1), set.mapAsync(concurrency = anyConcurrency, intTransformation1))
        val intTransformation2 = { i: Int -> "A$i" }
        assertEquals(set.map(intTransformation2), set.mapAsync(concurrency = anyConcurrency, intTransformation2))
        val intTransformation3 = { i: Int -> i.toChar() }
        assertEquals(set.map(intTransformation3), set.mapAsync(concurrency = anyConcurrency, intTransformation3))
    }

    @Test
    fun should_map_async_and_keep_elements_order() = runTest {
        val transforms = listOf(
            suspend { delay(3000); "A" },
            suspend { delay(2000); "B" },
            suspend { delay(4000); "C" },
            suspend { delay(1000); "D" },
        )

        val res = transforms.mapAsync(concurrency = 4) { it() }
        assertEquals(listOf("A", "B", "C", "D"), res)
        assertEquals(4000, currentTime)
    }

    @Test
    fun should_limit_concurrency_for_single_delay() = runTest {
        val process: suspend (Int) -> Int = { i: Int ->
            delay(1000)
            i * i
        }

        List(1000) { it }.mapAsync(concurrency = 10, transformation = process)
        assertEquals(1000 * 1000 / 10, currentTime)
    }

    @Test
    fun should_limit_concurrency_for_different_delays() = testFor(
        1 to 3000L + 2000L + 4000L + 1000L + 2000L,
        2 to 6000L,
        3 to 5000L,
        4 to 4000L,
        5 to 4000L,
    ) { concurrency, expectedTime ->
        val transforms = listOf(
            suspend { delay(3000); "A" },
            suspend { delay(2000); "B" },
            suspend { delay(4000); "C" },
            suspend { delay(1000); "D" },
            suspend { delay(2000); "E" },
        )

        val res = transforms.mapAsync(concurrency = concurrency) { it() }
        assertEquals(listOf("A", "B", "C", "D", "E"), res)
        assertEquals(expectedTime, currentTime)
    }

    @Test
    fun should_support_context_propagation() = runTest {
        var ctx: CoroutineContext? = null

        val name1 = CoroutineName("Name 1")
        withContext(name1) {
            listOf("A").mapAsync(concurrency = anyConcurrency) {
                ctx = currentCoroutineContext()
                it
            }
            assertEquals(name1, ctx?.get(CoroutineName))
        }

        val name2 = CoroutineName("Some name 2")
        withContext(name2) {
            listOf("B").mapAsync(concurrency = anyConcurrency) {
                ctx = currentCoroutineContext()
                it
            }
            assertEquals(name2, ctx?.get(CoroutineName))
        }
    }

    @Test
    fun should_support_cancellation() = runTest {
        var job: Job? = null

        val parentJob = launch {
            listOf("A").mapAsync(concurrency = anyConcurrency) {
                job = currentCoroutineContext().job
                delay(Long.MAX_VALUE)
            }
        }

        delay(1000)
        parentJob.cancel()
        assertEquals(true, job?.isCancelled)
    }
}

private fun <T1, T2> testFor(vararg data: Pair<T1, T2>, body: suspend TestScope.(T1, T2) -> Unit) {
    for ((input, expected) in data) {
        runTest {
            body(input, expected)
        }
    }
}
