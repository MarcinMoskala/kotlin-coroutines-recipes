@file:OptIn(ExperimentalCoroutinesApi::class)

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import recipes.mapAsync
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class MapAsyncTest {
    @Test
    fun should_behave_like_a_regular_map_for_a_list_and_a_set() = runTest {
        val list = ('a'..'z').toList()
        val charTransformation1 = { c: Char -> c.inc() }
        assertEquals(list.map(charTransformation1), list.mapAsync(charTransformation1))
        val charTransformation2 = { c: Char -> c.code }
        assertEquals(list.map(charTransformation2), list.mapAsync(charTransformation2))
        val charTransformation3 = { c: Char -> c.uppercaseChar() }
        assertEquals(list.map(charTransformation3), list.mapAsync(charTransformation3))

        val set = (1..10).toSet()
        val intTransformation1 = { i: Int -> i * i }
        assertEquals(set.map(intTransformation1), set.mapAsync(intTransformation1))
        val intTransformation2 = { i: Int -> "A$i" }
        assertEquals(set.map(intTransformation2), set.mapAsync(intTransformation2))
        val intTransformation3 = { i: Int -> i.toChar() }
        assertEquals(set.map(intTransformation3), set.mapAsync(intTransformation3))
    }

    @Test
    fun should_map_async_and_keep_elements_order() = runTest {
        val transforms = listOf(
            suspend { delay(3000); "A" },
            suspend { delay(2000); "B" },
            suspend { delay(4000); "C" },
            suspend { delay(1000); "D" },
        )

        val res = transforms.mapAsync { it() }
        assertEquals(listOf("A", "B", "C", "D"), res)
        assertEquals(4000, currentTime)
    }

    @Test
    fun should_support_context_propagation() = runTest {
        var ctx: CoroutineContext? = null

        val name1 = CoroutineName("Name 1")
        withContext(name1) {
            listOf("A").mapAsync {
                ctx = currentCoroutineContext()
                it
            }
            assertEquals(name1, ctx?.get(CoroutineName))
        }

        val name2 = CoroutineName("Some name 2")
        withContext(name2) {
            listOf("B").mapAsync {
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
            listOf("A").mapAsync {
                job = currentCoroutineContext().job
                delay(Long.MAX_VALUE)
            }
        }

        delay(1000)
        parentJob.cancel()
        assertEquals(true, job?.isCancelled)
    }
}